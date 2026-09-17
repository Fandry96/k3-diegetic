package com.k3.diegetic.gametest;

import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import com.k3.diegetic.item.ModItems;
import com.k3.diegetic.recipe.ArtisanCraftingRecipe;
import com.k3.diegetic.recipe.ArtisanRecipeInput;
import com.k3.diegetic.recipe.ModRecipes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
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
 * Headless GameTest suite verifying Method 2 Blueprint & Template Blacksmithing System:
 * 1. Recipe Registry & Dual-Input Recipe Matching (Sword, Pickaxe, Axe).
 * 2. In-World Workstation Lifecycle (Placement, blueprint staging, ingredient feeding, strike progression, craft completion).
 * 3. Atomic Cleanup on Block Break (zero orphan entities, clean item drops).
 * 4. Hopper Sided Inventory 5-slot architecture and filter validation.
 * 5. Continuous Batch Production Loop (batching with retained blueprint).
 * 6. Sequential Staging Order Enforcement.
 * 7. Sneak Retrieval & Complete Emptying Protocol.
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

        List<RecipeEntry<ArtisanCraftingRecipe>> recipes = context.getWorld().getRecipeManager()
                .listAllOfType(ModRecipes.ARTISAN_CRAFTING_TYPE);

        context.assertTrue(!recipes.isEmpty(), "Artisan crafting recipe list must not be empty");

        ItemStack ironPickaxe = new ItemStack(Items.IRON_PICKAXE);
        ItemStack shears = new ItemStack(Items.SHEARS);

        // 2. Scenario A: Sword Blueprint Recipe (Sword Blueprint + 2 Iron Ingots + 1 Stick -> 3 strikes -> Iron Sword)
        ArtisanRecipeInput swordInput = new ArtisanRecipeInput(
                new ItemStack(ModItems.SWORD_BLUEPRINT),
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STICK))
        );

        Optional<RecipeEntry<ArtisanCraftingRecipe>> swordOpt = recipes.stream()
                .filter(entry -> entry.value().matches(swordInput, context.getWorld()) && entry.value().matchesTool(ironPickaxe))
                .findFirst();

        context.assertTrue(swordOpt.isPresent(), "Scenario A: Sword Blueprint recipe must match");
        ArtisanCraftingRecipe swordRecipe = swordOpt.get().value();
        context.assertEquals(3, swordRecipe.requiredStrikes(), "Sword recipe must require 3 strikes");
        context.assertTrue(swordRecipe.getResult(context.getWorld().getRegistryManager()).isOf(Items.IRON_SWORD),
                "Sword recipe result must be an Iron Sword");

        // 3. Scenario B: Pickaxe Blueprint Recipe (Pickaxe Blueprint + 3 Iron Ingots + 2 Sticks -> 3 strikes -> Iron Pickaxe)
        ArtisanRecipeInput pickaxeInput = new ArtisanRecipeInput(
                new ItemStack(ModItems.PICKAXE_BLUEPRINT),
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
                        new ItemStack(Items.STICK), new ItemStack(Items.STICK))
        );

        Optional<RecipeEntry<ArtisanCraftingRecipe>> pickaxeOpt = recipes.stream()
                .filter(entry -> entry.value().matches(pickaxeInput, context.getWorld()) && entry.value().matchesTool(ironPickaxe))
                .findFirst();

        context.assertTrue(pickaxeOpt.isPresent(), "Scenario B: Pickaxe Blueprint recipe must match");
        ArtisanCraftingRecipe pickaxeRecipe = pickaxeOpt.get().value();
        context.assertEquals(3, pickaxeRecipe.requiredStrikes(), "Pickaxe recipe must require 3 strikes");
        context.assertTrue(pickaxeRecipe.getResult(context.getWorld().getRegistryManager()).isOf(Items.IRON_PICKAXE),
                "Pickaxe recipe result must be an Iron Pickaxe");

        // 4. Scenario C: Axe Blueprint Recipe (Axe Blueprint + 3 Iron Ingots + 2 Sticks -> 3 strikes -> Iron Axe)
        ArtisanRecipeInput axeInput = new ArtisanRecipeInput(
                new ItemStack(ModItems.AXE_BLUEPRINT),
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
                        new ItemStack(Items.STICK), new ItemStack(Items.STICK))
        );

        Optional<RecipeEntry<ArtisanCraftingRecipe>> axeOpt = recipes.stream()
                .filter(entry -> entry.value().matches(axeInput, context.getWorld()) && entry.value().matchesTool(ironPickaxe))
                .findFirst();

        context.assertTrue(axeOpt.isPresent(), "Scenario C: Axe Blueprint recipe must match");
        ArtisanCraftingRecipe axeRecipe = axeOpt.get().value();
        context.assertEquals(3, axeRecipe.requiredStrikes(), "Axe recipe must require 3 strikes");
        context.assertTrue(axeRecipe.getResult(context.getWorld().getRegistryManager()).isOf(Items.IRON_AXE),
                "Axe recipe result must be an Iron Axe");

        // 5. Cross-Scenario Mismatch Validation (Anti-Overfitting Negative Assertions)
        context.assertFalse(swordRecipe.matches(pickaxeInput, context.getWorld()), "Sword recipe must reject Pickaxe input");
        context.assertFalse(pickaxeRecipe.matches(swordInput, context.getWorld()), "Pickaxe recipe must reject Sword input");
        context.assertFalse(axeRecipe.matches(swordInput, context.getWorld()), "Axe recipe must reject Sword input");
        context.assertFalse(swordRecipe.matchesTool(shears), "Sword recipe must reject Shears tool");

        context.complete();
    }

    // =========================================================================
    // TEST 2: IN-WORLD WORKSTATION LIFECYCLE (SCENARIO A: SWORD FORGING)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldWorkstationLifecycleSmithing(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");
        context.assertFalse(anvilBe.hasItem(), "Initial workstation state must be empty");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step A: Insert Sword Blueprint
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        boolean bpInserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(bpInserted, "Inserting Sword Blueprint must succeed");
        context.assertTrue(anvilBe.hasBlueprint(), "Workstation must hold blueprint");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.SWORD_BLUEPRINT), "Held blueprint must be Sword Blueprint");
        context.assertTrue(anvilBe.getInteractionEntityUuid() != null, "Interaction entity UUID must be set on insertion");

        // Step B: Stage 2 Iron Ingots + 1 Stick
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting first Iron Ingot must succeed");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting second Iron Ingot must succeed");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting Stick must succeed");

        context.assertEquals(3, anvilBe.getStagedIngredients().size(), "3 ingredients must be staged");

        // Step C: Tool Strikes 1, 2, 3 with Pickaxe
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);

        boolean strike1 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike1, "Strike 1 must succeed with pickaxe");
        context.assertEquals(1, anvilBe.getStrikeCount(), "Strike count must advance to 1");

        boolean strike2 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike2, "Strike 2 must succeed with pickaxe");
        context.assertEquals(2, anvilBe.getStrikeCount(), "Strike count must advance to 2");

        boolean strike3 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike3, "Strike 3 must complete the craft");

        // Assert crafted output was spawned into world
        context.expectItemAt(Items.IRON_SWORD, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Staged ingredients must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Blueprint must remain on anvil");
        context.assertEquals(0, anvilBe.getStrikeCount(), "Strike count must reset to 0");

        context.complete();
    }

    // =========================================================================
    // TEST 3: IN-WORLD WORKSTATION LIFECYCLE (SCENARIO B: PICKAXE FORGING)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldWorkstationLifecycleCutting(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step A: Insert Pickaxe Blueprint
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PICKAXE_BLUEPRINT));
        boolean bpInserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(bpInserted, "Inserting Pickaxe Blueprint must succeed");

        // Step B: Negative Tool Strike Validation before staging all ingredients
        ItemStack stick = new ItemStack(Items.STICK);
        player.setStackInHand(Hand.MAIN_HAND, stick);
        boolean invalidStrike = anvilBe.performStrike(player, stick);
        context.assertFalse(invalidStrike, "Striking with stick must be rejected");
        context.assertEquals(0, anvilBe.getStrikeCount(), "Strike count must not advance on invalid tool");

        // Step C: Stage 3 Iron Ingots + 2 Sticks
        for (int i = 0; i < 3; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging ingot " + (i + 1));
        }
        for (int i = 0; i < 2; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging stick " + (i + 1));
        }

        // Step D: Perform 3 strikes with Forging Hammer
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertEquals(1, anvilBe.getStrikeCount(), "Strike count must be 1");

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertEquals(2, anvilBe.getStrikeCount(), "Strike count must be 2");

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        // Assert Iron Pickaxe was spawned
        context.expectItemAt(Items.IRON_PICKAXE, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Ingredients must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Blueprint must remain on anvil");

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

        // 1. Stage Sword Blueprint + ingredients
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        UUID interactionUuid = anvilBe.getInteractionEntityUuid();
        context.assertTrue(interactionUuid != null, "Interaction entity UUID must exist");

        var interactionEntity = context.getWorld().getEntity(interactionUuid);
        context.assertTrue(interactionEntity != null && !interactionEntity.isRemoved(),
                "Interaction entity must be active in world before block break");

        // 2. Break the workstation block mid-craft
        context.removeBlock(pos);

        // 3. Verify tracked entity is discarded
        var checkInteraction = context.getWorld().getEntity(interactionUuid);
        context.assertTrue(checkInteraction == null || checkInteraction.isRemoved(),
                "Interaction entity must be discarded after block destruction");

        // 4. Assert zero orphan Display or Interaction entities remain in the surrounding area
        var orphanDisplays = context.getEntitiesAround(EntityType.ITEM_DISPLAY, pos, 3.0);
        context.assertTrue(orphanDisplays.isEmpty(), "Zero orphan ItemDisplayEntities must remain in world");

        var orphanInteractions = context.getEntitiesAround(EntityType.INTERACTION, pos, 3.0);
        context.assertTrue(orphanInteractions.isEmpty(), "Zero orphan InteractionEntities must remain in world");

        // 5. Assert the active items were safely dropped into world
        context.expectItemAt(ModItems.SWORD_BLUEPRINT, pos, 2.0);
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
        context.assertEquals(5, anvilBe.size(), "Workstation inventory size must be 5 (1 blueprint + 4 ingredients)");
        context.assertEquals(1, anvilBe.getMaxCountPerStack(), "Max stack count per slot must be 1");

        // 1. Slot 0 Blueprint Insertion
        ItemStack swordBlueprint = new ItemStack(ModItems.SWORD_BLUEPRINT);
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);

        context.assertTrue(anvilBe.canInsert(0, swordBlueprint, Direction.UP), "Hopper must be allowed to insert Blueprint into slot 0 from top");
        context.assertTrue(anvilBe.canInsert(0, swordBlueprint, Direction.NORTH), "Hopper must be allowed to insert Blueprint into slot 0 from side");
        context.assertFalse(anvilBe.canInsert(0, swordBlueprint, Direction.DOWN), "Hopper must reject insertion from bottom");
        context.assertFalse(anvilBe.canInsert(0, ironIngot, Direction.UP), "Hopper must reject non-blueprint item into slot 0");

        // Insert Blueprint into Slot 0
        anvilBe.setStack(0, swordBlueprint);
        context.assertTrue(anvilBe.hasBlueprint(), "Workstation must hold blueprint after slot 0 insertion");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.SWORD_BLUEPRINT), "Held blueprint must be Sword Blueprint");

        // Slot 0 full rejection
        context.assertFalse(anvilBe.canInsert(0, swordBlueprint, Direction.UP),
                "Hopper must reject blueprint insertion when slot 0 is already occupied");

        // 2. Slot 1 Ingredient Insertion (allowed when blueprint is present)
        context.assertTrue(anvilBe.canInsert(1, ironIngot, Direction.UP),
                "Hopper must accept Iron Ingot into slot 1 when blueprint is present");
        anvilBe.setStack(1, ironIngot);
        context.assertEquals(1, anvilBe.getStagedIngredients().size(), "1 ingredient must be staged");

        // 3. Extraction protection: items cannot be extracted from bottom
        context.assertFalse(anvilBe.canExtract(0, anvilBe.getStack(0), Direction.DOWN),
                "Blueprint must be protected against extraction from below");
        context.assertEquals(0, anvilBe.getAvailableSlots(Direction.DOWN).length,
                "Bottom face must expose zero slots for automated pulling");

        context.complete();
    }

    // =========================================================================
    // TEST 6: CONTINUOUS BATCH PRODUCTION LOOP (RETAINED BLUEPRINT BATCHING)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testContinuousMassProductionAutoLoadLoop(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        // --- BATCH 1: Place Blueprint and forge first Iron Sword ---
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        player.setStackInHand(Hand.MAIN_HAND, hammer);
        anvilBe.performStrike(player, hammer);
        anvilBe.performStrike(player, hammer);
        anvilBe.performStrike(player, hammer);

        context.assertTrue(anvilBe.hasBlueprint(), "Blueprint must be retained after batch 1");
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Ingredients must be consumed after batch 1");

        // --- BATCH 2: Immediate second craft using the RETAINED blueprint without re-placing ---
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        boolean batch2Ing1 = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(batch2Ing1, "Batch 2: Staging first ingot with retained blueprint must succeed");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        context.assertEquals(3, anvilBe.getStagedIngredients().size(), "Batch 2: 3 ingredients staged");

        player.setStackInHand(Hand.MAIN_HAND, hammer);
        anvilBe.performStrike(player, hammer);
        anvilBe.performStrike(player, hammer);
        anvilBe.performStrike(player, hammer);

        context.assertTrue(anvilBe.hasBlueprint(), "Blueprint must remain retained after batch 2");
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Ingredients must be consumed after batch 2");

        // Assert 2 Iron Swords produced in the vicinity
        List<ItemEntity> swordEntities = context.getEntitiesAround(EntityType.ITEM, pos, 3.0);
        int totalSwords = swordEntities.stream()
                .filter(e -> e.getStack().isOf(Items.IRON_SWORD))
                .mapToInt(e -> e.getStack().getCount())
                .sum();
        context.assertEquals(2, totalSwords, "Exactly 2 Iron Swords must be produced across the 2 continuous batches");

        context.complete();
    }

    // =========================================================================
    // TEST 7: SEQUENTIAL STAGING ORDER ENFORCEMENT
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldItemEntityAutoLoadingSmithing(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Place Sword Blueprint
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        // Negative: Attempt to insert Stick first (Sword recipe requires Iron Ingot first)
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        boolean invalidOrder1 = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertFalse(invalidOrder1, "Must reject Stick when Iron Ingot is expected first");
        context.assertEquals(0, anvilBe.getStagedIngredients().size(), "Staged ingredients must remain 0");

        // Positive: Insert first Iron Ingot
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        boolean validIngot1 = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(validIngot1, "First Iron Ingot must succeed");

        // Negative: Attempt to insert Stick second (Sword recipe requires second Iron Ingot)
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        boolean invalidOrder2 = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertFalse(invalidOrder2, "Must reject Stick when second Iron Ingot is expected");
        context.assertEquals(1, anvilBe.getStagedIngredients().size(), "Staged ingredients must remain 1");

        // Positive: Insert second Iron Ingot and then Stick
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertEquals(3, anvilBe.getStagedIngredients().size(), "All 3 ingredients staged in order");

        // Negative: Overflow rejection when recipe is fully loaded
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        boolean overflow = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertFalse(overflow, "Must reject extra items when staging is already full");

        context.complete();
    }

    // =========================================================================
    // TEST 8: SNEAK RETRIEVAL POPPING BLUEPRINT AND STAGED WORKPIECES
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldItemEntityAutoLoadingCutting(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Stage Sword Blueprint + 1 Iron Ingot
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        context.assertTrue(anvilBe.hasBlueprint(), "Anvil must have blueprint");
        context.assertEquals(1, anvilBe.getStagedIngredients().size(), "Anvil must have 1 staged ingot");

        // Clear inventory to verify returned items
        player.getInventory().clear();

        // Sneak right-click retrieval
        boolean extracted = anvilBe.extractBlueprint(player);
        context.assertTrue(extracted, "Sneak extraction of blueprint must succeed");

        context.assertFalse(anvilBe.hasBlueprint(), "Anvil blueprint must be cleared");
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Anvil staged ingredients must be cleared");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.SWORD_BLUEPRINT)),
                "Player inventory must receive Sword Blueprint");
        context.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)),
                "Player inventory must receive staged Iron Ingot");

        context.complete();
    }

    // =========================================================================
    // TEST 9: IN-WORLD WORKSTATION LIFECYCLE (AXE BLUEPRINT FORGING)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testInWorldWorkstationLifecycleCopperAndGold(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step 1: Stage Axe Blueprint
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.AXE_BLUEPRINT));
        boolean bpPlaced = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(bpPlaced, "Inserting Axe Blueprint must succeed");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.AXE_BLUEPRINT), "Held blueprint must be Axe Blueprint");

        // Negative check: shears rejected on Axe blueprint
        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setStackInHand(Hand.MAIN_HAND, shears);
        boolean invalidStrike = anvilBe.performStrike(player, shears);
        context.assertFalse(invalidStrike, "Shears strike on Axe blueprint must be rejected");

        // Step 2: Stage 3 Iron Ingots + 2 Sticks
        for (int i = 0; i < 3; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            anvilBe.insertItem(player, Hand.MAIN_HAND);
        }
        for (int i = 0; i < 2; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            anvilBe.insertItem(player, Hand.MAIN_HAND);
        }

        context.assertEquals(5, anvilBe.getStagedIngredients().size(), "5 ingredients must be staged for Axe");

        // Step 3: Perform 3 strikes with Forging Hammer
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Axe strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Axe strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Axe strike 3 must complete craft");

        context.expectItemAt(Items.IRON_AXE, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Axe staged ingredients must be cleared");
        context.assertTrue(anvilBe.hasBlueprint(), "Axe blueprint must remain on anvil");

        context.complete();
    }
}
