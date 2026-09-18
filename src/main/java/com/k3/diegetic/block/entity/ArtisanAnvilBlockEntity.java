package com.k3.diegetic.block.entity;

import com.k3.diegetic.block.ArtisanAnvilBlock;
import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.item.BlueprintItem;
import com.k3.diegetic.item.ModItems;
import com.k3.diegetic.recipe.ArtisanCraftingRecipe;
import com.k3.diegetic.recipe.ArtisanRecipeInput;
import com.k3.diegetic.recipe.ModRecipes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Clearable;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Diegetic workstation BlockEntity for Artisan Anvil.
 * Supports Method 2 Blueprint & Template blacksmithing:
 * - Capacity: 1 active blueprint stencil + up to 4 staged ingredients.
 * - Sequential placement protocol with real-time Actionbar HUD feedback.
 * - Mistake correction: normal right-click unloads last ingredient, sneak right-click unloads blueprint.
 * - Striking protocol: requires all ingredients; crafts output and retains blueprint for rapid batching.
 * - Zero legacy NBT calls; modern 1.21.1 Data Components and Codecs only.
 */
public class ArtisanAnvilBlockEntity extends BlockEntity implements Clearable, SidedInventory {

    public static final int MAX_STAGED_INGREDIENTS = 8;
    public static final int INVENTORY_SIZE = 9;

    /** Active blueprint stencil positioned flat on the anvil top plate (Slot 0). */
    private ItemStack blueprint = ItemStack.EMPTY;

    /** Ordered sequence of staged workpiece materials (Slots 1-8). */
    private final DefaultedList<ItemStack> stagedIngredients = DefaultedList.of();

    /** Number of hammer strikes completed on current batch. */
    private int strikeCount = 0;

    /** Physical thermal state metric [0.0 - 1.0]. */
    private float thermalState = 0.0f;

    /** UUID of active clickable InteractionEntity hitbox. */
    @Nullable
    private UUID interactionEntityUuid = null;

    /** Cooldown ticks after craft completion before auto-loading. */
    private int autoLoadCooldown = 0;

    public ArtisanAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ARTISAN_ANVIL_BLOCK_ENTITY, pos, state);
    }

    /* =========================================================================
     * INVENTORY & STATE ACCESSORS
     * ========================================================================= */

    public boolean hasBlueprint() {
        return !this.blueprint.isEmpty();
    }

    public ItemStack getBlueprint() {
        return this.blueprint;
    }

    public DefaultedList<ItemStack> getStagedIngredients() {
        return this.stagedIngredients;
    }

    public int getStrikeCount() {
        return this.strikeCount;
    }

    public float getThermalState() {
        return this.thermalState;
    }

    public boolean hasItem() {
        return !this.blueprint.isEmpty() || !this.stagedIngredients.isEmpty();
    }

    /**
     * Backward-compatible accessor for single-item queries.
     */
    public ItemStack getHeldStack() {
        if (!this.blueprint.isEmpty()) {
            return this.blueprint;
        }
        if (!this.stagedIngredients.isEmpty()) {
            return this.stagedIngredients.get(0);
        }
        return ItemStack.EMPTY;
    }

    public void setHeldStack(ItemStack stack) {
        if (stack.getItem() instanceof BlueprintItem) {
            this.blueprint = stack;
        } else {
            this.stagedIngredients.clear();
            if (!stack.isEmpty()) {
                this.stagedIngredients.add(stack);
            }
        }
        this.markDirtyAndSync();
    }

    @Nullable
    public UUID getInteractionEntityUuid() {
        return this.interactionEntityUuid;
    }

    @Nullable
    public UUID getDisplayEntityUuid() {
        return null;
    }

    @Override
    public int size() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return this.blueprint.isEmpty() && this.stagedIngredients.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot == 0) {
            return this.blueprint;
        }
        int idx = slot - 1;
        if (idx >= 0 && idx < this.stagedIngredients.size()) {
            return this.stagedIngredients.get(idx);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == 0 && !this.blueprint.isEmpty() && amount > 0) {
            ItemStack split = this.blueprint.split(amount);
            if (this.blueprint.isEmpty()) {
                this.clear();
            } else {
                this.markDirtyAndSync();
            }
            return split;
        }
        int idx = slot - 1;
        if (idx >= 0 && idx < this.stagedIngredients.size() && amount > 0) {
            ItemStack current = this.stagedIngredients.get(idx);
            ItemStack split = current.split(amount);
            if (current.isEmpty()) {
                this.stagedIngredients.remove(idx);
            }
            this.markDirtyAndSync();
            return split;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot == 0 && !this.blueprint.isEmpty()) {
            ItemStack stack = this.blueprint;
            this.blueprint = ItemStack.EMPTY;
            if (this.world != null) {
                for (ItemStack ing : this.stagedIngredients) {
                    if (!ing.isEmpty()) {
                        ItemScatterer.spawn(this.world, this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5, ing.copy());
                    }
                }
            }
            this.clear();
            return stack;
        }
        int idx = slot - 1;
        if (idx >= 0 && idx < this.stagedIngredients.size()) {
            ItemStack stack = this.stagedIngredients.remove(idx);
            this.markDirtyAndSync();
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            this.blueprint = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(stack.getCount(), 1));
        } else {
            int idx = slot - 1;
            if (idx >= 0 && idx < MAX_STAGED_INGREDIENTS) {
                if (stack.isEmpty()) {
                    if (idx < this.stagedIngredients.size()) {
                        this.stagedIngredients.remove(idx);
                    }
                } else {
                    ItemStack single = stack.copyWithCount(Math.min(stack.getCount(), 1));
                    if (idx < this.stagedIngredients.size()) {
                        this.stagedIngredients.set(idx, single);
                    } else if (idx == this.stagedIngredients.size() && this.stagedIngredients.size() < MAX_STAGED_INGREDIENTS) {
                        this.stagedIngredients.add(single);
                    }
                }
            }
        }
        this.markDirtyAndSync();
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return this.world != null && this.world.getBlockEntity(this.pos) == this
                && player.squaredDistanceTo(this.pos.toCenterPos()) <= 64.0;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return this.canInsert(slot, stack, Direction.UP);
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.DOWN) {
            return new int[0];
        }
        return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (dir == Direction.DOWN || stack.isEmpty()) {
            return false;
        }
        if (slot == 0 && this.blueprint.isEmpty() && stack.getItem() instanceof BlueprintItem) {
            return true;
        }
        if (slot >= 1 && slot <= MAX_STAGED_INGREDIENTS && !this.blueprint.isEmpty()
                && this.stagedIngredients.size() < MAX_STAGED_INGREDIENTS) {
            int idx = slot - 1;
            if (idx == this.stagedIngredients.size()) {
                var recipeOpt = findRecipeForBlueprint(this.blueprint);
                if (recipeOpt.isPresent()) {
                    ArtisanCraftingRecipe recipe = recipeOpt.get().value();
                    if (idx < recipe.ingredients().size()) {
                        return recipe.ingredients().get(idx).test(stack);
                    }
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    @Override
    public void clear() {
        this.blueprint = ItemStack.EMPTY;
        this.stagedIngredients.clear();
        this.strikeCount = 0;
        this.thermalState = 0.0f;
        this.markDirtyAndSync();
        if (this.world instanceof ServerWorld serverWorld) {
            despawnEntities(serverWorld);
        }
    }

    /* =========================================================================
     * RECIPE QUERYING & TOOL VALIDATION
     * ========================================================================= */

    public Optional<RecipeEntry<ArtisanCraftingRecipe>> findMatchingRecipe() {
        if (this.world == null || this.blueprint.isEmpty()) {
            return Optional.empty();
        }
        ArtisanRecipeInput input = new ArtisanRecipeInput(this.blueprint, this.stagedIngredients);
        return this.world.getRecipeManager()
                .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE)
                .stream()
                .filter(entry -> entry.value().matches(input, this.world))
                .findFirst();
    }

    public Optional<RecipeEntry<ArtisanCraftingRecipe>> findRecipeForBlueprint(ItemStack blueprintStack) {
        if (this.world == null || blueprintStack.isEmpty()) {
            return Optional.empty();
        }
        return this.world.getRecipeManager()
                .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE)
                .stream()
                .filter(entry -> entry.value().matchesBlueprint(blueprintStack))
                .findFirst();
    }

    public Optional<RecipeEntry<ArtisanCraftingRecipe>> findMatchingRecipe(ItemStack workpiece, ItemStack tool) {
        if (this.world == null) return Optional.empty();
        boolean isHammer = tool.isOf(ModItems.FORGING_HAMMER);
        return this.world.getRecipeManager()
                .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE)
                .stream()
                .filter(entry -> (entry.value().matches(new SingleStackRecipeInput(workpiece), this.world)
                        || entry.value().matches(workpiece, tool))
                        && (isHammer || entry.value().matchesTool(tool)))
                .findFirst();
    }

    public boolean isValidStrikeTool(ItemStack toolStack) {
        if (toolStack.isEmpty()) return false;
        if (toolStack.isOf(ModItems.FORGING_HAMMER)) return true;
        if (this.hasItem()) {
            var recipeOpt = findRecipeForBlueprint(this.blueprint);
            if (recipeOpt.isPresent() && recipeOpt.get().value().matchesTool(toolStack)) {
                return true;
            }
        }
        return toolStack.getItem() instanceof MiningToolItem
                || toolStack.getItem() instanceof SwordItem
                || toolStack.getItem() instanceof ShearsItem;
    }

    private static String getIngredientDisplayName(Ingredient ingredient) {
        ItemStack[] matching = ingredient.getMatchingStacks();
        if (matching != null && matching.length > 0) {
            return matching[0].getName().getString();
        }
        return "Material";
    }

    private static String formatRequirementsList(ArtisanCraftingRecipe recipe) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ing : recipe.ingredients()) {
            String name = getIngredientDisplayName(ing);
            counts.put(name, counts.getOrDefault(name, 0) + 1);
        }
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (idx > 0) sb.append(", ");
            sb.append(entry.getValue()).append("x ").append(entry.getKey());
            idx++;
        }
        return sb.toString();
    }

    /* =========================================================================
     * PLACEMENT & RETRIEVAL PROTOCOLS
     * ========================================================================= */

    /**
     * Placement Protocol:
     * - If anvil has no blueprint: player must place a BlueprintItem first.
     * - If blueprint is present: player inserts matching workpiece materials sequentially.
     */
    public boolean insertItem(PlayerEntity player, Hand hand) {
        ItemStack playerStack = player.getStackInHand(hand);
        if (playerStack.isEmpty()) {
            return false;
        }

        // Case A: No blueprint present
        if (this.blueprint.isEmpty()) {
            if (playerStack.getItem() instanceof BlueprintItem) {
                this.blueprint = playerStack.split(1);
                this.strikeCount = 0;
                this.thermalState = 0.0f;
                this.markDirtyAndSync();

                if (this.world != null) {
                    this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.7f, 1.2f);
                }

                var recipeOpt = findRecipeForBlueprint(this.blueprint);
                if (recipeOpt.isPresent()) {
                    ArtisanCraftingRecipe recipe = recipeOpt.get().value();
                    String reqs = formatRequirementsList(recipe);
                    player.sendMessage(Text.literal("§a[Spark & Strike] " + this.blueprint.getName().getString()
                            + ": Requires " + reqs + " (0/" + recipe.ingredients().size() + " loaded)"), true);
                } else {
                    player.sendMessage(Text.literal("§a[Spark & Strike] Blueprint placed on anvil."), true);
                }

                if (this.world instanceof ServerWorld serverWorld) {
                    spawnInteractionEntity(serverWorld);
                }
                return true;
            } else {
                player.sendMessage(Text.literal("§e[Spark & Strike] Place a Blueprint stencil on the anvil first!"), true);
                if (this.world != null) {
                    this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 0.5f, 0.5f);
                }
                return false;
            }
        }

        // Case B: Blueprint is present -> stage sequential ingredients
        var recipeOpt = findRecipeForBlueprint(this.blueprint);
        if (recipeOpt.isEmpty()) {
            player.sendMessage(Text.literal("§c[Spark & Strike] Unknown blueprint recipe!"), true);
            return false;
        }

        ArtisanCraftingRecipe recipe = recipeOpt.get().value();
        int totalNeeded = recipe.ingredients().size();
        int currentStaged = this.stagedIngredients.size();

        if (currentStaged >= totalNeeded) {
            player.sendMessage(Text.literal("§a[Spark & Strike] (" + totalNeeded + "/" + totalNeeded
                    + " loaded) - Ready to Forge! Strike with Hammer!"), true);
            return false;
        }

        Ingredient nextRequired = recipe.ingredients().get(currentStaged);
        if (nextRequired.test(playerStack)) {
            ItemStack inserted = playerStack.split(1);
            this.stagedIngredients.add(inserted);
            this.markDirtyAndSync();

            if (this.world != null) {
                this.world.playSound(null, this.pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.8f, 1.2f);
            }

            int newCount = this.stagedIngredients.size();
            if (newCount == totalNeeded) {
                player.sendMessage(Text.literal("§a[Spark & Strike] (" + totalNeeded + "/" + totalNeeded
                        + " loaded) - Ready to Forge! Strike with Hammer!"), true);
            } else {
                String nextName = getIngredientDisplayName(recipe.ingredients().get(newCount));
                player.sendMessage(Text.literal("§e[Spark & Strike] (" + newCount + "/" + totalNeeded
                        + " loaded) - Needs: " + nextName), true);
            }
            return true;
        } else {
            String expectedName = getIngredientDisplayName(nextRequired);
            player.sendMessage(Text.literal("§c[Spark & Strike] Incorrect material! (" + currentStaged + "/" + totalNeeded
                    + " loaded) - Needs: " + expectedName), true);
            if (this.world != null) {
                this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 0.5f, 0.6f);
            }
            return false;
        }
    }

    /**
     * Retrieval Protocol (Normal Right-Click with Empty Hand):
     * Mistake correction: pops the LAST staged ingredient back to player.
     */
    public boolean extractItem(PlayerEntity player) {
        if (!this.stagedIngredients.isEmpty()) {
            ItemStack popped = this.stagedIngredients.remove(this.stagedIngredients.size() - 1);
            if (!player.getInventory().insertStack(popped)) {
                player.dropItem(popped, false);
            }
            this.markDirtyAndSync();

            if (this.world != null) {
                this.world.playSound(null, this.pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8f, 1.4f);
            }

            var recipeOpt = findRecipeForBlueprint(this.blueprint);
            if (recipeOpt.isPresent()) {
                ArtisanCraftingRecipe recipe = recipeOpt.get().value();
                int totalNeeded = recipe.ingredients().size();
                int current = this.stagedIngredients.size();
                String nextName = getIngredientDisplayName(recipe.ingredients().get(current));
                player.sendMessage(Text.literal("§6[Spark & Strike] Retrieved " + popped.getName().getString()
                        + " (" + current + "/" + totalNeeded + " loaded) - Needs: " + nextName), true);
            } else {
                player.sendMessage(Text.literal("§6[Spark & Strike] Retrieved last ingredient."), true);
            }
            return true;
        } else if (!this.blueprint.isEmpty()) {
            player.sendMessage(Text.literal("§e[Spark & Strike] No ingredients staged. Sneak + Right-Click to retrieve Blueprint."), true);
            return false;
        }
        return false;
    }

    /**
     * Retrieval Protocol (Sneak + Right-Click with Empty Hand):
     * Pops the Blueprint stencil back to player (along with any staged ingredients).
     */
    public boolean extractBlueprint(PlayerEntity player) {
        if (this.blueprint.isEmpty()) {
            return false;
        }

        // Return any remaining staged items first so nothing is lost
        while (!this.stagedIngredients.isEmpty()) {
            ItemStack ing = this.stagedIngredients.remove(this.stagedIngredients.size() - 1);
            if (!player.getInventory().insertStack(ing)) {
                player.dropItem(ing, false);
            }
        }

        ItemStack bp = this.blueprint.copy();
        this.blueprint = ItemStack.EMPTY;
        this.strikeCount = 0;
        this.thermalState = 0.0f;
        this.markDirtyAndSync();

        if (this.world instanceof ServerWorld serverWorld) {
            despawnEntities(serverWorld);
        }

        if (!player.getInventory().insertStack(bp)) {
            player.dropItem(bp, false);
        }

        if (this.world != null) {
            this.world.playSound(null, this.pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8f, 1.2f);
        }
        player.sendMessage(Text.literal("§6[Spark & Strike] Blueprint retrieved from anvil."), true);
        return true;
    }

    /* =========================================================================
     * FORGING PROTOCOL & STRIKE PROGRESSION
     * ========================================================================= */

    /**
     * Forging Protocol:
     * - Striking requires blueprint and ALL recipe ingredients staged.
     * - Incomplete staging is rejected with 0 progress.
     * - Completed craft drops result and KEEPS blueprint on anvil for rapid batching.
     */
    public boolean performStrike(PlayerEntity player, ItemStack toolStack) {
        if (this.world == null) return false;

        if (this.blueprint.isEmpty()) {
            player.sendMessage(Text.literal("§e[Spark & Strike] Place a Blueprint and materials on the anvil to forge!"), true);
            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 0.5f, 0.6f);
            return false;
        }

        var recipeOpt = findRecipeForBlueprint(this.blueprint);
        if (recipeOpt.isEmpty()) {
            player.sendMessage(Text.literal("§c[Spark & Strike] No matching recipe for active blueprint!"), true);
            return false;
        }

        ArtisanCraftingRecipe recipe = recipeOpt.get().value();
        boolean matchesTool = recipe.matchesTool(toolStack) || toolStack.isOf(ModItems.FORGING_HAMMER);
        if (!matchesTool) {
            player.sendMessage(Text.literal("§c[Spark & Strike] Requires a Forging Hammer or valid smithing tool!"), true);
            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 0.5f, 0.6f);
            return false;
        }

        int totalNeeded = recipe.ingredients().size();
        if (this.stagedIngredients.size() < totalNeeded) {
            String nextName = getIngredientDisplayName(recipe.ingredients().get(this.stagedIngredients.size()));
            player.sendMessage(Text.literal("§c[Spark & Strike] Cannot forge: Incomplete materials! ("
                    + this.stagedIngredients.size() + "/" + totalNeeded + " loaded) - Needs: " + nextName), true);
            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 0.6f, 0.6f);
            return false;
        }

        for (int i = 0; i < totalNeeded; i++) {
            if (!recipe.ingredients().get(i).test(this.stagedIngredients.get(i))) {
                player.sendMessage(Text.literal("§c[Spark & Strike] Staged materials do not match recipe!"), true);
                return false;
            }
        }

        // Advance strikes
        this.strikeCount++;
        if (!player.isCreative() && toolStack.isDamageable()) {
            toolStack.damage(1, player, EquipmentSlot.MAINHAND);
        }

        int required = recipe.requiredStrikes();
        int pct = Math.min(100, (int) (((float) this.strikeCount / required) * 100));
        this.thermalState = Math.min(1.0f, this.thermalState + 0.15f);

        double x = this.pos.getX() + 0.5;
        double y = this.pos.getY() + 1.05;
        double z = this.pos.getZ() + 0.5;

        if (this.strikeCount >= required) {
            // Crafting complete!
            ArtisanRecipeInput input = new ArtisanRecipeInput(this.blueprint, this.stagedIngredients);
            ItemStack resultStack = recipe.craft(input, this.world.getRegistryManager());

            // Consume workpiece materials, KEEP blueprint on anvil for batching
            this.stagedIngredients.clear();
            this.strikeCount = 0;
            this.thermalState = 0.0f;
            this.autoLoadCooldown = 5;

            ItemEntity outputEntity = new ItemEntity(this.world, x, y, z, resultStack);
            outputEntity.setToDefaultPickupDelay();
            this.world.spawnEntity(outputEntity);

            if (this.world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.1, z, 14, 0.25, 0.2, 0.25, 0.02);
                serverWorld.spawnParticles(ParticleTypes.CRIT, x, y, z, 16, 0.25, 0.15, 0.25, 0.2);
                serverWorld.spawnParticles(ParticleTypes.LAVA, x, y, z, 8, 0.2, 0.1, 0.2, 0.1);
            }

            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.0f, 1.2f);
            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.5f);

            player.sendMessage(Text.literal("§a[Spark & Strike] Successfully forged " + resultStack.getName().getString()
                    + "! Blueprint ready for next batch."), true);

            this.markDirtyAndSync();
            return true;
        } else {
            // Progressive strike
            float pitchProgress = required > 1 ? (float) this.strikeCount / required : 0.5f;
            float modulatedPitch = Math.min(1.6f, 0.85f + (pitchProgress * 0.55f));

            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.9f, modulatedPitch);

            if (this.world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.CRIT, x, y, z, 8, 0.15, 0.08, 0.15, 0.12);
                serverWorld.spawnParticles(ParticleTypes.LAVA, x, y, z, 4, 0.1, 0.05, 0.1, 0.08);
            }

            player.sendMessage(Text.literal("§6[Spark & Strike] Forging... Strike " + this.strikeCount + "/" + required
                    + " (" + pct + "%)"), true);

            this.markDirtyAndSync();
            return true;
        }
    }

    /* =========================================================================
     * INTERACTION ENTITY COORDINATION
     * ========================================================================= */

    private void spawnInteractionEntity(ServerWorld serverWorld) {
        despawnEntities(serverWorld);

        double x = this.pos.getX() + 0.5;
        double y = this.pos.getY() + 1.02;
        double z = this.pos.getZ() + 0.5;

        InteractionEntity interaction = EntityType.INTERACTION.create(serverWorld);
        if (interaction != null) {
            interaction.setPos(x, y - 0.2, z);
            NbtCompound interactionNbt = new NbtCompound();
            interactionNbt.putFloat("width", 0.8f);
            interactionNbt.putFloat("height", 0.6f);
            interactionNbt.putBoolean("response", true);
            interaction.readNbt(interactionNbt);

            serverWorld.spawnEntity(interaction);
            this.interactionEntityUuid = interaction.getUuid();
        }
    }

    public void despawnEntities(ServerWorld serverWorld) {
        if (this.interactionEntityUuid != null) {
            InteractionEntity interaction = findInteractionEntity(serverWorld);
            if (interaction != null) interaction.discard();
            this.interactionEntityUuid = null;
        }

        Box searchBox = new Box(this.pos).expand(1.2);
        List<DisplayEntity.ItemDisplayEntity> displays = serverWorld.getEntitiesByClass(
                DisplayEntity.ItemDisplayEntity.class, searchBox, e -> true);
        displays.forEach(DisplayEntity::discard);

        List<InteractionEntity> interactions = serverWorld.getEntitiesByClass(
                InteractionEntity.class, searchBox, e -> true);
        interactions.forEach(InteractionEntity::discard);
    }

    @Nullable
    private InteractionEntity findInteractionEntity(ServerWorld serverWorld) {
        if (this.interactionEntityUuid == null) return null;
        if (serverWorld.getEntity(this.interactionEntityUuid) instanceof InteractionEntity interaction) {
            return interaction;
        }
        return null;
    }

    /**
     * Invoked on block destruction for atomic cleanup with zero item or entity leaks.
     */
    public void cleanupOnBreak() {
        if (this.world instanceof ServerWorld serverWorld) {
            despawnEntities(serverWorld);
        }

        if (this.world != null) {
            double x = this.pos.getX() + 0.5;
            double y = this.pos.getY() + 0.5;
            double z = this.pos.getZ() + 0.5;

            if (!this.blueprint.isEmpty()) {
                ItemScatterer.spawn(this.world, x, y, z, this.blueprint.copy());
                this.blueprint = ItemStack.EMPTY;
            }
            for (ItemStack stack : this.stagedIngredients) {
                if (!stack.isEmpty()) {
                    ItemScatterer.spawn(this.world, x, y, z, stack.copy());
                }
            }
            this.stagedIngredients.clear();
        }
    }

    /**
     * Server tick handling interaction entity persistence and auto-load cooldown.
     */
    public static void tick(World world, BlockPos pos, BlockState state, ArtisanAnvilBlockEntity be) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        if (be.autoLoadCooldown > 0) {
            be.autoLoadCooldown--;
        }

        if (!be.blueprint.isEmpty()) {
            if (be.interactionEntityUuid == null || serverWorld.getEntity(be.interactionEntityUuid) == null) {
                be.spawnInteractionEntity(serverWorld);
            }
        }
    }

    /* =========================================================================
     * PERSISTENCE & CLIENT SYNCHRONIZATION (1.21.1 Yarn)
     * ========================================================================= */

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        super.readNbt(nbt, wrapperLookup);

        if (nbt.contains("blueprint", NbtElement.COMPOUND_TYPE)) {
            this.blueprint = ItemStack.fromNbtOrEmpty(wrapperLookup, nbt.getCompound("blueprint"));
        } else if (nbt.contains("held_item", NbtElement.COMPOUND_TYPE)) {
            ItemStack legacy = ItemStack.fromNbtOrEmpty(wrapperLookup, nbt.getCompound("held_item"));
            if (legacy.getItem() instanceof BlueprintItem) {
                this.blueprint = legacy;
            } else if (!legacy.isEmpty()) {
                this.stagedIngredients.clear();
                this.stagedIngredients.add(legacy);
            }
        } else {
            this.blueprint = ItemStack.EMPTY;
        }

        this.stagedIngredients.clear();
        if (nbt.contains("staged_ingredients", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("staged_ingredients", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size() && this.stagedIngredients.size() < MAX_STAGED_INGREDIENTS; i++) {
                ItemStack stack = ItemStack.fromNbtOrEmpty(wrapperLookup, list.getCompound(i));
                if (!stack.isEmpty()) {
                    this.stagedIngredients.add(stack);
                }
            }
        }

        this.strikeCount = nbt.getInt("strike_count");
        this.thermalState = nbt.getFloat("thermal_state");

        if (nbt.containsUuid("interaction_uuid")) {
            this.interactionEntityUuid = nbt.getUuid("interaction_uuid");
        } else {
            this.interactionEntityUuid = null;
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        super.writeNbt(nbt, wrapperLookup);

        if (!this.blueprint.isEmpty()) {
            nbt.put("blueprint", this.blueprint.encode(wrapperLookup));
        }

        if (!this.stagedIngredients.isEmpty()) {
            NbtList list = new NbtList();
            for (ItemStack stack : this.stagedIngredients) {
                list.add(stack.encode(wrapperLookup));
            }
            nbt.put("staged_ingredients", list);
        }

        nbt.putInt("strike_count", this.strikeCount);
        nbt.putFloat("thermal_state", this.thermalState);

        if (this.interactionEntityUuid != null) {
            nbt.putUuid("interaction_uuid", this.interactionEntityUuid);
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup wrapperLookup) {
        NbtCompound nbt = super.toInitialChunkDataNbt(wrapperLookup);
        if (!this.blueprint.isEmpty()) {
            nbt.put("blueprint", this.blueprint.encode(wrapperLookup));
        }
        if (!this.stagedIngredients.isEmpty()) {
            NbtList list = new NbtList();
            for (ItemStack stack : this.stagedIngredients) {
                list.add(stack.encode(wrapperLookup));
            }
            nbt.put("staged_ingredients", list);
        }
        nbt.putInt("strike_count", this.strikeCount);
        nbt.putFloat("thermal_state", this.thermalState);
        return nbt;
    }

    private void markDirtyAndSync() {
        this.markDirty();
        if (this.world != null) {
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
        }
        if (this.world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(this.pos);
        }
    }
}
