package com.k3.diegetic.client;

import com.k3.diegetic.block.ArtisanAnvilBlock;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * Client-side BlockEntityRenderer for Artisan Anvil.
 * Pure diegetic visualization of the blacksmithing workstation:
 * - Renders blueprint stencil lying flat on the anvil top plate (Y + 1.01).
 * - Renders staged workpiece items sequentially along the longitudinal axis (Y + 1.025).
 */
public class ArtisanAnvilBlockEntityRenderer implements BlockEntityRenderer<ArtisanAnvilBlockEntity> {

    public ArtisanAnvilBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(ArtisanAnvilBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        Direction facing = entity.getCachedState().contains(ArtisanAnvilBlock.FACING)
                ? entity.getCachedState().get(ArtisanAnvilBlock.FACING)
                : Direction.NORTH;

        // 1. Render Blueprint stencil lying flat at Y + 1.01
        ItemStack blueprint = entity.getBlueprint();
        if (blueprint != null && !blueprint.isEmpty()) {
            matrices.push();
            matrices.translate(0.5, 1.01, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
            matrices.scale(0.65f, 0.65f, 0.65f);

            MinecraftClient.getInstance().getItemRenderer().renderItem(
                    blueprint,
                    ModelTransformationMode.FIXED,
                    light,
                    overlay,
                    matrices,
                    vertexConsumers,
                    entity.getWorld(),
                    0
            );
            matrices.pop();
        }

        // 2. Render staged workpiece ingredients sequentially along longitudinal axis at Y + 1.025
        DefaultedList<ItemStack> staged = entity.getStagedIngredients();
        if (staged != null && !staged.isEmpty()) {
            int total = staged.size();
            for (int i = 0; i < total; i++) {
                ItemStack stack = staged.get(i);
                if (stack.isEmpty()) continue;

                matrices.push();
                // Translate to anvil top plate center
                matrices.translate(0.5, 1.025, 0.5);
                // Rotate to match anvil orientation
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));

                // Position workpiece items cleanly on anvil top plate:
                // For 1-4 items: single centered row.
                // For 5-8 items: 2 neat parallel rows side-by-side.
                float xOffset;
                float zOffset;
                float scale;

                if (total <= 4) {
                    xOffset = 0.0f;
                    zOffset = (i - (total - 1) / 2.0f) * 0.20f;
                    scale = 0.38f;
                } else {
                    int itemsPerRow = (total + 1) / 2;
                    int row = i / itemsPerRow;
                    int col = i % itemsPerRow;
                    int countInRow = (row == 0) ? itemsPerRow : (total - itemsPerRow);
                    xOffset = (row == 0) ? -0.11f : 0.11f;
                    zOffset = (col - (countInRow - 1) / 2.0f) * 0.18f;
                    scale = 0.30f;
                }

                matrices.translate(xOffset, 0.0, zOffset);

                // Lay flat on top of the blueprint stencil
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
                matrices.scale(scale, scale, scale);

                MinecraftClient.getInstance().getItemRenderer().renderItem(
                        stack,
                        ModelTransformationMode.FIXED,
                        light,
                        overlay,
                        matrices,
                        vertexConsumers,
                        entity.getWorld(),
                        0
                );
                matrices.pop();
            }
        }
    }
}
