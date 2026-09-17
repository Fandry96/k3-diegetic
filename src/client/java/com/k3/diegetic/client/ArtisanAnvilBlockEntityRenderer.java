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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * Client-side BlockEntityRenderer for Artisan Anvil.
 * Directly renders the active workpiece flat on the top face of the anvil.
 * Guarantees visual rendering independent of entity tracking.
 */
public class ArtisanAnvilBlockEntityRenderer implements BlockEntityRenderer<ArtisanAnvilBlockEntity> {

    public ArtisanAnvilBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(ArtisanAnvilBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ItemStack heldStack = entity.getHeldStack();
        if (heldStack == null || heldStack.isEmpty()) {
            return;
        }

        matrices.push();
        // Translate to the center of the top anvil face
        matrices.translate(0.5, 1.02, 0.5);

        Direction facing = entity.getCachedState().contains(ArtisanAnvilBlock.FACING)
                ? entity.getCachedState().get(ArtisanAnvilBlock.FACING)
                : Direction.NORTH;

        // Lay workpiece flat on the anvil face oriented with the anvil
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        matrices.scale(0.625f, 0.625f, 0.625f);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                heldStack,
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
