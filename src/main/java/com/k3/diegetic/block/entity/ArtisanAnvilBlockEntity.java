package com.k3.diegetic.block.entity;

import com.k3.diegetic.block.ArtisanAnvilBlock;
import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.component.ModDataComponentTypes;
import com.k3.diegetic.component.WorkstationStateComponent;
import com.k3.diegetic.recipe.ArtisanCraftingRecipe;
import com.k3.diegetic.recipe.ModRecipes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Clearable;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Diegetic workstation BlockEntity for Artisan Anvil.
 *
 * Core Guarantees:
 * 1. PURE DIEGETIC: Zero 2D container GUI screen handlers or ExtendedScreenHandlerFactory.
 * 2. DATA COMPONENT INTEGRATION: Strict zero calls to ItemStack#getTag(), setTag(), or getOrCreateTag().
 * 3. DISPLAY ENTITY COORDINATION: Coordinates synchronized vanilla ItemDisplayEntity & InteractionEntity.
 * 4. ATOMIC WORLD CLEANUP: Block break despawns entities and scatters active items cleanly.
 */
public class ArtisanAnvilBlockEntity extends BlockEntity implements Clearable, SidedInventory {

    /** Active workpiece item held on the workstation surface. */
    private ItemStack heldStack = ItemStack.EMPTY;

    /** UUID of the active visual ItemDisplayEntity. */
    @Nullable
    private UUID displayEntityUuid = null;

    /** UUID of the active clickable InteractionEntity. */
    @Nullable
    private UUID interactionEntityUuid = null;

    /** Cooldown ticks after a craft completes before auto-loading in-world items. */
    private int autoLoadCooldown = 0;

    public ArtisanAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ARTISAN_ANVIL_BLOCK_ENTITY, pos, state);
    }

    /* =========================================================================
     * INVENTORY & STATE MANAGEMENT (SidedInventory Automation)
     * ========================================================================= */

    public boolean hasItem() {
        return !this.heldStack.isEmpty();
    }

    public ItemStack getHeldStack() {
        return this.heldStack;
    }

    public void setHeldStack(ItemStack stack) {
        this.setStack(0, stack);
    }

    @Nullable
    public UUID getDisplayEntityUuid() {
        return this.displayEntityUuid;
    }

    @Nullable
    public UUID getInteractionEntityUuid() {
        return this.interactionEntityUuid;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.heldStack.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot == 0 ? this.heldStack : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == 0 && !this.heldStack.isEmpty() && amount > 0) {
            ItemStack split = this.heldStack.split(amount);
            if (this.heldStack.isEmpty()) {
                this.clear();
            } else {
                this.markDirtyAndSync();
                if (this.world instanceof ServerWorld serverWorld) {
                    spawnDisplayAndInteraction(serverWorld);
                }
            }
            return split;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot == 0 && !this.heldStack.isEmpty()) {
            ItemStack stack = this.heldStack;
            this.heldStack = ItemStack.EMPTY;
            this.clear();
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            if (!stack.isEmpty()) {
                ItemStack toHold = stack.copy();
                if (toHold.getCount() > getMaxCountPerStack()) {
                    toHold.setCount(getMaxCountPerStack());
                }
                if (!toHold.contains(ModDataComponentTypes.WORKSTATION_STATE)) {
                    WorkstationStateComponent state = WorkstationStateComponent.DEFAULT.withActive(true);
                    if (this.world != null) {
                        var matchOpt = this.world.getRecipeManager()
                                .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE)
                                .stream()
                                .filter(entry -> entry.value().matches(new SingleStackRecipeInput(toHold), this.world))
                                .findFirst();
                        if (matchOpt.isPresent()) {
                            state = state.withRecipe(matchOpt.get().id());
                        }
                    }
                    toHold.set(ModDataComponentTypes.WORKSTATION_STATE, state);
                }
                this.heldStack = toHold;
                this.markDirtyAndSync();
                if (this.world instanceof ServerWorld serverWorld) {
                    spawnDisplayAndInteraction(serverWorld);
                }
            } else {
                this.clear();
            }
        }
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot != 0 || stack.isEmpty() || this.world == null) return false;
        SingleStackRecipeInput input = new SingleStackRecipeInput(stack);
        return this.world.getRecipeManager()
                .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE)
                .stream()
                .anyMatch(entry -> entry.value().matches(input, this.world));
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return this.world != null && this.world.getBlockEntity(this.pos) == this
                && player.squaredDistanceTo(this.pos.toCenterPos()) <= 64.0;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.DOWN) {
            return new int[0];
        }
        return new int[]{0};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot != 0 || dir == Direction.DOWN || !this.heldStack.isEmpty() || stack.isEmpty()) {
            return false;
        }
        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // Unworked workpiece is protected from being pulled out by bottom hopper
        return false;
    }

    public boolean insertItemDirectly(ItemStack stack) {
        if (stack.isEmpty() || this.hasItem()) return false;
        ItemStack inserted = stack.split(1);
        this.setStack(0, inserted);
        if (this.world != null) {
            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.7f, 1.2f);
        }
        return true;
    }

    @Override
    public void clear() {
        this.heldStack = ItemStack.EMPTY;
        this.markDirtyAndSync();
        if (this.world instanceof ServerWorld serverWorld) {
            despawnEntities(serverWorld);
        }
    }

    /**
     * Checks if a tool is eligible to strike the active workpiece.
     * Matches recipes or fallback tools/weapons.
     */
    public boolean isValidStrikeTool(ItemStack toolStack) {
        if (toolStack.isEmpty()) return false;
        if (toolStack.isOf(com.k3.diegetic.item.ModItems.FORGING_HAMMER)) return true;
        if (this.hasItem()) {
            return findMatchingRecipe(this.heldStack, toolStack).isPresent()
                    || toolStack.getItem() instanceof MiningToolItem
                    || toolStack.getItem() instanceof SwordItem
                    || toolStack.getItem() instanceof ShearsItem;
        }
        return toolStack.getItem() instanceof MiningToolItem
                || toolStack.getItem() instanceof SwordItem
                || toolStack.getItem() instanceof ShearsItem;
    }

    /**
     * Queries active ArtisanCraftingRecipe for workpiece and tool.
     * The Forging Hammer is universally accepted for all artisan recipes.
     */
    public Optional<RecipeEntry<ArtisanCraftingRecipe>> findMatchingRecipe(ItemStack workpiece, ItemStack tool) {
        if (this.world == null || workpiece.isEmpty()) return Optional.empty();
        SingleStackRecipeInput input = new SingleStackRecipeInput(workpiece);
        boolean isHammer = tool.isOf(com.k3.diegetic.item.ModItems.FORGING_HAMMER);
        return this.world.getRecipeManager()
                .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE)
                .stream()
                .filter(entry -> entry.value().matches(input, this.world) && (isHammer || entry.value().matchesTool(tool)))
                .findFirst();
    }

    /* =========================================================================
     * PLAYER INTERACTION HANDLERS
     * ========================================================================= */

    /**
     * Inserts 1 item from player hand onto the workstation surface.
     * Attaches initial WorkstationStateComponent and spawns display entities.
     */
    public boolean insertItem(PlayerEntity player, Hand hand) {
        ItemStack playerStack = player.getStackInHand(hand);
        if (playerStack.isEmpty() || this.hasItem()) return false;

        // Take 1 item from player
        ItemStack inserted = playerStack.split(1);

        // Ensure modern WorkstationStateComponent is attached
        WorkstationStateComponent state = WorkstationStateComponent.DEFAULT.withActive(true);
        if (this.world != null) {
            var matchOpt = this.world.getRecipeManager()
                    .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE)
                    .stream()
                    .filter(entry -> entry.value().matches(new SingleStackRecipeInput(inserted), this.world))
                    .findFirst();
            if (matchOpt.isPresent()) {
                state = state.withRecipe(matchOpt.get().id());
            }
        }
        inserted.set(ModDataComponentTypes.WORKSTATION_STATE, state);

        this.heldStack = inserted;
        this.markDirtyAndSync();

        // Spawn visual and interaction entities on server
        if (this.world instanceof ServerWorld serverWorld) {
            spawnDisplayAndInteraction(serverWorld);
        }

        // Sound feedback
        if (this.world != null) {
            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.7f, 1.2f);
        }
        return true;
    }

    /**
     * Extracts held workpiece into player inventory or drops at player feet.
     */
    public boolean extractItem(PlayerEntity player) {
        if (!this.hasItem()) return false;

        ItemStack toExtract = this.heldStack.copy();
        this.heldStack = ItemStack.EMPTY;

        // Despawn display entities
        if (this.world instanceof ServerWorld serverWorld) {
            despawnEntities(serverWorld);
        }

        if (!player.getInventory().insertStack(toExtract)) {
            player.dropItem(toExtract, false);
        }

        this.markDirtyAndSync();
        if (this.world != null) {
            this.world.playSound(null, this.pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8f, 1.4f);
        }
        return true;
    }

    /**
     * Advances crafting progress upon player tool strike.
     * Emits diegetic sound, particle effects, updates display interpolation, damages tool, and crafts output.
     */
    public boolean performStrike(PlayerEntity player, ItemStack toolStack) {
        if (!this.hasItem() || this.world == null) return false;

        Optional<RecipeEntry<ArtisanCraftingRecipe>> recipeOpt = findMatchingRecipe(this.heldStack, toolStack);
        if (recipeOpt.isEmpty()) {
            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 0.6f, 0.6f);
            return false;
        }

        RecipeEntry<ArtisanCraftingRecipe> recipeEntry = recipeOpt.get();
        ArtisanCraftingRecipe recipe = recipeEntry.value();

        // Read immutable component
        WorkstationStateComponent current = this.heldStack.getOrDefault(
                ModDataComponentTypes.WORKSTATION_STATE,
                WorkstationStateComponent.DEFAULT
        );

        int currentStrikes = current.strikeCount();
        int newStrikes = currentStrikes + 1;
        int requiredStrikes = recipe.requiredStrikes();
        int newProgress = (int) (((float) newStrikes / requiredStrikes) * 100);
        float newThermal = Math.min(1.0f, current.thermalState() + 0.15f);

        // Damage tool if player is not in creative mode
        if (!player.isCreative() && toolStack.isDamageable()) {
            toolStack.damage(1, player, EquipmentSlot.MAINHAND);
        }

        double x = this.pos.getX() + 0.5;
        double y = this.pos.getY() + 1.05;
        double z = this.pos.getZ() + 0.5;

        if (newStrikes >= requiredStrikes) {
            // Crafting complete: spawn output result stack
            ItemStack resultStack = recipe.craft(
                    new SingleStackRecipeInput(this.heldStack),
                    this.world.getRegistryManager()
            );

            if (this.world instanceof ServerWorld serverWorld) {
                despawnEntities(serverWorld);
                serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.1, z, 12, 0.25, 0.2, 0.25, 0.02);
                serverWorld.spawnParticles(ParticleTypes.CRIT, x, y, z, 16, 0.25, 0.15, 0.25, 0.2);
                serverWorld.spawnParticles(ParticleTypes.LAVA, x, y, z, 8, 0.2, 0.1, 0.2, 0.1);
            }

            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.0f, 1.2f);
            this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.5f);

            this.heldStack = ItemStack.EMPTY;
            this.autoLoadCooldown = 5;

            ItemEntity outputEntity = new ItemEntity(this.world, x, y, z, resultStack);
            outputEntity.setToDefaultPickupDelay();
            this.world.spawnEntity(outputEntity);

            this.markDirtyAndSync();
            return true;
        } else {
            // Progressive strike
            WorkstationStateComponent updated = current
                    .withRecipe(recipeEntry.id())
                    .withStrike(newStrikes, newProgress)
                    .withThermalState(newThermal)
                    .withActive(true);

            this.heldStack.set(ModDataComponentTypes.WORKSTATION_STATE, updated);
            this.markDirtyAndSync();

            float pitchProgress = requiredStrikes > 1 ? (float) newStrikes / requiredStrikes : 0.5f;
            float modulatedPitch = Math.min(1.6f, 0.85f + (pitchProgress * 0.55f));

            if (newStrikes == 1) {
                this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 0.9f, modulatedPitch);
            } else {
                this.world.playSound(null, this.pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.9f, modulatedPitch);
            }

            if (this.world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.CRIT, x, y, z, 8, 0.15, 0.08, 0.15, 0.12);
                serverWorld.spawnParticles(ParticleTypes.LAVA, x, y, z, 4, 0.1, 0.05, 0.1, 0.08);
                updateDisplayTransform(serverWorld, newStrikes);
            }

            return true;
        }
    }

    /**
     * Alias for performStrike.
     */
    public boolean handleToolStrike(PlayerEntity player, ItemStack toolStack) {
        return performStrike(player, toolStack);
    }

    /* =========================================================================
     * DISPLAY & INTERACTION ENTITY COORDINATION (Pattern #7)
     * ========================================================================= */

    private void spawnDisplayAndInteraction(ServerWorld serverWorld) {
        despawnEntities(serverWorld);

        if (this.heldStack.isEmpty()) return;

        double x = this.pos.getX() + 0.5;
        double y = this.pos.getY() + 1.02;
        double z = this.pos.getZ() + 0.5;

        Direction facing = this.getCachedState().contains(ArtisanAnvilBlock.FACING)
                ? this.getCachedState().get(ArtisanAnvilBlock.FACING)
                : Direction.NORTH;

        // 1. Spawning floating preview ItemDisplayEntity
        DisplayEntity.ItemDisplayEntity itemDisplay = EntityType.ITEM_DISPLAY.create(serverWorld);
        if (itemDisplay != null) {
            itemDisplay.setPos(x, y, z);
            itemDisplay.getStackReference(0).set(this.heldStack.copy());

            AffineTransformation transform = new AffineTransformation(
                    new Vector3f(0f, 0.02f, 0f),
                    new Quaternionf()
                            .rotateY((float) Math.toRadians(-facing.asRotation()))
                            .rotateX((float) Math.toRadians(90f)),
                    new Vector3f(0.6f, 0.6f, 0.6f),
                    null
            );

            NbtCompound displayNbt = new NbtCompound();
            AffineTransformation.CODEC.encodeStart(NbtOps.INSTANCE, transform)
                    .ifSuccess(tag -> displayNbt.put(DisplayEntity.TRANSFORMATION_NBT_KEY, tag));
            displayNbt.putInt(DisplayEntity.INTERPOLATION_DURATION_KEY, 5);
            displayNbt.putInt(DisplayEntity.START_INTERPOLATION_KEY, 0);
            displayNbt.putString(DisplayEntity.BILLBOARD_NBT_KEY, "fixed");
            displayNbt.putString("item_display", "fixed");
            displayNbt.put("item", this.heldStack.encode(serverWorld.getRegistryManager()));
            itemDisplay.readNbt(displayNbt);
            itemDisplay.getStackReference(0).set(this.heldStack.copy());

            serverWorld.spawnEntity(itemDisplay);
            this.displayEntityUuid = itemDisplay.getUuid();
        }

        // 2. Spawn invisible InteractionEntity hitbox
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

    private void updateDisplayTransform(ServerWorld serverWorld, int strikes) {
        DisplayEntity.ItemDisplayEntity display = findDisplayEntity(serverWorld);
        if (display != null) {
            display.getStackReference(0).set(this.heldStack.copy());
            Direction facing = this.getCachedState().contains(ArtisanAnvilBlock.FACING)
                    ? this.getCachedState().get(ArtisanAnvilBlock.FACING)
                    : Direction.NORTH;
            float wobble = (strikes % 2 == 0 ? 0.05f : -0.05f);
            AffineTransformation transform = new AffineTransformation(
                    new Vector3f(0f, 0.02f, 0f),
                    new Quaternionf()
                            .rotateY((float) Math.toRadians(-facing.asRotation()))
                            .rotateX((float) Math.toRadians(90f))
                            .rotateZ(wobble),
                    new Vector3f(0.6f, 0.6f, 0.6f),
                    null
            );

            NbtCompound displayNbt = new NbtCompound();
            AffineTransformation.CODEC.encodeStart(NbtOps.INSTANCE, transform)
                    .ifSuccess(tag -> displayNbt.put(DisplayEntity.TRANSFORMATION_NBT_KEY, tag));
            displayNbt.putInt(DisplayEntity.INTERPOLATION_DURATION_KEY, 3);
            displayNbt.putInt(DisplayEntity.START_INTERPOLATION_KEY, 0);
            displayNbt.put("item", this.heldStack.encode(serverWorld.getRegistryManager()));
            display.readNbt(displayNbt);
            display.getStackReference(0).set(this.heldStack.copy());
        }
    }

    public void despawnEntities(ServerWorld serverWorld) {
        if (this.displayEntityUuid != null) {
            DisplayEntity.ItemDisplayEntity display = findDisplayEntity(serverWorld);
            if (display != null) display.discard();
            this.displayEntityUuid = null;
        }

        if (this.interactionEntityUuid != null) {
            InteractionEntity interaction = findInteractionEntity(serverWorld);
            if (interaction != null) interaction.discard();
            this.interactionEntityUuid = null;
        }

        // Fallback: sweep local AABB bounding box to guarantee ZERO ghost entity leaks
        Box searchBox = new Box(this.pos).expand(1.2);
        List<DisplayEntity.ItemDisplayEntity> displays = serverWorld.getEntitiesByClass(
                DisplayEntity.ItemDisplayEntity.class, searchBox, e -> true);
        displays.forEach(DisplayEntity::discard);

        List<InteractionEntity> interactions = serverWorld.getEntitiesByClass(
                InteractionEntity.class, searchBox, e -> true);
        interactions.forEach(InteractionEntity::discard);
    }

    @Nullable
    private DisplayEntity.ItemDisplayEntity findDisplayEntity(ServerWorld serverWorld) {
        if (this.displayEntityUuid == null) return null;
        if (serverWorld.getEntity(this.displayEntityUuid) instanceof DisplayEntity.ItemDisplayEntity display) {
            return display;
        }
        return null;
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
     * Invoked by ArtisanAnvilBlock#onStateReplaced when block is broken.
     * Guarantees atomic world cleanup: despawns entities and drops active workpiece.
     */
    public void cleanupOnBreak() {
        if (this.world instanceof ServerWorld serverWorld) {
            despawnEntities(serverWorld);
        }

        if (!this.heldStack.isEmpty() && this.world != null) {
            ItemScatterer.spawn(
                    this.world,
                    this.pos.getX() + 0.5,
                    this.pos.getY() + 0.5,
                    this.pos.getZ() + 0.5,
                    this.heldStack.copy()
            );
            this.heldStack = ItemStack.EMPTY;
        }
    }

    /**
     * Server tick enforcing self-healing reconciliation against chunk loads
     * and in-world item entity auto-loading (Dropper, water stream, player drop).
     */
    public static void tick(World world, BlockPos pos, BlockState state, ArtisanAnvilBlockEntity be) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        if (be.autoLoadCooldown > 0) {
            be.autoLoadCooldown--;
        }

        if (!be.heldStack.isEmpty()) {
            boolean needsUpdate = false;
            if (be.displayEntityUuid == null || serverWorld.getEntity(be.displayEntityUuid) == null) {
                needsUpdate = true;
            }
            if (be.interactionEntityUuid == null || serverWorld.getEntity(be.interactionEntityUuid) == null) {
                needsUpdate = true;
            }
            if (needsUpdate) {
                be.spawnDisplayAndInteraction(serverWorld);
            }
        } else if (be.autoLoadCooldown == 0) {
            // Check for floating ItemEntity above the anvil to auto-load (Dropper, water stream, player drop)
            Box pickupBox = new Box(
                    pos.getX() - 0.1, pos.getY() + 0.6, pos.getZ() - 0.1,
                    pos.getX() + 1.1, pos.getY() + 1.6, pos.getZ() + 1.1
            );
            List<ItemEntity> items = serverWorld.getEntitiesByClass(
                    ItemEntity.class,
                    pickupBox,
                    entity -> !entity.isRemoved() && entity.isAlive() && !entity.getStack().isEmpty()
            );

            for (ItemEntity itemEntity : items) {
                ItemStack entityStack = itemEntity.getStack();
                SingleStackRecipeInput input = new SingleStackRecipeInput(entityStack);
                boolean matches = serverWorld.getRecipeManager()
                        .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE)
                        .stream()
                        .anyMatch(entry -> entry.value().matches(input, serverWorld));

                if (matches) {
                    ItemStack inserted = entityStack.split(1);
                    if (entityStack.isEmpty()) {
                        itemEntity.discard();
                    } else {
                        itemEntity.setStack(entityStack);
                    }

                    be.setStack(0, inserted);
                    serverWorld.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.7f, 1.2f);
                    break;
                }
            }
        }
    }

    /* =========================================================================
     * PERSISTENCE & CLIENT SYNCHRONIZATION (1.21.1 Yarn)
     * ========================================================================= */

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        super.readNbt(nbt, wrapperLookup);

        if (nbt.contains("held_item", NbtElement.COMPOUND_TYPE)) {
            this.heldStack = ItemStack.fromNbtOrEmpty(wrapperLookup, nbt.getCompound("held_item"));
        } else {
            this.heldStack = ItemStack.EMPTY;
        }

        if (nbt.containsUuid("display_uuid")) {
            this.displayEntityUuid = nbt.getUuid("display_uuid");
        } else {
            this.displayEntityUuid = null;
        }
        if (nbt.containsUuid("interaction_uuid")) {
            this.interactionEntityUuid = nbt.getUuid("interaction_uuid");
        } else {
            this.interactionEntityUuid = null;
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        super.writeNbt(nbt, wrapperLookup);

        if (!this.heldStack.isEmpty()) {
            nbt.put("held_item", this.heldStack.encode(wrapperLookup));
        }

        if (this.displayEntityUuid != null) {
            nbt.putUuid("display_uuid", this.displayEntityUuid);
        }
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
        if (!this.heldStack.isEmpty()) {
            nbt.put("held_item", this.heldStack.encode(wrapperLookup));
        }
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
