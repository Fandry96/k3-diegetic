package com.k3.diegetic.gametest;

import com.google.gson.JsonElement;
import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import com.k3.diegetic.item.ModItems;
import com.k3.diegetic.recipe.ArtisanCraftingRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.UUID;

/**
 * Headless GameTest suite for Method 2 Blueprint & Template Blacksmithing System.
 * Validates:
 * 1. Dual-Input Sword Blueprint forging lifecycle (2 Iron Ingots + 1 Stick -> 3 strikes -> Iron Sword).
 * 2. Dual-Input Pickaxe Blueprint forging lifecycle (3 Iron Ingots + 2 Sticks -> 3 strikes -> Iron Pickaxe).
 * 3. Partial ingredient strike rejection and tool durability preservation.
 * 4. Unload mistake correction (normal click pops last ingredient; sneak click pops blueprint).
 * 5. Dual-channel serialization roundtrip for ArtisanCraftingRecipe (DFU MapCodec & Netty PacketCodec).
 * 6. Atomic block destruction cleanup with blueprint (zero orphan entities, safe item drop).
 */
public class BlueprintBlacksmithingGameTest implements FabricGameTest {

    // =========================================================================
    // TEST 1: SWORD BLUEPRINT FORGING LIFECYCLE (DUAL-INPUT SCENARIO A)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testSwordBlueprintForgingLifecycle(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");
        context.assertFalse(anvilBe.hasBlueprint(), "Anvil must initially have no blueprint");
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Anvil must initially have no staged ingredients");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step 1: Stage SWORD_BLUEPRINT flat on the anvil top plate
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        boolean bpPlaced = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(bpPlaced, "Placing Sword Blueprint on anvil must succeed");
        context.assertTrue(anvilBe.hasBlueprint(), "Anvil must report active blueprint");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.SWORD_BLUEPRINT), "Active blueprint must be Sword Blueprint");
        context.assertEquals(1, anvilBe.getBlueprint().getCount(), "Blueprint count must be 1");

        // Step 2: Stage sequential ingredients: 2x Iron Ingot + 1x Stick
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        boolean ing1 = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(ing1, "Staging first Iron Ingot must succeed");
        context.assertEquals(1, anvilBe.getStagedIngredients().size(), "Staged ingredients count must be 1");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        boolean ing2 = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(ing2, "Staging second Iron Ingot must succeed");
        context.assertEquals(2, anvilBe.getStagedIngredients().size(), "Staged ingredients count must be 2");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        boolean ing3 = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(ing3, "Staging Stick must succeed");
        context.assertEquals(3, anvilBe.getStagedIngredients().size(), "Staged ingredients count must be 3");

        // Step 3: Perform 3 strikes with FORGING_HAMMER
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        boolean strike1 = anvilBe.performStrike(player, hammer);
        context.assertTrue(strike1, "Strike 1 must succeed");
        context.assertEquals(1, anvilBe.getStrikeCount(), "Strike count must be 1 after strike 1");
        context.assertEquals(3, anvilBe.getStagedIngredients().size(), "Ingredients must remain during progressive strikes");

        boolean strike2 = anvilBe.performStrike(player, hammer);
        context.assertTrue(strike2, "Strike 2 must succeed");
        context.assertEquals(2, anvilBe.getStrikeCount(), "Strike count must be 2 after strike 2");

        boolean strike3 = anvilBe.performStrike(player, hammer);
        context.assertTrue(strike3, "Strike 3 must succeed and complete craft");

        // Step 4: Assert craft completion outcomes
        // a) Finished item spawned into world
        context.expectItemAt(Items.IRON_SWORD, pos, 2.0);

        // b) Workpiece ingredients consumed
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(),
                "Staged ingredients must be completely consumed upon craft completion");

        // c) Blueprint retained on anvil for rapid batching
        context.assertTrue(anvilBe.hasBlueprint(),
                "Sword Blueprint must be retained on anvil after craft completion");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.SWORD_BLUEPRINT),
                "Retained blueprint must be Sword Blueprint");
        context.assertEquals(1, anvilBe.getBlueprint().getCount(),
                "Retained blueprint count must remain 1");

        // d) Strike count and thermal state reset
        context.assertEquals(0, anvilBe.getStrikeCount(),
                "Strike count must reset to 0 after completion");
        context.assertTrue(Math.abs(anvilBe.getThermalState()) < 1e-4f,
                "Thermal state must reset to 0.0f after completion");

        context.complete();
    }

    // =========================================================================
    // TEST 2: PICKAXE BLUEPRINT FORGING LIFECYCLE (DUAL-INPUT SCENARIO B)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testPickaxeBlueprintForgingLifecycle(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step 1: Stage PICKAXE_BLUEPRINT on anvil
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PICKAXE_BLUEPRINT));
        boolean bpPlaced = anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertTrue(bpPlaced, "Placing Pickaxe Blueprint on anvil must succeed");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.PICKAXE_BLUEPRINT), "Active blueprint must be Pickaxe Blueprint");

        // Step 2: Stage sequential ingredients: 3x Iron Ingot + 2x Stick
        for (int i = 0; i < 3; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            boolean inserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
            context.assertTrue(inserted, "Inserting Iron Ingot " + (i + 1) + " must succeed");
        }
        for (int i = 0; i < 2; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            boolean inserted = anvilBe.insertItem(player, Hand.MAIN_HAND);
            context.assertTrue(inserted, "Inserting Stick " + (i + 1) + " must succeed");
        }

        context.assertEquals(5, anvilBe.getStagedIngredients().size(),
                "Pickaxe recipe must have exactly 5 staged ingredients");

        // Step 3: Perform 3 strikes with FORGING_HAMMER
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        boolean s1 = anvilBe.performStrike(player, hammer);
        context.assertTrue(s1, "Pickaxe strike 1 must succeed");
        context.assertEquals(1, anvilBe.getStrikeCount(), "Strike count must advance to 1");

        boolean s2 = anvilBe.performStrike(player, hammer);
        context.assertTrue(s2, "Pickaxe strike 2 must succeed");
        context.assertEquals(2, anvilBe.getStrikeCount(), "Strike count must advance to 2");

        boolean s3 = anvilBe.performStrike(player, hammer);
        context.assertTrue(s3, "Pickaxe strike 3 must succeed and complete craft");

        // Step 4: Assert craft completion outcomes
        context.expectItemAt(Items.IRON_PICKAXE, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(),
                "Pickaxe staged ingredients must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(),
                "Pickaxe Blueprint must be retained on anvil for batching");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.PICKAXE_BLUEPRINT),
                "Retained blueprint must be Pickaxe Blueprint");
        context.assertEquals(0, anvilBe.getStrikeCount(),
                "Strike count must reset to 0 after completion");

        context.complete();
    }

    // =========================================================================
    // TEST 3: PARTIAL INGREDIENT STRIKE REJECTION & DURABILITY PRESERVATION
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testPartialIngredientStrikeRejection(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step 1: Stage SWORD_BLUEPRINT
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        // Step 2: Stage ONLY 1 Iron Ingot (missing 1 Ingot and 1 Stick)
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);
        context.assertEquals(1, anvilBe.getStagedIngredients().size(), "Only 1 ingredient must be staged");

        // Step 3: Attempt strike with FORGING_HAMMER
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);
        context.assertEquals(0, hammer.getDamage(), "Hammer initial damage must be 0");

        boolean strikeResult = anvilBe.performStrike(player, hammer);

        // Assertions:
        // 1. Strike must return false (rejected)
        context.assertFalse(strikeResult, "performStrike must return false when ingredients are incomplete");

        // 2. Strike count remains 0
        context.assertEquals(0, anvilBe.getStrikeCount(),
                "Workstation strike count must not advance on rejected strike");

        // 3. Hammer durability is undamaged
        context.assertEquals(0, hammer.getDamage(),
                "Hammer durability must not take damage when strike is rejected");

        // 4. Staged ingot remains on anvil
        context.assertEquals(1, anvilBe.getStagedIngredients().size(),
                "Staged ingredient must remain on anvil");
        context.assertTrue(anvilBe.getStagedIngredients().get(0).isOf(Items.IRON_INGOT),
                "Staged ingredient must remain Iron Ingot");
        context.assertTrue(anvilBe.hasBlueprint(),
                "Blueprint must remain on anvil");

        context.complete();
    }

    // =========================================================================
    // TEST 4: UNLOAD & MISTAKE CORRECTION RETRIEVAL PROTOCOL
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testUnloadMistakeCorrection(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step 1: Stage SWORD_BLUEPRINT + 2 Iron Ingots
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        context.assertEquals(2, anvilBe.getStagedIngredients().size(), "2 ingots must be staged");

        // Clear player inventory to precisely track returned items
        player.getInventory().clear();
        context.assertTrue(player.getInventory().isEmpty(), "Player inventory must be clear before retrieval");

        // Step 2: Normal retrieval (extractItem / right-click with empty hand)
        // Must pop the LAST staged ingredient (mistake correction)
        boolean extractedLast = anvilBe.extractItem(player);
        context.assertTrue(extractedLast, "extractItem must succeed in popping last ingredient");

        // Assert 1 Iron Ingot returned to player
        context.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)),
                "Player inventory must receive the popped Iron Ingot");
        context.assertEquals(1, player.getInventory().count(Items.IRON_INGOT),
                "Player must have received exactly 1 Iron Ingot");

        // Assert 1 Iron Ingot remains on anvil
        context.assertEquals(1, anvilBe.getStagedIngredients().size(),
                "Exactly 1 Iron Ingot must remain on anvil after extracting last ingredient");
        context.assertTrue(anvilBe.getStagedIngredients().get(0).isOf(Items.IRON_INGOT),
                "Remaining staged item must be Iron Ingot");
        context.assertTrue(anvilBe.hasBlueprint(),
                "Blueprint must remain intact on anvil after popping an ingredient");

        // Step 3: Sneak retrieval (extractBlueprint / sneak right-click with empty hand)
        // Must pop the blueprint (and any remaining staged ingredients safely)
        boolean extractedBp = anvilBe.extractBlueprint(player);
        context.assertTrue(extractedBp, "extractBlueprint must succeed");

        // Assert Sword Blueprint returned to player
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.SWORD_BLUEPRINT)),
                "Player inventory must receive the returned Sword Blueprint");

        // Assert anvil blueprint is cleared
        context.assertFalse(anvilBe.hasBlueprint(),
                "Anvil blueprint must be cleared after extractBlueprint");
        context.assertTrue(anvilBe.getBlueprint().isEmpty(),
                "Anvil getBlueprint() must return ItemStack.EMPTY");
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(),
                "All staged ingredients must be cleared from anvil");

        context.complete();
    }

    // =========================================================================
    // TEST 5: RECIPE DUAL-CHANNEL CODEC ROUNDTRIP (DFU MAPCODEC & NETTY PACKETCODEC)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testRecipeDualChannelCodecRoundtrip(TestContext context) {
        // Recipe A: Sword Forging Recipe
        ArtisanCraftingRecipe swordRecipe = new ArtisanCraftingRecipe(
                Ingredient.ofItems(ModItems.SWORD_BLUEPRINT),
                DefaultedList.copyOf(Ingredient.EMPTY,
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.STICK)
                ),
                Ingredient.ofItems(ModItems.FORGING_HAMMER),
                3,
                new ItemStack(Items.IRON_SWORD)
        );

        // Recipe B: Pickaxe Forging Recipe
        ArtisanCraftingRecipe pickaxeRecipe = new ArtisanCraftingRecipe(
                Ingredient.ofItems(ModItems.PICKAXE_BLUEPRINT),
                DefaultedList.copyOf(Ingredient.EMPTY,
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.STICK),
                        Ingredient.ofItems(Items.STICK)
                ),
                Ingredient.ofItems(ModItems.FORGING_HAMMER),
                3,
                new ItemStack(Items.IRON_PICKAXE)
        );

        // Recipe C: Chestplate Forging Recipe (8 Iron Ingots)
        ArtisanCraftingRecipe chestplateRecipe = new ArtisanCraftingRecipe(
                Ingredient.ofItems(ModItems.CHESTPLATE_BLUEPRINT),
                DefaultedList.copyOf(Ingredient.EMPTY,
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT),
                        Ingredient.ofItems(Items.IRON_INGOT)
                ),
                Ingredient.ofItems(ModItems.FORGING_HAMMER),
                3,
                new ItemStack(Items.IRON_CHESTPLATE)
        );

        List<ArtisanCraftingRecipe> testRecipes = List.of(swordRecipe, pickaxeRecipe, chestplateRecipe);

        // Channel 1: DFU MapCodec via JsonOps.INSTANCE
        Codec<ArtisanCraftingRecipe> mapCodecAsCodec = ArtisanCraftingRecipe.Serializer.CODEC.codec();
        for (ArtisanCraftingRecipe original : testRecipes) {
            DataResult<JsonElement> jsonEncode = mapCodecAsCodec.encodeStart(JsonOps.INSTANCE, original);
            JsonElement jsonElement = jsonEncode.getOrThrow(msg ->
                    new AssertionError("DFU MapCodec failed to encode recipe: " + msg));

            DataResult<ArtisanCraftingRecipe> jsonDecode = mapCodecAsCodec.parse(JsonOps.INSTANCE, jsonElement);
            ArtisanCraftingRecipe dfuDecoded = jsonDecode.getOrThrow(msg ->
                    new AssertionError("DFU MapCodec failed to parse recipe from JSON: " + msg));

            assertRecipeEquals(context, original, dfuDecoded, "DFU JsonOps");
        }

        // Channel 2: Netty PacketCodec via RegistryByteBuf
        for (ArtisanCraftingRecipe original : testRecipes) {
            RegistryByteBuf registryBuf = new RegistryByteBuf(PacketByteBufs.create(), context.getWorld().getRegistryManager());
            ArtisanCraftingRecipe.Serializer.PACKET_CODEC.encode(registryBuf, original);

            context.assertTrue(registryBuf.readableBytes() > 0,
                    "Netty encoded buffer must contain readable bytes");

            ArtisanCraftingRecipe nettyDecoded = ArtisanCraftingRecipe.Serializer.PACKET_CODEC.decode(registryBuf);

            assertRecipeEquals(context, original, nettyDecoded, "Netty PacketCodec");
            context.assertEquals(0, registryBuf.readableBytes(),
                    "Netty buffer must have zero unread trailing bytes");
        }

        context.complete();
    }

    // =========================================================================
    // TEST 6: ATOMIC BLOCK CLEANUP WITH BLUEPRINT (ZERO ENTITY LEAKS & SAFE DROP)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testAtomicBreakCleanupWithBlueprint(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        // Step 1: Stage SWORD_BLUEPRINT + 2 Iron Ingots + 1 Stick
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SWORD_BLUEPRINT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        anvilBe.insertItem(player, Hand.MAIN_HAND);

        UUID interactionUuid = anvilBe.getInteractionEntityUuid();
        context.assertTrue(interactionUuid != null, "Interaction entity UUID must exist prior to block destruction");

        var interactionEntity = context.getWorld().getEntity(interactionUuid);
        context.assertTrue(interactionEntity != null && !interactionEntity.isRemoved(),
                "Interaction entity must be active in world prior to block destruction");

        // Step 2: Destroy the block mid-staging
        context.removeBlock(pos);

        // Step 3: Assert tracked entity discarded
        var checkInteraction = context.getWorld().getEntity(interactionUuid);
        context.assertTrue(checkInteraction == null || checkInteraction.isRemoved(),
                "Interaction entity must be removed from world");

        // Step 4: Assert 0 orphan entities in 3.0 radius
        var orphanInteractions = context.getEntitiesAround(EntityType.INTERACTION, pos, 3.0);
        context.assertTrue(orphanInteractions.isEmpty(),
                "Zero orphan InteractionEntities must remain in world");

        var orphanDisplays = context.getEntitiesAround(EntityType.ITEM_DISPLAY, pos, 3.0);
        context.assertTrue(orphanDisplays.isEmpty(),
                "Zero orphan ItemDisplayEntities must remain in world");

        // Step 5: Assert all items dropped safely into world
        List<ItemEntity> droppedEntities = context.getEntitiesAround(EntityType.ITEM, pos, 3.0);
        context.assertFalse(droppedEntities.isEmpty(), "Dropped item entities must be present in world");

        int blueprintCount = 0;
        int ingotCount = 0;
        int stickCount = 0;

        for (ItemEntity itemEntity : droppedEntities) {
            ItemStack stack = itemEntity.getStack();
            if (stack.isOf(ModItems.SWORD_BLUEPRINT)) {
                blueprintCount += stack.getCount();
            } else if (stack.isOf(Items.IRON_INGOT)) {
                ingotCount += stack.getCount();
            } else if (stack.isOf(Items.STICK)) {
                stickCount += stack.getCount();
            }
        }

        context.assertEquals(1, blueprintCount, "Exactly 1 Sword Blueprint must be dropped safely");
        context.assertEquals(2, ingotCount, "Exactly 2 Iron Ingots must be dropped safely");
        context.assertEquals(1, stickCount, "Exactly 1 Stick must be dropped safely");

        context.complete();
    }

    // =========================================================================
    // TEST 7: SHOVEL BLUEPRINT FORGING LIFECYCLE (1 Iron Ingot + 2 Sticks)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testShovelBlueprintForgingLifecycle(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SHOVEL_BLUEPRINT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting Shovel Blueprint must succeed");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging Iron Ingot must succeed");

        for (int i = 0; i < 2; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging Stick " + (i + 1) + " must succeed");
        }

        context.assertEquals(3, anvilBe.getStagedIngredients().size(), "Shovel must have 3 staged ingredients");

        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        context.expectItemAt(Items.IRON_SHOVEL, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Staged ingredients must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Shovel Blueprint must be retained");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.SHOVEL_BLUEPRINT), "Retained blueprint must be Shovel Blueprint");

        context.complete();
    }

    // =========================================================================
    // TEST 8: HOE BLUEPRINT FORGING LIFECYCLE (2 Iron Ingots + 2 Sticks)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testHoeBlueprintForgingLifecycle(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.HOE_BLUEPRINT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting Hoe Blueprint must succeed");

        for (int i = 0; i < 2; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging Ingot " + (i + 1) + " must succeed");
        }
        for (int i = 0; i < 2; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging Stick " + (i + 1) + " must succeed");
        }

        context.assertEquals(4, anvilBe.getStagedIngredients().size(), "Hoe must have 4 staged ingredients");

        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        context.expectItemAt(Items.IRON_HOE, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Staged ingredients must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Hoe Blueprint must be retained");
        context.assertTrue(anvilBe.getBlueprint().isOf(ModItems.HOE_BLUEPRINT), "Retained blueprint must be Hoe Blueprint");

        context.complete();
    }

    // =========================================================================
    // TEST 9: ARMOR BLUEPRINTS (HELMET, BOOTS, LEGGINGS, CHESTPLATE)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testHelmetBlueprintForgingLifecycle(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.HELMET_BLUEPRINT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting Helmet Blueprint must succeed");

        for (int i = 0; i < 5; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging Ingot " + (i + 1) + " for Helmet");
        }
        context.assertEquals(5, anvilBe.getStagedIngredients().size(), "Helmet must have 5 staged ingots");

        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        context.expectItemAt(Items.IRON_HELMET, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Staged ingots must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Helmet Blueprint must be retained");

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testBootsBlueprintForgingLifecycle(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.BOOTS_BLUEPRINT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting Boots Blueprint must succeed");

        for (int i = 0; i < 4; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging Ingot " + (i + 1) + " for Boots");
        }
        context.assertEquals(4, anvilBe.getStagedIngredients().size(), "Boots must have 4 staged ingots");

        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        context.expectItemAt(Items.IRON_BOOTS, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Staged ingots must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Boots Blueprint must be retained");

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testLeggingsBlueprintForgingLifecycle(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.LEGGINGS_BLUEPRINT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting Leggings Blueprint must succeed");

        for (int i = 0; i < 7; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging Ingot " + (i + 1) + " for Leggings");
        }
        context.assertEquals(7, anvilBe.getStagedIngredients().size(), "Leggings must have 7 staged ingots");

        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        context.expectItemAt(Items.IRON_LEGGINGS, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Staged ingots must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Leggings Blueprint must be retained");

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testChestplateBlueprintForgingLifecycle(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.CHESTPLATE_BLUEPRINT));
        context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Inserting Chestplate Blueprint must succeed");

        for (int i = 0; i < 8; i++) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
            context.assertTrue(anvilBe.insertItem(player, Hand.MAIN_HAND), "Staging Ingot " + (i + 1) + " for Chestplate");
        }
        context.assertEquals(8, anvilBe.getStagedIngredients().size(), "Chestplate must have 8 staged ingots");

        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        context.expectItemAt(Items.IRON_CHESTPLATE, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Staged ingots must be consumed");
        context.assertTrue(anvilBe.hasBlueprint(), "Chestplate Blueprint must be retained");

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testHopperChestplateFullStagingAndForging(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertEquals(9, anvilBe.size(), "Workstation must have 9 slots");

        // 1. Insert Chestplate Blueprint via slot 0
        ItemStack chestplateBp = new ItemStack(ModItems.CHESTPLATE_BLUEPRINT);
        context.assertTrue(anvilBe.canInsert(0, chestplateBp, Direction.UP), "Hopper must be allowed to insert Chestplate Blueprint into slot 0");
        anvilBe.setStack(0, chestplateBp);

        // 2. Feed 8 Iron Ingots sequentially into slots 1 to 8
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        for (int slot = 1; slot <= 8; slot++) {
            context.assertTrue(anvilBe.canInsert(slot, ironIngot, Direction.UP), "Hopper must be allowed to insert ingot into slot " + slot);
            anvilBe.setStack(slot, ironIngot);
            context.assertEquals(slot, anvilBe.getStagedIngredients().size(), "Staged ingredients count must match slot " + slot);
        }

        // 3. Overflow rejection: extra insertion into full anvil must be rejected
        context.assertFalse(anvilBe.canInsert(1, ironIngot, Direction.UP), "Hopper must reject extra insertion when anvil is full");

        // 4. Forge with hammer
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        context.expectItemAt(Items.IRON_CHESTPLATE, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Ingredients must be consumed after craft");
        context.assertTrue(anvilBe.hasBlueprint(), "Chestplate Blueprint must remain on anvil");

        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testHopperMultiIngredientFilterSequenceAndForging(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ARTISAN_ANVIL.getDefaultState());

        ArtisanAnvilBlockEntity anvilBe = context.getBlockEntity(pos);
        context.assertTrue(anvilBe != null, "ArtisanAnvilBlockEntity must be present");

        ItemStack shovelBp = new ItemStack(ModItems.SHOVEL_BLUEPRINT);
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack stick = new ItemStack(Items.STICK);

        // 1. Stage Shovel Blueprint
        context.assertTrue(anvilBe.canInsert(0, shovelBp, Direction.UP), "Hopper must be allowed to insert Shovel Blueprint into slot 0");
        anvilBe.setStack(0, shovelBp);

        // 2. Slot 1 filter check: stick must be rejected, ingot must be accepted
        context.assertFalse(anvilBe.canInsert(1, stick, Direction.UP), "Slot 1 must reject Stick for Shovel recipe (requires Ingot)");
        context.assertTrue(anvilBe.canInsert(1, ironIngot, Direction.UP), "Slot 1 must accept Iron Ingot");
        context.assertTrue(anvilBe.isValid(1, ironIngot), "isValid(1, Iron Ingot) must return true");
        context.assertFalse(anvilBe.isValid(1, stick), "isValid(1, Stick) must return false");

        anvilBe.setStack(1, ironIngot);
        context.assertEquals(1, anvilBe.getStagedIngredients().size(), "Staged count must be 1 after slot 1");

        // 3. Slot 2 filter check: ingot must be rejected, stick must be accepted
        context.assertFalse(anvilBe.canInsert(2, ironIngot, Direction.UP), "Slot 2 must reject Iron Ingot for Shovel recipe (requires Stick)");
        context.assertTrue(anvilBe.canInsert(2, stick, Direction.UP), "Slot 2 must accept Stick");

        // Test stack count clamping in setStack
        ItemStack oversizedStick = new ItemStack(Items.STICK, 16);
        anvilBe.setStack(2, oversizedStick);
        context.assertEquals(1, anvilBe.getStagedIngredients().get(1).getCount(), "setStack must clamp item count to 1 (maxCountPerStack)");

        // 4. Slot 3 filter check: stick must be accepted
        context.assertTrue(anvilBe.canInsert(3, stick, Direction.UP), "Slot 3 must accept second Stick");
        anvilBe.setStack(3, stick);
        context.assertEquals(3, anvilBe.getStagedIngredients().size(), "Staged count must be 3 after slot 3");

        // 5. Overflow rejection: Slot 4 must reject any additional insertion
        context.assertFalse(anvilBe.canInsert(4, stick, Direction.UP), "Slot 4 must reject extra stick for 3-ingredient Shovel recipe");
        context.assertFalse(anvilBe.canInsert(4, ironIngot, Direction.UP), "Slot 4 must reject extra ingot for 3-ingredient Shovel recipe");

        // 6. Forge with hammer
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack hammer = new ItemStack(ModItems.FORGING_HAMMER);
        player.setStackInHand(Hand.MAIN_HAND, hammer);

        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 1 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 2 must succeed");
        context.assertTrue(anvilBe.performStrike(player, hammer), "Strike 3 must complete craft");

        context.expectItemAt(Items.IRON_SHOVEL, pos, 2.0);
        context.assertTrue(anvilBe.getStagedIngredients().isEmpty(), "Ingredients must be consumed after craft");
        context.assertTrue(anvilBe.hasBlueprint(), "Shovel Blueprint must remain on anvil for next batch");

        context.complete();
    }

    // =========================================================================
    // ASSERTION HELPERS
    // =========================================================================

    private void assertRecipeEquals(TestContext context, ArtisanCraftingRecipe expected, ArtisanCraftingRecipe actual, String channel) {
        context.assertTrue(expected != null && actual != null, channel + ": Recipes must not be null");

        // Compare Blueprint ingredient
        context.assertEquals(expected.blueprint().getMatchingStacks().length, actual.blueprint().getMatchingStacks().length,
                channel + ": Blueprint matching stacks length mismatch");
        if (expected.blueprint().getMatchingStacks().length > 0) {
            context.assertTrue(expected.blueprint().getMatchingStacks()[0].isOf(actual.blueprint().getMatchingStacks()[0].getItem()),
                    channel + ": Blueprint item mismatch");
        }

        // Compare Ingredients list
        context.assertEquals(expected.ingredients().size(), actual.ingredients().size(),
                channel + ": Ingredients size mismatch");
        for (int i = 0; i < expected.ingredients().size(); i++) {
            Ingredient expIng = expected.ingredients().get(i);
            Ingredient actIng = actual.ingredients().get(i);
            context.assertEquals(expIng.getMatchingStacks().length, actIng.getMatchingStacks().length,
                    channel + ": Ingredient " + i + " matching stacks length mismatch");
            if (expIng.getMatchingStacks().length > 0) {
                context.assertTrue(expIng.getMatchingStacks()[0].isOf(actIng.getMatchingStacks()[0].getItem()),
                        channel + ": Ingredient " + i + " item mismatch");
            }
        }

        // Compare Tool ingredient
        if (expected.tool().getMatchingStacks().length > 0 && actual.tool().getMatchingStacks().length > 0) {
            context.assertTrue(expected.tool().getMatchingStacks()[0].isOf(actual.tool().getMatchingStacks()[0].getItem()),
                    channel + ": Tool item mismatch");
        }

        // Compare Required strikes
        context.assertEquals(expected.requiredStrikes(), actual.requiredStrikes(),
                channel + ": Required strikes mismatch");

        // Compare Result item stack
        context.assertTrue(ItemStack.areEqual(expected.result(), actual.result()),
                channel + ": Result ItemStack mismatch (expected " + expected.result() + ", got " + actual.result() + ")");
    }
}
