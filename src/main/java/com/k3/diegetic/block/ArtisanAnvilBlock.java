package com.k3.diegetic.block;

import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Physical diegetic crafting station block.
 * Features:
 * - Horizontal facing (perpendicular to player placement).
 * - Accurate multi-axis VoxelShape for collision and raycast outline.
 * - 1.21.1 onUseWithItem / onUse interaction pipelines (ingredient insertion, tool strike, item retrieval).
 * - Left-click strike support via onBlockBreakStart.
 * - Atomic cleanup on block break via onStateReplaced (despawns display/interaction entities and drops workpiece).
 * - ZERO 2D GUI screen handlers or menus.
 */
public class ArtisanAnvilBlock extends BlockWithEntity {

    public static final MapCodec<ArtisanAnvilBlock> CODEC = createCodec(ArtisanAnvilBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    // VoxelShape parts
    private static final VoxelShape BASE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);
    private static final VoxelShape LOWER_PILLAR = Block.createCuboidShape(4.0, 4.0, 4.0, 12.0, 5.0, 12.0);
    private static final VoxelShape PILLAR = Block.createCuboidShape(6.0, 5.0, 6.0, 10.0, 10.0, 10.0);

    // North-South top face (Z-axis extended)
    private static final VoxelShape TOP_NORTH_SOUTH = Block.createCuboidShape(3.0, 10.0, 0.0, 13.0, 16.0, 16.0);
    private static final VoxelShape SHAPE_NORTH_SOUTH = VoxelShapes.union(BASE, LOWER_PILLAR, PILLAR, TOP_NORTH_SOUTH);

    // East-West top face (X-axis extended)
    private static final VoxelShape TOP_EAST_WEST = Block.createCuboidShape(0.0, 10.0, 3.0, 16.0, 16.0, 13.0);
    private static final VoxelShape SHAPE_EAST_WEST = VoxelShapes.union(BASE, LOWER_PILLAR, PILLAR, TOP_EAST_WEST);

    public ArtisanAnvilBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Vanilla anvil places perpendicular to player facing
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().rotateYClockwise());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction dir = state.get(FACING);
        return dir.getAxis() == Direction.Axis.X ? SHAPE_EAST_WEST : SHAPE_NORTH_SOUTH;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return this.getOutlineShape(state, world, pos, context);
    }

    /**
     * CRITICAL: BlockWithEntity defaults to BlockRenderType.INVISIBLE.
     * Must return MODEL so the block model renders in-game.
     */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ArtisanAnvilBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlocks.ARTISAN_ANVIL_BLOCK_ENTITY, ArtisanAnvilBlockEntity::tick);
    }

    /* =========================================================================
     * 1.21.1 DIEGETIC INTERACTION PIPELINE (ZERO 2D GUI)
     * ========================================================================= */

    /**
     * Primary right-click interaction when player holds an item.
     * Routes ingredient insertion or tool strikes.
     */
    @Override
    public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                          PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ArtisanAnvilBlockEntity anvilBe)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 1. If anvil has no item, try to insert player's held item as the workpiece
        if (!anvilBe.hasItem()) {
            if (!stack.isEmpty()) {
                if (!world.isClient()) {
                    anvilBe.insertItem(player, hand);
                }
                return ItemActionResult.success(world.isClient());
            }
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 2. Anvil has an item: check if player is holding a crafting tool to strike
        if (anvilBe.isValidStrikeTool(stack)) {
            if (!world.isClient()) {
                anvilBe.performStrike(player, stack);
            }
            return ItemActionResult.success(world.isClient());
        }

        // Fall through to onUse (e.g. for empty hand retrieval or off-hand use)
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Fallback right-click interaction (invoked when onUseWithItem passes or hand is empty).
     * Handles workpiece retrieval.
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ArtisanAnvilBlockEntity anvilBe && anvilBe.hasItem()) {
            if (!world.isClient()) {
                anvilBe.extractItem(player);
            }
            return ActionResult.success(world.isClient());
        }
        return ActionResult.PASS;
    }

    /**
     * Left-click attack interaction routing.
     * Allows striking the anvil with a held tool via left-click.
     */
    @Override
    public void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ArtisanAnvilBlockEntity anvilBe && anvilBe.hasItem()) {
                ItemStack mainHand = player.getMainHandStack();
                if (anvilBe.isValidStrikeTool(mainHand)) {
                    anvilBe.performStrike(player, mainHand);
                }
            }
        }
        super.onBlockBreakStart(state, world, pos, player);
    }

    /* =========================================================================
     * ATOMIC WORLD CLEANUP (ZERO ENTITY OR ITEM LEAKS)
     * ========================================================================= */

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ArtisanAnvilBlockEntity anvilBe) {
                anvilBe.cleanupOnBreak();
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
