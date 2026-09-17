package com.k3.diegetic.gametest;

import com.k3.diegetic.block.ArtisanAnvilBlock;
import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import com.k3.diegetic.component.ModDataComponentTypes;
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
 * Empirical Stress Harness validating:
 * 1. Dual-Input Acceptance Constraint (Scenario A: Iron Ingot + Pickaxe -> 3 strikes -> Iron Sword).
 * 2. Dual-Input Acceptance Constraint (Scenario B: Amethyst Shard + Shears -> 2 strikes -> Diamond).
 * 3. Exact per-strike tool durability degradation for damageable tools in Survival mode.
 * 4. Durability damage exemption when player is in Creative mode.
 * 5. Negative matching and durability preservation: shears on ingot, pickaxe on amethyst, sticks on anvil.
 * 6. Workstation block-level interaction pipeline: onUseWithItem, onUse extraction, and onBlockBreakStart.
 */
public class ArtisanWorkstationStressTest implements FabricGameTest {

    // =========================================================================
    // 1. SCENARIO A: INGOT SMITHING STRESS & DURABILITY VERIFICATION
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testScenarioAStressAndDurability(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());
        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // 1. Insert Iron Ingot
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1));
        boolean inserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(inserted, "Inserting iron ingot must succeed");

        // 2. Prepare Pickaxe with 0 initial damage
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);
        context.assertEquals(0, pickaxe.getDamage(), "Pickaxe initial damage must be 0");

        // 3. Strike 1
        boolean strike1 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike1, "Strike 1 with pickaxe must succeed");
        context.assertEquals(1, pickaxe.getDamage(), "Pickaxe durability damage must be exactly 1 after strike 1");
        context.assertEquals(1, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Strike count must be 1 after strike 1");

        // 4. Strike 2
        boolean strike2 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike2, "Strike 2 with pickaxe must succeed");
        context.assertEquals(2, pickaxe.getDamage(), "Pickaxe durability damage must be exactly 2 after strike 2");
        context.assertEquals(2, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Strike count must be 2 after strike 2");

        // 5. Strike 3 (Craft completion)
        boolean strike3 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike3, "Strike 3 with pickaxe must complete the craft");
        context.assertEquals(3, pickaxe.getDamage(), "Pickaxe durability damage must be exactly 3 after completion");
        context.assertFalse(anvilBe.hasItem(), "Workstation workpiece must be cleared after completion");

        // Assert crafted Iron Sword was spawned
        context.expectItemAt(Items.IRON_SWORD, pos, 2.0);
        context.complete();
    }

    // =========================================================================
    // 2. SCENARIO B: GEM CUTTING STRESS & DURABILITY VERIFICATION
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testScenarioBStressAndDurability(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());
        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // 1. Insert Amethyst Shard
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 1));
        boolean inserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(inserted, "Inserting amethyst shard must succeed");

        // 2. Prepare Shears with 0 initial damage
        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setStackInHand(Hand.MAIN_HAND, shears);
        context.assertEquals(0, shears.getDamage(), "Shears initial damage must be 0");

        // 3. Strike 1 with Shears
        boolean strike1 = anvilBe.performStrike(player, shears);
        context.assertTrue(strike1, "Strike 1 with shears must succeed");
        context.assertEquals(1, shears.getDamage(), "Shears durability damage must be exactly 1 after strike 1");
        context.assertEquals(1, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Strike count must be 1 after strike 1");

        // 4. Strike 2 with Shears (Completion)
        boolean strike2 = anvilBe.performStrike(player, shears);
        context.assertTrue(strike2, "Strike 2 with shears must complete the craft");
        context.assertEquals(2, shears.getDamage(), "Shears durability damage must be exactly 2 after completion");
        context.assertFalse(anvilBe.hasItem(), "Workstation workpiece must be cleared after completion");

        // Assert Diamond spawned
        context.expectItemAt(Items.DIAMOND, pos, 2.0);
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

        // --- Test 3A: Shears on Iron Ingot ---
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setStackInHand(Hand.MAIN_HAND, shears);
        boolean invalidStrike1 = anvilBe.performStrike(player, shears);

        context.assertFalse(invalidStrike1, "Shears on Iron Ingot must fail cleanly");
        context.assertEquals(0, shears.getDamage(), "Shears must NOT receive durability damage on rejected strike");
        context.assertEquals(0, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Workstation strike count must NOT advance on rejected strike");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.IRON_INGOT), "Workpiece must remain Iron Ingot");

        // Reset workstation
        anvilBe.clear();
        context.assertFalse(anvilBe.hasItem(), "Workstation must be clear");

        // --- Test 3B: Pickaxe on Amethyst Shard ---
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 1));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);
        boolean invalidStrike2 = anvilBe.performStrike(player, pickaxe);

        context.assertFalse(invalidStrike2, "Pickaxe on Amethyst Shard must fail cleanly");
        context.assertEquals(0, pickaxe.getDamage(), "Pickaxe must NOT receive durability damage on rejected strike");
        context.assertEquals(0, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Workstation strike count must NOT advance on rejected strike");
        context.assertTrue(anvilBe.getHeldStack().isOf(Items.AMETHYST_SHARD), "Workpiece must remain Amethyst Shard");

        // --- Test 3C: Stick on Amethyst Shard ---
        ItemStack stick = new ItemStack(Items.STICK);
        player.setStackInHand(Hand.MAIN_HAND, stick);
        boolean invalidStick = anvilBe.performStrike(player, stick);
        context.assertFalse(invalidStick, "Stick on Amethyst Shard must fail cleanly");

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

        creativePlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1));
        anvilBe.insertItem(creativePlayer, Hand.MAIN_HAND);

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        creativePlayer.setStackInHand(Hand.MAIN_HAND, pickaxe);

        boolean strike = anvilBe.performStrike(creativePlayer, pickaxe);
        context.assertTrue(strike, "Creative strike must succeed");
        context.assertEquals(0, pickaxe.getDamage(), "Tool must NOT suffer durability damage in Creative mode");

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

        // Step 1: Right-click with Iron Ingot -> onUseWithItem insertion
        ItemStack ingot = new ItemStack(Items.IRON_INGOT, 1);
        player.setStackInHand(Hand.MAIN_HAND, ingot);
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(absolutePos.getX() + 0.5, absolutePos.getY() + 1.0, absolutePos.getZ() + 0.5),
                Direction.UP,
                absolutePos,
                false
        );

        ItemActionResult insertResult = block.onUseWithItem(
                ingot,
                context.getBlockState(pos),
                context.getWorld(),
                absolutePos,
                player,
                Hand.MAIN_HAND,
                hit
        );
        context.assertTrue(insertResult.isAccepted(), "onUseWithItem must accept ingot insertion");
        context.assertTrue(anvilBe.hasItem(), "Workstation must now have item");

        // Step 2: Left-click with Pickaxe -> onBlockBreakStart tool strike
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);

        block.onBlockBreakStart(context.getBlockState(pos), context.getWorld(), absolutePos, player);
        context.assertEquals(1, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "onBlockBreakStart must advance strike count to 1");
        context.assertEquals(1, pickaxe.getDamage(), "onBlockBreakStart must inflict tool durability damage");

        // Step 3: Right-click with Pickaxe -> onUseWithItem tool strike 2
        ItemActionResult strike2Result = block.onUseWithItem(
                pickaxe,
                context.getBlockState(pos),
                context.getWorld(),
                absolutePos,
                player,
                Hand.MAIN_HAND,
                hit
        );
        context.assertTrue(strike2Result.isAccepted(), "onUseWithItem must accept pickaxe strike");
        context.assertEquals(2, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Strike count must advance to 2");

        // Step 4: Right-click with Pickaxe -> onUseWithItem tool strike 3 (Completion)
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
        context.assertFalse(anvilBe.hasItem(), "Workstation must be cleared after craft completion");
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

        // 1. Insert Iron Ingot into workstation
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        UUID displayUuid = anvilBe.getDisplayEntityUuid();
        UUID interactionUuid = anvilBe.getInteractionEntityUuid();
        context.assertTrue(displayUuid != null, "Display entity UUID must exist");
        context.assertTrue(interactionUuid != null, "Interaction entity UUID must exist");

        // 2. Perform 2 out of 3 required strikes (Partial Progress: 66%)
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);
        boolean strike1 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike1, "Strike 1 must succeed");
        boolean strike2 = anvilBe.performStrike(player, pickaxe);
        context.assertTrue(strike2, "Strike 2 must succeed");

        context.assertEquals(2, anvilBe.getHeldStack().get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Workstation must hold 2 strikes of partial progress");
        context.assertEquals(2, pickaxe.getDamage(), "Pickaxe must have 2 damage");

        // 3. Destroy the block mid-craft at partial progress
        context.removeBlock(pos);

        // 4. Assert tracked entities are discarded
        var checkDisplay = context.getWorld().getEntity(displayUuid);
        context.assertTrue(checkDisplay == null || checkDisplay.isRemoved(),
                "Display entity must be discarded after block destruction");

        var checkInteraction = context.getWorld().getEntity(interactionUuid);
        context.assertTrue(checkInteraction == null || checkInteraction.isRemoved(),
                "Interaction entity must be discarded after block destruction");

        // 5. Assert zero orphan entities remain in the surrounding area
        var orphanDisplays = context.getEntitiesAround(EntityType.ITEM_DISPLAY, pos, 3.0);
        context.assertTrue(orphanDisplays.isEmpty(), "Zero orphan ItemDisplayEntities must remain in world");

        var orphanInteractions = context.getEntitiesAround(EntityType.INTERACTION, pos, 3.0);
        context.assertTrue(orphanInteractions.isEmpty(), "Zero orphan InteractionEntities must remain in world");

        // 6. Assert dropped workpiece retains partial progress component
        List<ItemEntity> droppedItems = context.getEntitiesAround(EntityType.ITEM, pos, 2.0);
        context.assertFalse(droppedItems.isEmpty(), "Dropped item entity must be present in world");
        ItemEntity dropped = droppedItems.get(0);
        ItemStack droppedStack = dropped.getStack();
        context.assertTrue(droppedStack.isOf(Items.IRON_INGOT), "Dropped stack must be Iron Ingot");
        context.assertTrue(droppedStack.contains(ModDataComponentTypes.WORKSTATION_STATE),
                "Dropped stack must retain WorkstationStateComponent");
        context.assertEquals(2, droppedStack.get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Dropped stack must retain 2 strikes of partial progress");

        context.complete();
    }
}
