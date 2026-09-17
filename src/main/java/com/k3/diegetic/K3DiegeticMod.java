package com.k3.diegetic;

import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import com.k3.diegetic.component.ModDataComponentTypes;
import com.k3.diegetic.item.ModItems;
import com.k3.diegetic.recipe.ModRecipes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Common entrypoint for K3 Diegetic Workstation.
 * Initializes Data Components, Blocks, Block Entities, custom recipe serializers,
 * entity interaction routing, and first-join Blacksmith's Manual distribution.
 */
public class K3DiegeticMod implements ModInitializer {
    public static final String MOD_ID = "k3_diegetic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String FIRST_JOIN_TAG = "spark_and_strike_joined";

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing K3 Diegetic Workstation (modid: {})", MOD_ID);
        
        // Data Component Types
        ModDataComponentTypes.registerDataComponentTypes();
        
        // ModBlocks, ModItems & ModRecipes
        ModBlocks.registerModBlocks();
        ModItems.registerModItems();
        ModRecipes.registerModRecipes();

        // Interaction Raycast Support
        registerInteractionCallbacks();

        // First-join Blacksmith's Manual delivery
        registerFirstJoinGuideDelivery();
        
        LOGGER.info("K3 Diegetic Workstation common initialization complete.");
    }

    private void registerFirstJoinGuideDelivery() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            if (!player.getCommandTags().contains(FIRST_JOIN_TAG)) {
                player.addCommandTag(FIRST_JOIN_TAG);
                ItemStack manual = createBlacksmithManual();
                player.getInventory().offerOrDrop(manual);
                LOGGER.info("Delivered Blacksmith's Manual to {} on first join", player.getName().getString());
            }
        });
    }

    /**
     * Constructs the 5-page illustrated Blacksmith's Manual using modern Data Components.
     * Guaranteed zero legacy NBT calls.
     */
    public static ItemStack createBlacksmithManual() {
        ItemStack manual = new ItemStack(Items.WRITTEN_BOOK);
        RawFilteredPair<String> title = RawFilteredPair.of("Blacksmith's Manual");
        String author = "Master Artisan";
        int generation = 0;

        List<RawFilteredPair<Text>> pages = List.of(
            // Page 1: Overview & Anvil/Hammer
            RawFilteredPair.of(Text.literal(
                "§6§lSpark & Strike§r\n" +
                "§8Blacksmith's Manual§r\n\n" +
                "§0Welcome to physical in-world smithing.\n\n" +
                "Forget 2D menus. Craft an §1Artisan Anvil§0 and §1Forging Hammer§0 to shape metal directly in the world."
            )),
            // Page 2: Crafting Blueprints
            RawFilteredPair.of(Text.literal(
                "§6§lCrafting Blueprints§r\n\n" +
                "§0Before shaping steel, draft a Blueprint at a Crafting Table:\n\n" +
                "• §1Sword Blueprint§0 (Paper, Ingot, Stick)\n" +
                "• §1Pickaxe Blueprint§0 (Paper, 3 Ingots, 2 Sticks)\n" +
                "• §1Axe Blueprint§0 (Paper, 3 Ingots, 2 Sticks)"
            )),
            // Page 3: Feeding Materials
            RawFilteredPair.of(Text.literal(
                "§6§lFeeding Materials§r\n\n" +
                "§01. Right-click the anvil with a Blueprint to lay it flat.\n\n" +
                "2. Feed workpiece items in sequence by right-clicking.\n\n" +
                "Watch your §2action bar§0 for real-time loading feedback."
            )),
            // Page 4: Striking & Heat
            RawFilteredPair.of(Text.literal(
                "§6§lStriking & Heat§r\n\n" +
                "§0Once all materials are staged, strike with your §1Forging Hammer§0 (or pickaxe).\n\n" +
                "Each strike shapes the metal. When finished, your forged item drops and the Blueprint remains for batching!"
            )),
            // Page 5: Mistake Correction
            RawFilteredPair.of(Text.literal(
                "§6§lMistake Correction§r\n\n" +
                "§0Inserted the wrong material?\n\n" +
                "• §1Empty hand right-click§0: pops the last staged item back.\n\n" +
                "• §1Sneak + right-click§0: pops the Blueprint and all items back."
            ))
        );

        WrittenBookContentComponent content = new WrittenBookContentComponent(title, author, generation, pages, true);
        manual.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
        return manual;
    }

    private void registerInteractionCallbacks() {
        // Right-click interaction routing (tool strike, placement, or extraction)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ArtisanAnvilBlockEntity anvilBe = findAnvilForInteraction(world, entity);
            if (anvilBe == null) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);
            if (anvilBe.isValidStrikeTool(stack)) {
                if (!world.isClient()) {
                    anvilBe.performStrike(player, stack);
                }
                return ActionResult.SUCCESS;
            } else if (!stack.isEmpty()) {
                if (!world.isClient()) {
                    anvilBe.insertItem(player, hand);
                }
                return ActionResult.SUCCESS;
            } else {
                if (!world.isClient()) {
                    if (player.isSneaking()) {
                        anvilBe.extractBlueprint(player);
                    } else {
                        anvilBe.extractItem(player);
                    }
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
