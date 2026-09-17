package com.k3.diegetic.gametest;

import com.k3.diegetic.block.ArtisanAnvilBlock;
import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import com.k3.diegetic.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.UUID;

/**
 * Empirical Stress Harness validating Method 2 Blueprint Blacksmithing:
 * 1. Dual-Input Acceptance Constraint (Scenario A: Sword Blueprint + 2 Ingots + 1 Stick -> 3 strikes -> Iron Sword).
 * 2. Dual-Input Acceptance Constraint (Scenario B: Pickaxe Blueprint + 3 Ingots + 2 Sticks -> 3 strikes -> Iron Pickaxe).
 * 3. Exact per-strike tool durability degradation for damageable tools in Survival mode.
 * 4. Durability damage exemption when player is in Creative mode.
 * 5. Negative matching and durability preservation: empty anvil, partial staging, invalid tools.
 * 6. Workstation block-level interaction pipeline: onUseWithItem, onUse extraction, and onBlockBreakStart.
 * 7. Partial-progress block destruction cleanup (safe item preservation, zero orphan entities).
 */
public class ArtisanWorkstationStressTest implements FabricGameTest {

    // =========================================================================
    // 1. SCENARIO A: SWORD BLUEPRINT STRESS & DURABILITY VERIFICATION
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testScenarioAStressAndDurability(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());
        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // 1. Stage Sword Blueprint
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        boolean bpInserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(bpInserted, "Inserting Sword Blueprint must succeed");

        // 2. Stage ingredients (2 Iron Ingots + 1 Stick)
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        // 3. Prepare Pickaxe with 0 initial damage
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);
        context.assertEquals(0, pickaxe.getDamage(), "Pickaxe initial damage must be 0");

        // 4. Strike 1
        boolean strike1 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike1, "Strike 1 with pickaxe must succeed");
        context.assertEquals(1, pickaxe.getDamage(), "Pickaxe durability damage must be exactly 1 after strike 1");
        context.assertEquals(1, anvilBe.getStrikeCount(), "Strike count must be 1 after strike 1");

        // 5. Strike 2
        boolean strike2 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike2, "Strike 2 with pickaxe must succeed");
        context.assertEquals(2, pickaxe.getDamage(), "Pickaxe durability damage must be exactly 2 after strike 2");
        context.assertEquals(2, anvilBe.getStrikeCount(), "Strike count must be 2 after strike 2");

        // 6. Strike 3 (Craft completion)
        boolean strike3 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike3, "Strike 3 with pickaxe must complete the craft");
        context.assertEquals(3, pickaxe.getDamage(), "Pickaxe durability damage must be exactly 3 after completion");
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Staged ingredients must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Blueprint must be retained for batching");

        // Assert crafted Iron Sword was spawned
        context.expectItemAt(Items.IRON_SWORD, pos, 2.0);
        context.complete();
    }

    // =========================================================================
    // 2. SCENARIO B: PICKAXE BLUEPRINT STRESS & DURABILITY VERIFICATION
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testScenarioBStressAndDurability(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());
        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // 1. Stage Pickaxe Blueprint
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PICKAXE_BLUEPRINT));
        boolean bpInserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(bpInserted, "Inserting Pickaxe Blueprint must succeed");

        // 2. Stage ingredients (3 Iron Ingots + 2 Sticks)
        for (int i = 0; i < 3; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            anvilBe.insertItem(player, Hand.MAIN_HAND);
        }
        for (int i = 0; i < 2; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            anvilBe.insertItem(player, Hand.MAIN_HAND);
        }

        // 3. Prepare Forging Hammer with 0 initial damage
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);
        context.assertEquals(0, hammer.getDamage(), "Hammer initial damage must be 0");

        // 4. Strike 1 with Hammer
        boolean strike1 = anvilBe.performStrike(player, hammer);
        context.assertTrue(strike1, "Strike 1 with hammer must succeed");
        context.assertEquals(1, hammer.getDamage(), "Hammer durability damage must be exactly 1 after strike 1");
        context.assertEquals(1, anvilBe.getStrikeCount(), "Strike count must be 1 after strike 1");

        // 5. Strike 2 with Hammer
        boolean strike2 = anvilBe.performStrike(player, hammer);
        context.assertTrue(strike2, "Strike 2 with hammer must succeed");
        context.assertEquals(2, hammer.getDamage(), "Hammer durability damage must be exactly 2 after strike 2");
        context.assertEquals(2, anvilBe.getStrikeCount(), "Strike count must be 2 after strike 2");

        // 6. Strike 3 with Hammer (Completion)
        boolean strike3 = anvilBe.performStrike(player, hammer);
        context.assertTrue(strike3, "Strike 3 with hammer must complete the craft");
        context.assertEquals(3, hammer.getDamage(), "Hammer durability damage must be exactly 3 after completion");
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Pickaxe ingredients must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Pickaxe blueprint must remain on anvil");

        // Assert Iron Pickaxe spawned
        context.expectItemAt(Items.IRON_PICKAXE, pos, 2.0);
        context.complete();
    }

    // =========================================================================
    // 3. NEGATIVE MATCHES & DURABILITY PRESERVATION
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testNegativeMatchesAndDurabilityPreservation(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());
        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // --- Test 3A: Striking empty anvil with Hammer ---
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);
        boolean emptyStrike = anvilBe.performStrike(player, hammer);

        context.assertFalse(emptyStrike, "Hammer on empty anvil must fail cleanly");
        context.assertEquals(0, hammer.getDamage(), "Hammer must NOT receive durability damage on empty anvil strike");
        context.assertEquals(0, anvilBe.getStrikeCount(), "Workstation strike count must NOT advance on rejected strike");

        // --- Test 3B: Striking incomplete staging (Blueprint + 1 Ingot only) ---
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        boolean incompleteStrike = anvilBe.performStrike(player, hammer);
        context.assertFalse(incompleteStrike, "Hammer on incomplete staging must fail cleanly");
        context.assertEquals(0, hammer.getDamage(), "Hammer must NOT receive durability damage on incomplete staging");
        context.assertEquals(0, anvilBe.getStrikeCount(), "Workstation strike count must NOT advance");
        context.assertEquals(1, anvilBe.getStagedIngredients().size(), "Staged ingot must remain");

        // --- Test 3C: Invalid tool (Stick) on anvil ---
        ItemStack stick = new ItemStack(Items.STICK);
        player.setStackInHand(Hand.MAIN_HAND, stick);
        boolean invalidStick = anvilBe.performStrike(player, stick);
        context.assertFalse(invalidStick, "Stick on anvil must fail cleanly");

        context.complete();
    }

    // =========================================================================
    // 4. CREATIVE MODE DURABILITY BYPASS
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testCreativePlayerDurabilityExemption(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());
        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity creativePlayer = context.createMockPlayer(GameMode.CREATIVE);

        // Stage Sword Blueprint + full ingredients
        creativePlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(creativePlayer, Hand.MAIN_HAND);
        creativePlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(creativePlayer, Hand.MAIN_HAND);
        creativePlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(creativePlayer, Hand.MAIN_HAND);
        creativePlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        anvilBe.insertItem(creativePlayer, Hand.MAIN_HAND);

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        creativePlayer.setStackInHand(Hand.MAIN_HAND, pickaxe);

        boolean strike = anvilBe.performStrike(creativePlayer, pickaxe);
        context.assertTrue(strike, "Creative strike must succeed");
        context.assertEquals(0, pickaxe.getDamage(), "Tool must NOT suffer durability damage in Creative mode");
        context.assertEquals(1, anvilBe.getStrikeCount(), "Strike count must advance even in Creative mode");

        context.complete();
    }

    // =========================================================================
    // 5. BLOCK-LEVEL INTERACTION PIPELINE (onUseWithItem & onBlockBreakStart)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testBlockLevelInteractionPipeline(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());
        ArtisanAnvilBlock block = (ArtisanAnvilBlock) context.getBlockState(pos).getBlock();
        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        BlockPos absolutePos = context.getAbsolutePos(pos);
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(absolutePos.getX() + 0.5, absolutePos.getY() + 1.0, absolutePos.getZ() + 0.5),
                Direction.UP,
                absolutePos,
                false
        );

        // Step 1: Right-click with Sword Blueprint -> onUseWithItem insertion
        ItemStack blueprint = new ItemStack(ModItems.SWORD_BLUEPRINT);
        player.setStackInHand(Hand.MAIN_HAND, blueprint);
        ItemActionResult bpResult = block.onUseWithItem(
                blueprint,
                context.getBlockState(pos),
                context.getWorld(),
                absolutePos,
                player,
                Hand.MAIN_HAND,
                hit
        );
        context.assertTrue(bpResult.isAccepted(), "onUseWithItem must accept blueprint insertion");
        context.assertTrue(anvilBe.hasBlueprint(), "Workstation must now have blueprint");

        // Step 2: Right-click with 2 Ingots and 1 Stick -> onUseWithItem
        ItemStack ingot1 = new ItemStack(Items.IRON_INGOT);
        player.setStackInHand(Hand.MAIN_HAND, ingot1);
        block.onUseWithItem(ingot1, context.getBlockState(pos), context.getWorld(), absolutePos, player, Hand.MAIN_HAND, hit);

        ItemStack ingot2 = new ItemStack(Items.IRON_INGOT);
        player.setStackInHand(Hand.MAIN_HAND, ingot2);
        block.onUseWithItem(ingot2, context.getBlockState(pos), context.getWorld(), absolutePos, player, Hand.MAIN_HAND, hit);

        ItemStack stick = new ItemStack(Items.STICK);
        player.setStackInHand(Hand.MAIN_HAND, stick);
        block.onUseWithItem(stick, context.getBlockState(pos), context.getWorld(), absolutePos, player, Hand.MAIN_HAND, hit);

        context.assertEquals(3, anvilBe.getStagedIngredients().size(), "3 ingredients must be staged");

        // Step 3: Left-click with Pickaxe -> onBlockBreakStart tool strike
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);

        block.onBlockBreakStart(context.getBlockState(pos), context.getWorld(), absolutePos, player);
        context.assertEquals(1, anvilBe.getStrikeCount(), "onBlockBreakStart must advance strike count to 1");
        context.assertEquals(1, pickaxe.getDamage(), "onBlockBreakStart must inflict tool durability damage");

        // Step 4: Right-click with Pickaxe -> onUseWithItem tool strike 2
        ItemActionResult strike2Result = block.onUseWithItem(
                pickaxe,
                context.getBlockState(pos),
                context.getWorld(),
                absolutePos,
                player,
                Hand.MAIN_HAND,
                hit
        );
        context.assertTrue(strike2Result.isAccepted(), "onUseWithItem must accept pickaxe strike 2");
        context.assertEquals(2, anvilBe.getStrikeCount(), "Strike count must advance to 2");

        // Step 5: Right-click with Pickaxe -> onUseWithItem tool strike 3 (Completion)
        ItemActionResult strike3Result = block.onUseWithItem(
                pickaxe,
                context.getBlockState(pos),
                context.getWorld(),
                absolutePos,
                player,
                Hand.MAIN_HAND,
                hit
        );
        context.assertTrue(strike3Result.isAccepted(), "onUseWithItem strike 3 must complete craft");
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Workstation ingredients must be cleared");
        context.expectItemAt(Items.IRON_SWORD, pos, 2.0);

        context.complete();
    }

    // =========================================================================
    // 6. PARTIAL-PROGRESS BLOCK DESTRUCTION CLEANUP
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testPartialProgressBlockDestructionCleanup(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());
        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // 1. Stage Sword Blueprint + 2 Ingots + 1 Stick
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        UUID interactionUuid = anvilBe.getInteractionEntityUuid();
        context.assertTrue(interactionUuid != null, "Interaction entity UUID must exist");

        // 2. Perform 2 out of 3 required strikes (Partial Progress: 66%)
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);
        boolean strike1 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike1, "Strike 1 must succeed");
        boolean strike2 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike2, "Strike 2 must succeed");

        context.assertEquals(2, anvilBe.getStrikeCount(), "Workstation must hold 2 strikes of partial progress");
        context.assertEquals(2, pickaxe.getDamage(), "Pickaxe must have 2 damage");

        // 3. Destroy the block mid-craft at partial progress
        context.removeBlock(pos);

        // 4. Assert tracked entity is discarded
        var checkInteraction = context.getWorld().getEntity(interactionUuid);
        context.assertTrue(checkInteraction == null || checkInteraction.isRemoved(),
                "Interaction entity must be discarded after block destruction");

        // 5. Assert zero orphan entities remain in the surrounding area
        var orphanDisplays = context.getEntitiesAround(EntityType.ITEM_DISPLAY, pos, 3.0);
        context.assertTrue(orphanDisplays.isEmpty(), "Zero orphan ItemDisplayEntities must remain in world");

        var orphanInteractions = context.getEntitiesAround(EntityType.INTERACTION, pos, 3.0);
        context.assertTrue(orphanInteractions.isEmpty(), "Zero orphan InteractionEntities must remain in world");

        // 6. Assert dropped items exist in world
        List<ItemEntity> droppedItems = context.getEntitiesAround(EntityType.ITEM, pos, 3.0);
        context.assertFalse(droppedItems.isEmpty(), "Dropped item entities must be present in world");

        int blueprintCount = 0;
        int ingotCount = 0;
        int stickCount = 0;

        for (ItemEntity dropped : droppedItems) {
            ItemStack stack = dropped.getStack();
            if (stack.isOf(ModItems.SWORD_BLUEPRINT)) {
                blueprintCount += stack.getCount();
            } else if (stack.isOf(Items.IRON_INGOT)) {
                ingotCount += stack.getCount();
            } else if (stack.isOf(Items.STICK)) {
                stickCount += stack.getCount();
            }
        }

        context.assertEquals(1, blueprintCount, "Exactly 1 Sword Blueprint must be dropped");
        context.assertEquals(2, ingotCount, "Exactly 2 Iron Ingots must be dropped");
        context.assertEquals(1, stickCount, "Exactly 1 Stick must be dropped");

        context.complete();
    }
}
