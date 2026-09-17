package com.k3.diegetic.gametest;

import com.google.gson.JsonElement;
import com.k3.diegetic.component.ModDataComponentTypes;
import com.k3.diegetic.component.WorkstationStateComponent;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Headless GameTest suite validating dual-channel serialization round-tripping
 * (DFU Codec & Netty PacketCodec) for WorkstationStateComponent across dual-input
 * non-adjacent state vectors and ItemStack integration.
 */
public class WorkstationStateComponentTest implements FabricGameTest {

    // =========================================================================
    // DUAL-INPUT ACCEPTANCE VECTORS (Anti-Overfitting Constraint §2)
    // =========================================================================

    /** State A: Ingot smithing recipe, 5 strikes completed, 0.85f thermal, active */
    public static final WorkstationStateComponent STATE_A_INGOT_SMITHING = new WorkstationStateComponent(
            Identifier.of("k3_diegetic", "smithing/tempered_blade"),
            50,
            5,
            0.85f,
            true
    );

    /** State B: Gem cutting recipe, 2 strikes completed, 0.15f thermal, inactive */
    public static final WorkstationStateComponent STATE_B_GEM_CUTTING = new WorkstationStateComponent(
            Identifier.of("k3_diegetic", "cutting/polished_prism"),
            20,
            2,
            0.15f,
            false
    );

    /** State C: Edge/Boundary - Zero initial workstation state */
    public static final WorkstationStateComponent STATE_C_INITIAL_EMPTY = new WorkstationStateComponent(
            Identifier.of("k3_diegetic", "empty"),
            0,
            0,
            0.0f,
            false
    );

    /** State D: Edge/Boundary - Overheated masterwork forging state */
    public static final WorkstationStateComponent STATE_D_OVERHEATED_MASTER = new WorkstationStateComponent(
            Identifier.of("k3_diegetic", "smithing/damascus_core"),
            100,
            12,
            1.0f,
            true
    );

    private static final List<WorkstationStateComponent> TEST_VECTORS = List.of(
            STATE_A_INGOT_SMITHING,
            STATE_B_GEM_CUTTING,
            STATE_C_INITIAL_EMPTY,
            STATE_D_OVERHEATED_MASTER
    );

    // =========================================================================
    // TEST 1: DFU CODEC ROUND-TRIP (NbtOps & JsonOps)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testDfuCodecRoundTrip(TestContext context) {
        for (WorkstationStateComponent original : TEST_VECTORS) {
            // --- Channel 1A: NbtOps (World Disk / Chunk Storage) ---
            DataResult<NbtElement> nbtEncodeResult = WorkstationStateComponent.CODEC.encodeStart(NbtOps.INSTANCE, original);
            NbtElement nbtElement = nbtEncodeResult.getOrThrow(msg ->
                    new AssertionError("DFU Codec failed to encode state [" + original.recipeId() + "] to NBT: " + msg));

            DataResult<WorkstationStateComponent> nbtDecodeResult = WorkstationStateComponent.CODEC.parse(NbtOps.INSTANCE, nbtElement);
            WorkstationStateComponent nbtDecoded = nbtDecodeResult.getOrThrow(msg ->
                    new AssertionError("DFU Codec failed to parse state [" + original.recipeId() + "] from NBT: " + msg));

            assertComponentEquals(context, original, nbtDecoded, "DFU NbtOps");

            // --- Channel 1B: JsonOps (Data Pack Recipes & Command Syntax) ---
            DataResult<JsonElement> jsonEncodeResult = WorkstationStateComponent.CODEC.encodeStart(JsonOps.INSTANCE, original);
            JsonElement jsonElement = jsonEncodeResult.getOrThrow(msg ->
                    new AssertionError("DFU Codec failed to encode state [" + original.recipeId() + "] to JSON: " + msg));

            DataResult<WorkstationStateComponent> jsonDecodeResult = WorkstationStateComponent.CODEC.parse(JsonOps.INSTANCE, jsonElement);
            WorkstationStateComponent jsonDecoded = jsonDecodeResult.getOrThrow(msg ->
                    new AssertionError("DFU Codec failed to parse state [" + original.recipeId() + "] from JSON: " + msg));

            assertComponentEquals(context, original, jsonDecoded, "DFU JsonOps");
        }

        context.complete();
    }

    // =========================================================================
    // TEST 2: STREAM CODEC ROUND-TRIP (PacketByteBuf & RegistryByteBuf)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testStreamCodecRoundTrip(TestContext context) {
        for (WorkstationStateComponent original : TEST_VECTORS) {
            // --- Channel 2A: PacketByteBuf (Standard Binary Packet Stream) ---
            PacketByteBuf packetBuf = PacketByteBufs.create();
            WorkstationStateComponent.PACKET_CODEC.encode(packetBuf, original);

            context.assertTrue(packetBuf.readableBytes() > 0,
                    "Encoded buffer for [" + original.recipeId() + "] must have readable bytes");

            WorkstationStateComponent packetDecoded = WorkstationStateComponent.PACKET_CODEC.decode(packetBuf);

            assertComponentEquals(context, original, packetDecoded, "StreamCodec PacketByteBuf");
            context.assertTrue(packetBuf.readableBytes() == 0,
                    "PacketByteBuf for [" + original.recipeId() + "] has trailing unread bytes: " + packetBuf.readableBytes());

            // --- Channel 2B: RegistryByteBuf (Network Registry Sync Stream) ---
            RegistryByteBuf registryBuf = new RegistryByteBuf(new PacketByteBuf(Unpooled.buffer()), context.getWorld().getRegistryManager());
            WorkstationStateComponent.PACKET_CODEC.encode(registryBuf, original);

            context.assertTrue(registryBuf.readableBytes() > 0,
                    "Encoded registry buffer for [" + original.recipeId() + "] must have readable bytes");

            WorkstationStateComponent registryDecoded = WorkstationStateComponent.PACKET_CODEC.decode(registryBuf);

            assertComponentEquals(context, original, registryDecoded, "StreamCodec RegistryByteBuf");
            context.assertTrue(registryBuf.readableBytes() == 0,
                    "RegistryByteBuf for [" + original.recipeId() + "] has trailing unread bytes: " + registryBuf.readableBytes());
        }

        context.complete();
    }

    // =========================================================================
    // TEST 3: ITEMSTACK INTEGRATION & COMPONENT RETENTION
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testItemStackComponentLifecycle(TestContext context) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);

        // Verify empty initial state
        context.assertTrue(!stack.contains(ModDataComponentTypes.WORKSTATION_STATE),
                "Fresh ItemStack must not contain WORKSTATION_STATE component");

        // Attach State A
        stack.set(ModDataComponentTypes.WORKSTATION_STATE, STATE_A_INGOT_SMITHING);
        context.assertTrue(stack.contains(ModDataComponentTypes.WORKSTATION_STATE),
                "ItemStack must contain WORKSTATION_STATE after stack.set()");
        assertComponentEquals(context, STATE_A_INGOT_SMITHING, stack.get(ModDataComponentTypes.WORKSTATION_STATE), "ItemStack.get()");

        // Round-trip ItemStack via ItemStack.CODEC (NBT persistence)
        DataResult<NbtElement> itemNbtResult = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack);
        NbtElement itemNbt = itemNbtResult.getOrThrow(msg -> new AssertionError("ItemStack NBT encode failed: " + msg));

        DataResult<ItemStack> itemParseResult = ItemStack.CODEC.parse(NbtOps.INSTANCE, itemNbt);
        ItemStack deserializedStack = itemParseResult.getOrThrow(msg -> new AssertionError("ItemStack NBT parse failed: " + msg));

        context.assertTrue(deserializedStack.contains(ModDataComponentTypes.WORKSTATION_STATE),
                "Deserialized ItemStack must retain WORKSTATION_STATE component");
        assertComponentEquals(context, STATE_A_INGOT_SMITHING, deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE), "Deserialized ItemStack Component");

        // Mutate using immutable record transition
        WorkstationStateComponent updatedState = deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE)
                .withStrike(2, 20)
                .withThermalState(0.15f)
                .withActive(false);
        deserializedStack.set(ModDataComponentTypes.WORKSTATION_STATE, updatedState);

        context.assertTrue(deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount() == 2,
                "Mutated component must reflect 2 strikes");
        context.assertTrue(deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE).progressTicks() == 20,
                "Mutated component must reflect 20 progress ticks");
        context.assertTrue(Math.abs(deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE).thermalState() - 0.15f) < 1e-6f,
                "Mutated component must reflect 0.15f thermal");
        context.assertFalse(deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE).active(),
                "Mutated component must reflect inactive state");

        // Test component removal
        deserializedStack.remove(ModDataComponentTypes.WORKSTATION_STATE);
        context.assertFalse(deserializedStack.contains(ModDataComponentTypes.WORKSTATION_STATE),
                "ItemStack must not contain component after remove()");
        context.assertEquals(WorkstationStateComponent.DEFAULT,
                deserializedStack.getOrDefault(ModDataComponentTypes.WORKSTATION_STATE, WorkstationStateComponent.DEFAULT),
                "getOrDefault must return fallback when component is absent");

        context.complete();
    }

    // =========================================================================
    // TEST 4: IMMUTABLE WITHER CONTRACTS
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testWitherContracts(TestContext context) {
        WorkstationStateComponent base = WorkstationStateComponent.DEFAULT;

        // withRecipe
        Identifier recipe = Identifier.of("k3_diegetic", "smithing/custom_blade");
        WorkstationStateComponent withRecipe = base.withRecipe(recipe);
        context.assertEquals(recipe, withRecipe.recipeId(), "withRecipe should set recipeId");
        context.assertTrue(withRecipe.active(), "withRecipe should activate state");

        // withProgress
        WorkstationStateComponent withProgress = withRecipe.withProgress(42);
        context.assertEquals(42, withProgress.progressTicks(), "withProgress should set progressTicks");
        context.assertEquals(42, withProgress.progress(), "progress() alias should match progressTicks");

        // withStrike (1-arg)
        WorkstationStateComponent withStrike1 = withProgress.withStrike(7);
        context.assertEquals(7, withStrike1.strikeCount(), "withStrike(int) should set strikeCount");
        context.assertEquals(42, withStrike1.progressTicks(), "withStrike(int) should preserve progressTicks");

        // withStrike (2-arg)
        WorkstationStateComponent withStrike2 = withProgress.withStrike(9, 85);
        context.assertEquals(9, withStrike2.strikeCount(), "withStrike(int, int) should set strikeCount");
        context.assertEquals(85, withStrike2.progressTicks(), "withStrike(int, int) should set progressTicks");

        // withThermalState
        WorkstationStateComponent withThermal = withStrike2.withThermalState(0.75f);
        context.assertTrue(Math.abs(withThermal.thermalState() - 0.75f) < 1e-6f, "withThermalState should set thermal");

        // withActive
        WorkstationStateComponent withDeactivated = withThermal.withActive(false);
        context.assertFalse(withDeactivated.active(), "withActive(false) should deactivate state");

        // reset
        WorkstationStateComponent reset = withDeactivated.reset();
        context.assertEquals(WorkstationStateComponent.DEFAULT, reset, "reset() should return DEFAULT");

        context.complete();
    }

    // =========================================================================
    // TEST 5: ADVERSARIAL STRESS TEST (Dual-Input Boundaries & Truncation)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testDualInputAdversarialBufferBoundariesAndCorruption(TestContext context) {
        // --- 1. Bitwise Float Fidelity for Dual-Input States ---
        // State A: 0.85f
        PacketByteBuf bufA = PacketByteBufs.create();
        WorkstationStateComponent.PACKET_CODEC.encode(bufA, STATE_A_INGOT_SMITHING);
        int bytesA = bufA.readableBytes();
        WorkstationStateComponent decodedA = WorkstationStateComponent.PACKET_CODEC.decode(bufA);
        context.assertTrue(Float.floatToIntBits(STATE_A_INGOT_SMITHING.thermalState()) == Float.floatToIntBits(decodedA.thermalState()),
                "State A thermal float must be bitwise identical after PacketCodec decode");
        context.assertTrue(bufA.readableBytes() == 0, "Buffer A must have 0 unread bytes");

        // State B: 0.15f
        PacketByteBuf bufB = PacketByteBufs.create();
        WorkstationStateComponent.PACKET_CODEC.encode(bufB, STATE_B_GEM_CUTTING);
        int bytesB = bufB.readableBytes();
        WorkstationStateComponent decodedB = WorkstationStateComponent.PACKET_CODEC.decode(bufB);
        context.assertTrue(Float.floatToIntBits(STATE_B_GEM_CUTTING.thermalState()) == Float.floatToIntBits(decodedB.thermalState()),
                "State B thermal float must be bitwise identical after PacketCodec decode");
        context.assertTrue(bufB.readableBytes() == 0, "Buffer B must have 0 unread bytes");

        // --- 2. Concatenated Dual-Input Stream Pipeline (State A immediately followed by State B) ---
        PacketByteBuf streamBufAB = PacketByteBufs.create();
        WorkstationStateComponent.PACKET_CODEC.encode(streamBufAB, STATE_A_INGOT_SMITHING);
        WorkstationStateComponent.PACKET_CODEC.encode(streamBufAB, STATE_B_GEM_CUTTING);

        context.assertTrue(streamBufAB.readableBytes() == bytesA + bytesB,
                "Concatenated AB buffer must equal exact sum of State A and State B byte lengths: " + (bytesA + bytesB));

        WorkstationStateComponent streamDecodedA = WorkstationStateComponent.PACKET_CODEC.decode(streamBufAB);
        assertComponentEquals(context, STATE_A_INGOT_SMITHING, streamDecodedA, "Concatenated Stream A");
        context.assertTrue(streamBufAB.readableBytes() == bytesB,
                "After reading State A, stream buffer must have exactly bytesB remaining: " + bytesB);

        WorkstationStateComponent streamDecodedB = WorkstationStateComponent.PACKET_CODEC.decode(streamBufAB);
        assertComponentEquals(context, STATE_B_GEM_CUTTING, streamDecodedB, "Concatenated Stream B");
        context.assertTrue(streamBufAB.readableBytes() == 0,
                "After reading State B, stream buffer must have exactly zero unread trailing bytes");

        // --- 3. Reverse Concatenated Stream Pipeline (State B immediately followed by State A) ---
        PacketByteBuf streamBufBA = PacketByteBufs.create();
        WorkstationStateComponent.PACKET_CODEC.encode(streamBufBA, STATE_B_GEM_CUTTING);
        WorkstationStateComponent.PACKET_CODEC.encode(streamBufBA, STATE_A_INGOT_SMITHING);

        context.assertTrue(streamBufBA.readableBytes() == bytesB + bytesA,
                "Concatenated BA buffer must equal exact sum of State B and State A byte lengths: " + (bytesB + bytesA));

        WorkstationStateComponent revDecodedB = WorkstationStateComponent.PACKET_CODEC.decode(streamBufBA);
        assertComponentEquals(context, STATE_B_GEM_CUTTING, revDecodedB, "Reverse Stream B");
        context.assertTrue(streamBufBA.readableBytes() == bytesA,
                "After reading State B, stream buffer must have exactly bytesA remaining: " + bytesA);

        WorkstationStateComponent revDecodedA = WorkstationStateComponent.PACKET_CODEC.decode(streamBufBA);
        assertComponentEquals(context, STATE_A_INGOT_SMITHING, revDecodedA, "Reverse Stream A");
        context.assertTrue(streamBufBA.readableBytes() == 0,
                "After reading State A, reverse stream buffer must have exactly zero unread trailing bytes");

        // --- 4. Trailing Garbage Byte Isolation ---
        PacketByteBuf garbageBuf = PacketByteBufs.create();
        WorkstationStateComponent.PACKET_CODEC.encode(garbageBuf, STATE_A_INGOT_SMITHING);
        garbageBuf.writeInt(0x5A5A5A5A); // 4 trailing sentinel garbage bytes
        context.assertTrue(garbageBuf.readableBytes() == bytesA + 4, "Garbage buffer must contain bytesA + 4 bytes");

        WorkstationStateComponent decodedFromGarbage = WorkstationStateComponent.PACKET_CODEC.decode(garbageBuf);
        assertComponentEquals(context, STATE_A_INGOT_SMITHING, decodedFromGarbage, "Garbage Buffer Decode");
        context.assertTrue(garbageBuf.readableBytes() == 4,
                "Codec must not consume trailing bytes; exactly 4 bytes must remain unread");
        context.assertTrue(garbageBuf.readInt() == 0x5A5A5A5A, "Trailing bytes must remain uncorrupted 0x5A5A5A5A sentinel");

        // --- 5. Systematic Truncation & Underflow Protection ---
        // Verify every possible sub-length prefix of State A throws on decode
        for (int cutLength = 0; cutLength < bytesA; cutLength++) {
            PacketByteBuf fullBuf = PacketByteBufs.create();
            WorkstationStateComponent.PACKET_CODEC.encode(fullBuf, STATE_A_INGOT_SMITHING);
            byte[] truncatedBytes = new byte[cutLength];
            fullBuf.readBytes(truncatedBytes);

            PacketByteBuf truncatedBuf = PacketByteBufs.create();
            truncatedBuf.writeBytes(truncatedBytes);

            boolean underflowCaught = false;
            try {
                WorkstationStateComponent.PACKET_CODEC.decode(truncatedBuf);
            } catch (Exception e) {
                underflowCaught = true;
            }
            context.assertTrue(underflowCaught, "Decoding truncated State A buffer of length " + cutLength + "/" + bytesA + " must throw underflow exception");
        }

        // Verify every possible sub-length prefix of State B throws on decode
        for (int cutLength = 0; cutLength < bytesB; cutLength++) {
            PacketByteBuf fullBuf = PacketByteBufs.create();
            WorkstationStateComponent.PACKET_CODEC.encode(fullBuf, STATE_B_GEM_CUTTING);
            byte[] truncatedBytes = new byte[cutLength];
            fullBuf.readBytes(truncatedBytes);

            PacketByteBuf truncatedBuf = PacketByteBufs.create();
            truncatedBuf.writeBytes(truncatedBytes);

            boolean underflowCaught = false;
            try {
                WorkstationStateComponent.PACKET_CODEC.decode(truncatedBuf);
            } catch (Exception e) {
                underflowCaught = true;
            }
            context.assertTrue(underflowCaught, "Decoding truncated State B buffer of length " + cutLength + "/" + bytesB + " must throw underflow exception");
        }

        // --- 6. Extreme Values & Multi-Byte VarInt Bounds ---
        WorkstationStateComponent extremeState = new WorkstationStateComponent(
                Identifier.of("k3_diegetic", "smithing/ultra_dense_core"),
                10_000_000,
                500_000,
                1.0f,
                true
        );
        PacketByteBuf extremeBuf = PacketByteBufs.create();
        WorkstationStateComponent.PACKET_CODEC.encode(extremeBuf, extremeState);
        WorkstationStateComponent extremeDecoded = WorkstationStateComponent.PACKET_CODEC.decode(extremeBuf);
        assertComponentEquals(context, extremeState, extremeDecoded, "Extreme State PacketCodec");
        context.assertTrue(extremeBuf.readableBytes() == 0, "Extreme state buffer must have 0 unread bytes");

        // --- 7. DFU Codec Partial & Extra-Field Schema Drift ---
        // Empty NBT compound decodes to DEFAULT
        NbtCompound emptyNbt = new NbtCompound();
        DataResult<WorkstationStateComponent> emptyParse = WorkstationStateComponent.CODEC.parse(NbtOps.INSTANCE, emptyNbt);
        WorkstationStateComponent fromEmpty = emptyParse.getOrThrow(msg -> new AssertionError("Empty NBT parse failed: " + msg));
        context.assertEquals(WorkstationStateComponent.DEFAULT, fromEmpty, "Empty NBT must decode to DEFAULT");

        // NBT with extra unexpected field (future forward-compatibility)
        NbtCompound extraFieldNbt = new NbtCompound();
        extraFieldNbt.putString("recipe_id", "k3_diegetic:smithing/tempered_blade");
        extraFieldNbt.putInt("progress_ticks", 50);
        extraFieldNbt.putInt("strike_count", 5);
        extraFieldNbt.putFloat("thermal_state", 0.85f);
        extraFieldNbt.putBoolean("active", true);
        extraFieldNbt.putString("future_unknown_field", "some_data");
        DataResult<WorkstationStateComponent> extraParse = WorkstationStateComponent.CODEC.parse(NbtOps.INSTANCE, extraFieldNbt);
        WorkstationStateComponent fromExtra = extraParse.getOrThrow(msg -> new AssertionError("Extra-field NBT parse failed: " + msg));
        assertComponentEquals(context, STATE_A_INGOT_SMITHING, fromExtra, "DFU Extra-Field Forward Compatibility");

        context.complete();
    }

    // =========================================================================
    // ASSERTION HELPER
    // =========================================================================

    private static void assertComponentEquals(TestContext context, WorkstationStateComponent expected, WorkstationStateComponent actual, String channel) {
        context.assertTrue(expected != null && actual != null,
                "[" + channel + "] Components must not be null");
        context.assertTrue(expected.recipeId().equals(actual.recipeId()),
                "[" + channel + "] Recipe ID mismatch: expected " + expected.recipeId() + ", got " + actual.recipeId());
        context.assertTrue(expected.progressTicks() == actual.progressTicks(),
                "[" + channel + "] ProgressTicks mismatch: expected " + expected.progressTicks() + ", got " + actual.progressTicks());
        context.assertTrue(expected.progress() == actual.progress(),
                "[" + channel + "] Progress alias mismatch: expected " + expected.progress() + ", got " + actual.progress());
        context.assertTrue(expected.strikeCount() == actual.strikeCount(),
                "[" + channel + "] Strike count mismatch: expected " + expected.strikeCount() + ", got " + actual.strikeCount());
        context.assertTrue(Math.abs(expected.thermalState() - actual.thermalState()) < 1e-6f,
                "[" + channel + "] Thermal state mismatch: expected " + expected.thermalState() + ", got " + actual.thermalState());
        context.assertTrue(expected.active() == actual.active(),
                "[" + channel + "] Active status mismatch: expected " + expected.active() + ", got " + actual.active());
        context.assertTrue(expected.equals(actual),
                "[" + channel + "] Record equals() equality failed between expected and actual");
    }
}
