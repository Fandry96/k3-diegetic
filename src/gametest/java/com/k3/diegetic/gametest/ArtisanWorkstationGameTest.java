package com.k3.diegetic.gametest;

import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import com.k3.diegetic.component.ModDataComponentTypes;
import com.k3.diegetic.recipe.ArtisanCraftingRecipe;
import com.k3.diegetic.recipe.ModRecipes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Headless GameTest suite verifying:
 * 1. Recipe Registry & Dual-Input Recipe Matching (Scenario A Ingot Smithing vs Scenario B Gem Cutting).
 * 2. In-World Workstation Lifecycle (Placement, workpiece insertion, strike progression with tool, craft completion, item entity spawn).
 * 3. Atomic Cleanup on Block Break (100% entity despawn, zero ghost/orphan entities, clean item drop).
 */
public class ArtisanWorkstationGameTest implements FabricGameTest {

    // =========================================================================
    // TEST 1: RECIPE REGISTRY & DUAL-INPUT RECIPE MATCHING
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testRecipeRegistryAndDualInputMatching(TestContext context) {
        // 1. Verify Registries
        Identifier recipeId = Identifier.of("k3_diegetic", "artisan_crafting");
        context.assertTrue(Registries.RECIPE_TYPE.containsId(recipeId), "ARTISAN_CRAFTING_TYPE must be registered");
        context.assertTrue(Registries.RECIPE_SERIALIZER.containsId(recipeId), "ARTISAN_CRAFTING_SERIALIZER must be registered");

        // 2. Scenario A: Ingot Smithing (Iron Ingot + Iron Pickaxe -> 3 strikes -> Iron Sword)
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack ironPickaxe = new ItemStack(Items.IRON_PICKAXE);
        SingleStackRecipeInput ingotInput = new SingleStackRecipeInput(ironIngot);

        List<RecipeEntry<ArtisanCraftingRecipe>> recipes = context.getWorld().getRecipeManager()
                .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE);

        context.assertTrue(!recipes.isEmpty(), "Artisan crafting recipe list must not be empty");

        Optional<RecipeEntry<ArtisanCraftingRecipe>> smithingOpt = recipes.stream()
                .filter(entry -> entry.value().matches(ingotInput, context.getWorld()) && entry.value().matchesTool(ironPickaxe))
                .findFirst();

        context.assertTrue(smithingOpt.isPresent(), "Scenario A: Ingot Smithing recipe must match (Iron Ingot + Pickaxe)");
        ArtisanCraftingRecipe smithingRecipe = smithingOpt.get().value();
        context.assertEquals(3, smithingRecipe.requiredStrikes(), "Ingot Smithing must require 3 strikes");
        context.assertTrue(smithingRecipe.getResult(context.getWorld().getRegistryManager()).isOf(Items.IRON_SWORD),
                "Ingot Smithing result must be an Iron Sword");

        // 3. Scenario B: Gem Cutting (Amethyst Shard + Shears -> 2 strikes -> Diamond)
        ItemStack amethystShard = new ItemStack(Items.AMETHYST_SHARD);
        ItemStack shears = new ItemStack(Items.SHEARS);
        SingleStackRecipeInput gemInput = new SingleStackRecipeInput(amethystShard);

        Optional<RecipeEntry<ArtisanCraftingRecipe>> cuttingOpt = recipes.stream()
                .filter(entry -> entry.value().matches(gemInput, context.getWorld()) && entry.value().matchesTool(shears))
                .findFirst();

        context.assertTrue(cuttingOpt.isPresent(), "Scenario B: Gem Cutting recipe must match (Amethyst Shard + Shears)");
        ArtisanCraftingRecipe cuttingRecipe = cuttingOpt.get().value();
        context.assertEquals(2, cuttingRecipe.requiredStrikes(), "Gem Cutting must require 2 strikes");
        context.assertTrue(cuttingRecipe.getResult(context.getWorld().getRegistryManager()).isOf(Items.DIAMOND),
                "Gem Cutting result must be a Diamond");

        // 4. Cross-Scenario Mismatch Validation (Anti-Overfitting Negative Assertions)
        context.assertFalse(smithingRecipe.matchesTool(shears), "Iron Ingot recipe must reject Shears tool");
        context.assertFalse(cuttingRecipe.matchesTool(ironPickaxe), "Amethyst Shard recipe must reject Pickaxe tool");
        context.assertFalse(smithingRecipe.matches(gemInput, context.getWorld()), "Ingot Smithing must reject Amethyst Shard input");
        context.assertFalse(cuttingRecipe.matches(ingotInput, context.getWorld()), "Gem Cutting must reject Iron Ingot input");

        context.complete();
    }

    // =========================================================================
    // TEST 2: IN-WORLD WORKSTATION LIFECYCLE (SCENARIO A: INGOT SMITHING)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldWorkstationLifecycleSmithing(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");
        context.assertFalse(anvilBe.hasItem(), "Initial workstation state must be empty");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step A: Insert workpiece item
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1));
        boolean inserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(inserted, "Inserting iron ingot must succeed");
        context.assertTrue(anvilBe.hasItem(), "Workstation must hold workpiece");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.IRON_INGOT), "Held workpiece must be iron ingot");
        context.assertTrue(anvilBe.getHeldStack().contains(ModDataComponentTypes.WORKSTATION_STATE),
                "Inserted item must receive WorkstationStateComponent");
        context.assertEquals(0, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Initial strike count must be 0");
        context.assertTrue(anvilBe.getDisplayEntityUuid() != null, "Display entity UUID must be set on insertion");
        context.assertTrue(anvilBe.getInteractionEntityUuid() != null, "Interaction entity UUID must be set on insertion");

        // Step B: Tool Strike 1
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);
        boolean strike1 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike1, "Strike 1 must succeed with pickaxe");
        context.assertTrue(anvilBe.hasItem(), "Workpiece must still be on workstation after strike 1");
        context.assertEquals(1, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Strike count must advance to 1");

        // Step C: Tool Strike 2
        boolean strike2 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike2, "Strike 2 must succeed with pickaxe");
        context.assertTrue(anvilBe.hasItem(), "Workpiece must still be on workstation after strike 2");
        context.assertEquals(2, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Strike count must advance to 2");

        // Step D: Tool Strike 3 (Craft completion threshold reached)
        boolean strike3 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike3, "Strike 3 must complete the craft");
        context.assertFalse(anvilBe.hasItem(), "Workstation workpiece must be cleared after completion");

        // Assert crafted output was spawned into world
        context.expectItemAt(Items.IRON_SWORD, pos, 2.0);

        context.complete();
    }

    // =========================================================================
    // TEST 3: IN-WORLD WORKSTATION LIFECYCLE (SCENARIO B: GEM CUTTING)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldWorkstationLifecycleCutting(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step A: Insert amethyst shard
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 1));
        boolean inserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(inserted, "Inserting amethyst shard must succeed");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.AMETHYST_SHARD), "Held workpiece must be amethyst shard");

        // Step B: Invalid tool strike rejection (iron pickaxe cannot cut amethyst)
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        boolean invalidStrike = anvilBe.performStrike(player, pickaxe);
        context.assertFalse(invalidStrike, "Striking amethyst shard with pickaxe must be rejected");
        context.assertEquals(0, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Strike count must not advance on invalid tool");

        // Step C: Strike 1 with shears
        ItemStack shears = new ItemStack(Items.SHEARS);
        boolean strike1 = anvilBe.performStrike(player, shears);
        context.assertTrue(strike1, "Strike 1 with shears must succeed");
        context.assertEquals(1, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Strike count must advance to 1");

        // Step D: Strike 2 with shears (threshold 2 reached -> Diamond crafted)
        boolean strike2 = anvilBe.performStrike(player, shears);
        context.assertTrue(strike2, "Strike 2 with shears must complete gem cutting");
        context.assertFalse(anvilBe.hasItem(), "Workstation workpiece must be cleared after completion");

        // Assert diamond was spawned
        context.expectItemAt(Items.DIAMOND, pos, 2.0);

        context.complete();
    }

    // =========================================================================
    // TEST 4: ATOMIC CLEANUP ON BLOCK BREAK (ZERO ORPHAN ENTITY GUARANTEE)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testAtomicCleanupOnBlockBreak(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // 1. Insert item to spawn Display and Interaction entities
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        UUID displayUuid = anvilBe.getDisplayEntityUuid();
        UUID interactionUuid = anvilBe.getInteractionEntityUuid();

        context.assertTrue(displayUuid != null, "Display entity UUID must exist");
        context.assertTrue(interactionUuid != null, "Interaction entity UUID must exist");

        var displayEntity = context.getWorld().getEntity(displayUuid);
        var interactionEntity = context.getWorld().getEntity(interactionUuid);

        context.assertTrue(displayEntity != null && !displayEntity.isRemoved(),
                "Display entity must be active in world before block break");
        context.assertTrue(interactionEntity != null && !interactionEntity.isRemoved(),
                "Interaction entity must be active in world before block break");

        // 2. Break the workstation block mid-craft
        context.removeBlock(pos);

        // 3. Verify tracked entities are discarded
        var checkDisplay = context.getWorld().getEntity(displayUuid);
        context.assertTrue(checkDisplay == null || checkDisplay.isRemoved(),
                "Display entity must be discarded after block destruction");

        var checkInteraction = context.getWorld().getEntity(interactionUuid);
        context.assertTrue(checkInteraction == null || checkInteraction.isRemoved(),
                "Interaction entity must be discarded after block destruction");

        // 4. Assert zero orphan Display or Interaction entities remain in the surrounding area
        var orphanDisplays = context.getEntitiesAround(EntityType.ITEM_DISPLAY, pos, 3.0);
        context.assertTrue(orphanDisplays.isEmpty(), "Zero orphan ItemDisplayEntities must remain in world");

        var orphanInteractions = context.getEntitiesAround(EntityType.INTERACTION, pos, 3.0);
        context.assertTrue(orphanInteractions.isEmpty(), "Zero orphan InteractionEntities must remain in world");

        // 5. Assert the active workpiece was safely dropped into world (no item loss)
        context.expectItemAt(Items.IRON_INGOT, pos, 2.0);

        context.complete();
    }

    // =========================================================================
    // TEST 5: HOPPER SIDED INVENTORY DIRECT INSERTION & FILTER REJECTION
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testHopperSidedInventoryDirectInsertionAndFilterRejection(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");
        context.assertEquals(1, anvilBe.size(), "Workstation inventory size must be 1");
        context.assertEquals(1, anvilBe.getMaxCountPerStack(), "Max stack count per slot must be 1");

        // 1. Dual-Input Validation: Scenario A Ingot Smithing (Iron Ingot)
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        context.assertTrue(anvilBe.canInsert(0, ironIngot, Direction.UP), "Hopper must be allowed to insert Iron Ingot from top");
        context.assertTrue(anvilBe.canInsert(0, ironIngot, Direction.NORTH), "Hopper must be allowed to insert Iron Ingot from side");
        context.assertFalse(anvilBe.canInsert(0, ironIngot, Direction.DOWN), "Hopper must reject insertion from bottom");

        // Insert Iron Ingot via inventory automation
        anvilBe.setStack(0, ironIngot);
        context.assertTrue(anvilBe.hasItem(), "Workstation must hold workpiece after inventory insertion");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.IRON_INGOT), "Held item must be Iron Ingot");
        context.assertTrue(anvilBe.getHeldStack().contains(ModDataComponentTypes.WORKSTATION_STATE),
                "Direct inventory insertion must attach WorkstationStateComponent");

        // Full slot rejection
        context.assertFalse(anvilBe.canInsert(0, ironIngot, Direction.UP),
                "Hopper must reject insertion when slot 0 is already occupied");

        // Extraction protection: unworked workpiece must never be pulled out from below
        context.assertFalse(anvilBe.canExtract(0, anvilBe.getStack(0), Direction.DOWN),
                "Unworked workpiece must be protected against extraction from below");
        context.assertEquals(0, anvilBe.getAvailableSlots(Direction.DOWN).length,
                "Bottom face must expose zero slots for automated pulling");

        // Clear for next check
        anvilBe.clear();
        context.assertFalse(anvilBe.hasItem(), "Workpiece must be cleared");

        // 2. Dual-Input Validation: Scenario B Gem Cutting (Amethyst Shard)
        ItemStack amethystShard = new ItemStack(Items.AMETHYST_SHARD);
        context.assertTrue(anvilBe.canInsert(0, amethystShard, Direction.UP), "Hopper must accept Amethyst Shard from top");
        context.assertTrue(anvilBe.canInsert(0, amethystShard, Direction.WEST), "Hopper must accept Amethyst Shard from side");
        anvilBe.setStack(0, amethystShard);
        context.assertTrue(anvilBe.hasItem(), "Workstation must hold workpiece after amethyst insertion");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.AMETHYST_SHARD), "Held item must be Amethyst Shard");
        anvilBe.clear();

        // 3. Anti-Overfitting Negative Filter: non-recipe ingredients MUST be rejected
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack cobblestone = new ItemStack(Items.COBBLESTONE);
        ItemStack stick = new ItemStack(Items.STICK);

        context.assertFalse(anvilBe.isValid(0, dirt), "Dirt must be invalid inventory input");
        context.assertFalse(anvilBe.canInsert(0, dirt, Direction.UP), "Hopper must reject Dirt insertion");
        context.assertFalse(anvilBe.canInsert(0, cobblestone, Direction.NORTH), "Hopper must reject Cobblestone insertion");
        context.assertFalse(anvilBe.canInsert(0, stick, Direction.UP), "Hopper must reject Stick insertion");

        context.complete();
    }

    // =========================================================================
    // TEST 6: IN-WORLD ITEM ENTITY AUTO-LOADING (SCENARIO A: INGOT SMITHING)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldItemEntityAutoLoadingSmithing(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        BlockPos absPos = context.getAbsolutePos(pos);

        // Spawn floating Iron Ingot ItemEntity right above the anvil (simulating dropper or drop)
        ItemEntity droppedItem = new ItemEntity(
                context.getWorld(),
                absPos.getX() + 0.5,
                absPos.getY() + 1.1,
                absPos.getZ() + 0.5,
                new ItemStack(Items.IRON_INGOT, 1)
        );
        context.getWorld().spawnEntity(droppedItem);

        // Tick block entity to execute auto-loading
        ArtisanAnvilBlockEntity.tick(context.getWorld(), absPos, context.getBlockState(pos), anvilBe);

        // Verify workpiece auto-loaded onto workstation
        context.assertTrue(anvilBe.hasItem(), "Workstation must auto-load in-world dropped Iron Ingot");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.IRON_INGOT), "Workpiece must be Iron Ingot");
        context.assertTrue(anvilBe.getHeldStack().contains(ModDataComponentTypes.WORKSTATION_STATE),
                "Auto-loaded workpiece must receive WorkstationStateComponent");
        context.assertTrue(droppedItem.isRemoved(), "Dropped item entity must be consumed upon auto-load");

        // Player strikes 3 times to complete craft
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);

        context.assertTrue(anvilBe.performStrike(player, pickaxe), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, pickaxe), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, pickaxe), "Strike 3 must complete craft");

        context.assertFalse(anvilBe.hasItem(), "Workstation must be cleared after craft");
        context.expectItemAt(Items.IRON_SWORD, pos, 2.0);

        context.complete();
    }

    // =========================================================================
    // TEST 7: IN-WORLD ITEM ENTITY AUTO-LOADING (SCENARIO B: GEM CUTTING)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldItemEntityAutoLoadingCutting(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        BlockPos absPos = context.getAbsolutePos(pos);

        // Spawn floating Amethyst Shard ItemEntity right above the anvil
        ItemEntity droppedGem = new ItemEntity(
                context.getWorld(),
                absPos.getX() + 0.5,
                absPos.getY() + 1.1,
                absPos.getZ() + 0.5,
                new ItemStack(Items.AMETHYST_SHARD, 1)
        );
        context.getWorld().spawnEntity(droppedGem);

        // Tick workstation to auto-load gem
        ArtisanAnvilBlockEntity.tick(context.getWorld(), absPos, context.getBlockState(pos), anvilBe);

        context.assertTrue(anvilBe.hasItem(), "Workstation must auto-load in-world dropped Amethyst Shard");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.AMETHYST_SHARD), "Workpiece must be Amethyst Shard");
        context.assertTrue(droppedGem.isRemoved(), "Dropped gem entity must be consumed upon auto-load");

        // Player strikes 2 times with shears to craft diamond
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setStackInHand(Hand.MAIN_HAND, shears);

        context.assertTrue(anvilBe.performStrike(player, shears), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, shears), "Strike 2 must complete craft");

        context.assertFalse(anvilBe.hasItem(), "Workstation must be cleared after craft");
        context.expectItemAt(Items.DIAMOND, pos, 2.0);

        context.complete();
    }

    // =========================================================================
    // TEST 8: CONTINUOUS MASS PRODUCTION AUTO-LOAD LOOP
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testContinuousMassProductionAutoLoadLoop(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        BlockPos absPos = context.getAbsolutePos(pos);

        // Spawn stack of 2 Iron Ingots
        ItemEntity droppedStack = new ItemEntity(
                context.getWorld(),
                absPos.getX() + 0.5,
                absPos.getY() + 1.1,
                absPos.getZ() + 0.5,
                new ItemStack(Items.IRON_INGOT, 2)
        );
        context.getWorld().spawnEntity(droppedStack);

        // Cycle 1: Tick 1 -> loads first ingot, leaves 1 ingot in floating entity
        ArtisanAnvilBlockEntity.tick(context.getWorld(), absPos, context.getBlockState(pos), anvilBe);
        context.assertTrue(anvilBe.hasItem(), "Workstation must auto-load first ingot");
        context.assertEquals(1, droppedStack.getStack().getCount(), "Remaining dropped entity must have 1 ingot");

        // Player performs 3 strikes to complete first sword
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        anvilBe.performStrike(player, pickaxe);
        anvilBe.performStrike(player, pickaxe);
        anvilBe.performStrike(player, pickaxe);

        context.assertFalse(anvilBe.hasItem(), "Workstation is empty after craft 1");

        // Exhaust the 5-tick post-craft cooldown
        for (int i = 0; i < 5; i++) {
            ArtisanAnvilBlockEntity.tick(context.getWorld(), absPos, context.getBlockState(pos), anvilBe);
        }

        // Cycle 2: Tick 6 -> cooldown exhausted, second ingot auto-loads immediately
        ArtisanAnvilBlockEntity.tick(context.getWorld(), absPos, context.getBlockState(pos), anvilBe);
        context.assertTrue(anvilBe.hasItem(), "Workstation must auto-load second ingot in mass-production loop");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.IRON_INGOT), "Second workpiece must be Iron Ingot");
        context.assertTrue(droppedStack.isRemoved(), "Dropped item entity must be fully consumed");

        // Player performs 3 strikes to complete second sword
        anvilBe.performStrike(player, pickaxe);
        anvilBe.performStrike(player, pickaxe);
        anvilBe.performStrike(player, pickaxe);
        context.assertFalse(anvilBe.hasItem(), "Workstation is cleared after craft 2");

        context.complete();
    }
}
