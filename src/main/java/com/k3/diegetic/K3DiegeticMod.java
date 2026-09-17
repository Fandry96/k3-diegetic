package com.k3.diegetic;

import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import com.k3.diegetic.component.ModDataComponentTypes;
import com.k3.diegetic.recipe.ModRecipes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for K3 Diegetic Workstation.
 * Initializes Data Components, Blocks, Block Entities, and custom recipe serializers.
 */
public class K3DiegeticMod implements ModInitializer {
    public static final String MOD_ID = "k3_diegetic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing K3 Diegetic Workstation (modid: {})", MOD_ID);
        
        // Milestone 3: Data Component Types
        ModDataComponentTypes.registerDataComponentTypes();
        
        // Milestone 4: ModBlocks, ModItems & ModRecipes
        ModBlocks.registerModBlocks();
        com.k3.diegetic.item.ModItems.registerModItems();
        ModRecipes.registerModRecipes();

        // Interaction Raycast Support (Milestone 5 / Fix):
        // Detect player clicks targeting InteractionEntity hitbox and route to active anvil BlockEntity
        registerInteractionCallbacks();
        
        LOGGER.info("K3 Diegetic Workstation common initialization complete.");
    }

    private void registerInteractionCallbacks() {
        // Right-click interaction routing (tool strike or item extraction)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ArtisanAnvilBlockEntity anvilBe = findAnvilForInteraction(world, entity);
            if (anvilBe == null || !anvilBe.hasItem()) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);
            if (anvilBe.isValidStrikeTool(stack)) {
                if (!world.isClient()) {
                    anvilBe.performStrike(player, stack);
                }
                return ActionResult.SUCCESS;
            } else {
                if (!world.isClient()) {
                    anvilBe.extractItem(player);
                }
                return ActionResult.SUCCESS;
            }
        });

        // Left-click attack routing (tool strike)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ArtisanAnvilBlockEntity anvilBe = findAnvilForInteraction(world, entity);
            if (anvilBe == null || !anvilBe.hasItem()) {
                return ActionResult.PASS;
            }

            ItemStack mainHand = player.getMainHandStack();
            if (anvilBe.isValidStrikeTool(mainHand)) {
                if (!world.isClient()) {
                    anvilBe.performStrike(player, mainHand);
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    private static ArtisanAnvilBlockEntity findAnvilForInteraction(World world, Entity entity) {
        if (!(entity instanceof InteractionEntity interaction)) {
            return null;
        }
        BlockPos pos = interaction.getBlockPos();
        if (world.getBlockEntity(pos) instanceof ArtisanAnvilBlockEntity be
                && interaction.getUuid().equals(be.getInteractionEntityUuid())) {
            return be;
        }
        if (world.getBlockEntity(pos.down()) instanceof ArtisanAnvilBlockEntity be
                && interaction.getUuid().equals(be.getInteractionEntityUuid())) {
            return be;
        }
        return null;
    }

    /**
     * Helper factory for creating namespaced Identifiers in Minecraft 1.21+.
     * Uses the modern Identifier.of factory method.
     */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
