package com.k3.diegetic.gametest;

import com.google.gson.JsonElement;
import com.k3.diegetic.component.ModDataComponentTypes;
import com.k3.diegetic.component.WorkstationStateComponent;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
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

    /** State A: Sword forging state, 2 staged items, 2 strikes completed, 0.45f thermal */
    public static final WorkstationStateComponent STATE_A_SWORD_FORGING = new WorkstationStateComponent(
            Identifier.of("k3_diegetic", "sword_blueprint"),
            List.of(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.STICK, 1)),
            2,
            0.45f
    );

    /** State B: Pickaxe forging state, 3 staged items, 1 strike completed, 0.25f thermal */
    public static final WorkstationStateComponent STATE_B_PICKAXE_FORGING = new WorkstationStateComponent(
            Identifier.of("k3_diegetic", "pickaxe_blueprint"),
            List.of(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.STICK, 1)),
            1,
            0.25f
    );

    /** State C: Edge/Boundary - Zero initial workstation state */
    public static final WorkstationStateComponent STATE_C_INITIAL_EMPTY = WorkstationStateComponent.DEFAULT;

    /** State D: Edge/Boundary - Overheated masterwork forging state */
    public static final WorkstationStateComponent STATE_D_OVERHEATED_MASTER = new WorkstationStateComponent(
            Identifier.of("k3_diegetic", "axe_blueprint"),
            List.of(new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 2)),
            12,
            1.0f
    );

    private static final List<WorkstationStateComponent> TEST_VECTORS = List.of(
            STATE_A_SWORD_FORGING,
            STATE_B_PICKAXE_FORGING,
            STATE_C_INITIAL_EMPTY,
            STATE_D_OVERHEATED_MASTER
    );

    private RegistryByteBuf createRegistryBuf(TestContext context) {
        return new RegistryByteBuf(PacketByteBufs.create(), context.getWorld().getRegistryManager());
    }

    // =========================================================================
    // TEST 1: DFU CODEC ROUND-TRIP (NbtOps & JsonOps)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testDfuCodecRoundTrip(TestContext context) {
        for (WorkstationStateComponent original : TEST_VECTORS) {
            // --- Channel 1A: NbtOps (World Disk / Chunk Storage) ---
            DataResult<NbtElement> nbtEncodeResult = WorkstationStateComponent.CODEC.encodeStart(NbtOps.INSTANCE, original);
            NbtElement nbtElement = nbtEncodeResult.getOrThrow(msg ->
                    new AssertionError("DFU Codec failed to encode state [" + original.activeBlueprint() + "] to NBT: " + msg));

            DataResult<WorkstationStateComponent> nbtDecodeResult = WorkstationStateComponent.CODEC.parse(NbtOps.INSTANCE, nbtElement);
            WorkstationStateComponent nbtDecoded = nbtDecodeResult.getOrThrow(msg ->
                    new AssertionError("DFU Codec failed to parse state [" + original.activeBlueprint() + "] from NBT: " + msg));

            assertComponentEquals(context, original, nbtDecoded, "DFU NbtOps");

            // --- Channel 1B: JsonOps (Data Pack Recipes & Command Syntax) ---
            DataResult<JsonElement> jsonEncodeResult = WorkstationStateComponent.CODEC.encodeStart(JsonOps.INSTANCE, original);
            JsonElement jsonElement = jsonEncodeResult.getOrThrow(msg ->
                    new AssertionError("DFU Codec failed to encode state [" + original.activeBlueprint() + "] to JSON: " + msg));

            DataResult<WorkstationStateComponent> jsonDecodeResult = WorkstationStateComponent.CODEC.parse(JsonOps.INSTANCE, jsonElement);
            WorkstationStateComponent jsonDecoded = jsonDecodeResult.getOrThrow(msg ->
                    new AssertionError("DFU Codec failed to parse state [" + original.activeBlueprint() + "] from JSON: " + msg));

            assertComponentEquals(context, original, jsonDecoded, "DFU JsonOps");
        }

        context.complete();
    }

    // =========================================================================
    // TEST 2: STREAM CODEC ROUND-TRIP (RegistryByteBuf)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testStreamCodecRoundTrip(TestContext context) {
        for (WorkstationStateComponent original : TEST_VECTORS) {
            RegistryByteBuf registryBuf = createRegistryBuf(context);
            WorkstationStateComponent.PACKET_CODEC.encode(registryBuf, original);

            context.assertTrue(registryBuf.readableBytes() > 0,
                    "Encoded registry buffer for [" + original.activeBlueprint() + "] must have readable bytes");

            WorkstationStateComponent registryDecoded = WorkstationStateComponent.PACKET_CODEC.decode(registryBuf);

            assertComponentEquals(context, original, registryDecoded, "StreamCodec RegistryByteBuf");
            context.assertEquals(0, registryBuf.readableBytes(),
                    "RegistryByteBuf for [" + original.activeBlueprint() + "] has trailing unread bytes: " + registryBuf.readableBytes());
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
        stack.set(ModDataComponentTypes.WORKSTATION_STATE, STATE_A_SWORD_FORGING);
        context.assertTrue(stack.contains(ModDataComponentTypes.WORKSTATION_STATE),
                "ItemStack must contain WORKSTATION_STATE after stack.set()");
        assertComponentEquals(context, STATE_A_SWORD_FORGING, stack.get(ModDataComponentTypes.WORKSTATION_STATE), "ItemStack.get()");

        // Round-trip ItemStack via ItemStack.CODEC (NBT persistence)
        DataResult<NbtElement> itemNbtResult = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack);
        NbtElement itemNbt = itemNbtResult.getOrThrow(msg -> new AssertionError("ItemStack NBT encode failed: " + msg));

        DataResult<ItemStack> itemParseResult = ItemStack.CODEC.parse(NbtOps.INSTANCE, itemNbt);
        ItemStack deserializedStack = itemParseResult.getOrThrow(msg -> new AssertionError("ItemStack NBT parse failed: " + msg));

        context.assertTrue(deserializedStack.contains(ModDataComponentTypes.WORKSTATION_STATE),
                "Deserialized ItemStack must retain WORKSTATION_STATE component");
        assertComponentEquals(context, STATE_A_SWORD_FORGING, deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE), "Deserialized ItemStack Component");

        // Mutate using immutable record transition
        WorkstationStateComponent updatedState = deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE)
                .withStrike(3)
                .withThermalState(0.85f);
        deserializedStack.set(ModDataComponentTypes.WORKSTATION_STATE, updatedState);

        context.assertEquals(3, deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE).strikeCount(),
                "Mutated component must reflect 3 strikes");
        context.assertTrue(Math.abs(deserializedStack.get(ModDataComponentTypes.WORKSTATION_STATE).thermalState() - 0.85f) < 1e-6f,
                "Mutated component must reflect 0.85f thermal");

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

        // withBlueprint
        Identifier bp = Identifier.of("k3_diegetic", "custom_blueprint");
        WorkstationStateComponent withBp = base.withBlueprint(bp);
        context.assertEquals(bp, withBp.activeBlueprint(), "withBlueprint should set activeBlueprint");
        context.assertTrue(withBp.active(), "withBlueprint should activate state");

        // withStagedIngredients
        List<ItemStack> ingredients = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STICK));
        WorkstationStateComponent withIngs = withBp.withStagedIngredients(ingredients);
        context.assertEquals(2, withIngs.stagedIngredients().size(), "withStagedIngredients should update ingredients");

        // withStrike
        WorkstationStateComponent withStrike = withIngs.withStrike(5);
        context.assertEquals(5, withStrike.strikeCount(), "withStrike should set strikeCount");

        // withThermalState
        WorkstationStateComponent withThermal = withStrike.withThermalState(0.75f);
        context.assertTrue(Math.abs(withThermal.thermalState() - 0.75f) < 1e-6f, "withThermalState should set thermal");

        // reset
        WorkstationStateComponent reset = withThermal.reset();
        context.assertEquals(WorkstationStateComponent.DEFAULT, reset, "reset() should return DEFAULT");

        context.complete();
    }

    // =========================================================================
    // TEST 5: ADVERSARIAL STRESS TEST (Dual-Input Boundaries & Truncation)
    // =========================================================================

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void testDualInputAdversarialBufferBoundariesAndCorruption(TestContext context) {
        // --- 1. Bitwise Float Fidelity for Dual-Input States ---
        RegistryByteBuf bufA = createRegistryBuf(context);
        WorkstationStateComponent.PACKET_CODEC.encode(bufA, STATE_A_SWORD_FORGING);
        int bytesA = bufA.readableBytes();
        WorkstationStateComponent decodedA = WorkstationStateComponent.PACKET_CODEC.decode(bufA);
        context.assertEquals(Float.floatToIntBits(STATE_A_SWORD_FORGING.thermalState()),
                Float.floatToIntBits(decodedA.thermalState()),
                "State A thermal float must be bitwise identical after PacketCodec decode");
        context.assertEquals(0, bufA.readableBytes(), "Buffer A must have 0 unread bytes");

        RegistryByteBuf bufB = createRegistryBuf(context);
        WorkstationStateComponent.PACKET_CODEC.encode(bufB, STATE_B_PICKAXE_FORGING);
        int bytesB = bufB.readableBytes();
        WorkstationStateComponent decodedB = WorkstationStateComponent.PACKET_CODEC.decode(bufB);
        context.assertEquals(Float.floatToIntBits(STATE_B_PICKAXE_FORGING.thermalState()),
                Float.floatToIntBits(decodedB.thermalState()),
                "State B thermal float must be bitwise identical after PacketCodec decode");
        context.assertEquals(0, bufB.readableBytes(), "Buffer B must have 0 unread bytes");

        // --- 2. Concatenated Dual-Input Stream Pipeline (State A immediately followed by State B) ---
        RegistryByteBuf streamBufAB = createRegistryBuf(context);
        WorkstationStateComponent.PACKET_CODEC.encode(streamBufAB, STATE_A_SWORD_FORGING);
        WorkstationStateComponent.PACKET_CODEC.encode(streamBufAB, STATE_B_PICKAXE_FORGING);

        context.assertEquals(bytesA + bytesB, streamBufAB.readableBytes(),
                "Concatenated AB buffer must equal exact sum of State A and State B byte lengths: " + (bytesA + bytesB));

        WorkstationStateComponent streamDecodedA = WorkstationStateComponent.PACKET_CODEC.decode(streamBufAB);
        assertComponentEquals(context, STATE_A_SWORD_FORGING, streamDecodedA, "Concatenated Stream A");
        context.assertEquals(bytesB, streamBufAB.readableBytes(),
                "After reading State A, stream buffer must have exactly bytesB remaining: " + bytesB);

        WorkstationStateComponent streamDecodedB = WorkstationStateComponent.PACKET_CODEC.decode(streamBufAB);
        assertComponentEquals(context, STATE_B_PICKAXE_FORGING, streamDecodedB, "Concatenated Stream B");
        context.assertEquals(0, streamBufAB.readableBytes(),
                "After reading State B, stream buffer must have exactly zero unread trailing bytes");

        // --- 3. Reverse Concatenated Stream Pipeline (State B immediately followed by State A) ---
        RegistryByteBuf streamBufBA = createRegistryBuf(context);
        WorkstationStateComponent.PACKET_CODEC.encode(streamBufBA, STATE_B_PICKAXE_FORGING);
        WorkstationStateComponent.PACKET_CODEC.encode(streamBufBA, STATE_A_SWORD_FORGING);

        context.assertEquals(bytesB + bytesA, streamBufBA.readableBytes(),
                "Concatenated BA buffer must equal exact sum of State B and State A byte lengths: " + (bytesB + bytesA));

        WorkstationStateComponent revDecodedB = WorkstationStateComponent.PACKET_CODEC.decode(streamBufBA);
        assertComponentEquals(context, STATE_B_PICKAXE_FORGING, revDecodedB, "Reverse Stream B");
        context.assertEquals(bytesA, streamBufBA.readableBytes(),
                "After reading State B, stream buffer must have exactly bytesA remaining: " + bytesA);

        WorkstationStateComponent revDecodedA = WorkstationStateComponent.PACKET_CODEC.decode(streamBufBA);
        assertComponentEquals(context, STATE_A_SWORD_FORGING, revDecodedA, "Reverse Stream A");
        context.assertEquals(0, streamBufBA.readableBytes(),
                "After reading State A, reverse stream buffer must have exactly zero unread trailing bytes");

        // --- 4. Trailing Garbage Byte Isolation ---
        RegistryByteBuf garbageBuf = createRegistryBuf(context);
        WorkstationStateComponent.PACKET_CODEC.encode(garbageBuf, STATE_A_SWORD_FORGING);
        garbageBuf.writeInt(0x5A5A5A5A); // 4 trailing sentinel garbage bytes
        context.assertEquals(bytesA + 4, garbageBuf.readableBytes(), "Garbage buffer must contain bytesA + 4 bytes");

        WorkstationStateComponent decodedFromGarbage = WorkstationStateComponent.PACKET_CODEC.decode(garbageBuf);
        assertComponentEquals(context, STATE_A_SWORD_FORGING, decodedFromGarbage, "Garbage Buffer Decode");
        context.assertEquals(4, garbageBuf.readableBytes(),
                "Codec must not consume trailing bytes; exactly 4 bytes must remain unread");
        context.assertEquals(0x5A5A5A5A, garbageBuf.readInt(), "Trailing bytes must remain uncorrupted 0x5A5A5A5A sentinel");

        // --- 5. Systematic Truncation & Underflow Protection ---
        // Verify every possible sub-length prefix of State A throws on decode
        for (int cutLength = 0; cutLength < bytesA; cutLength++) {
            RegistryByteBuf fullBuf = createRegistryBuf(context);
            WorkstationStateComponent.PACKET_CODEC.encode(fullBuf, STATE_A_SWORD_FORGING);
            byte[] truncatedBytes = new byte[cutLength];
            fullBuf.readBytes(truncatedBytes);

            RegistryByteBuf truncatedBuf = createRegistryBuf(context);
            truncatedBuf.writeBytes(truncatedBytes);

            boolean underflowCaught = false;
            try {
                WorkstationStateComponent.PACKET_CODEC.decode(truncatedBuf);
            } catch (Exception e) {
                underflowCaught = true;
            }
            context.assertTrue(underflowCaught, "Decoding truncated State A buffer of length " + cutLength + "/" + bytesA + " must throw underflow exception");
        }

        // --- 6. DFU Codec Partial & Extra-Field Schema Drift ---
        // Empty NBT compound decodes to DEFAULT
        NbtCompound emptyNbt = new NbtCompound();
        DataResult<WorkstationStateComponent> emptyParse = WorkstationStateComponent.CODEC.parse(NbtOps.INSTANCE, emptyNbt);
        WorkstationStateComponent fromEmpty = emptyParse.getOrThrow(msg -> new AssertionError("Empty NBT parse failed: " + msg));
        context.assertEquals(WorkstationStateComponent.DEFAULT, fromEmpty, "Empty NBT must decode to DEFAULT");

        // NBT with extra unexpected field (future forward-compatibility)
        NbtCompound extraFieldNbt = new NbtCompound();
        extraFieldNbt.putString("active_blueprint", "k3_diegetic:sword_blueprint");
        extraFieldNbt.putInt("strike_count", 2);
        extraFieldNbt.putFloat("thermal_state", 0.45f);
        extraFieldNbt.putString("future_unknown_field", "some_data");
        DataResult<WorkstationStateComponent> extraParse = WorkstationStateComponent.CODEC.parse(NbtOps.INSTANCE, extraFieldNbt);
        WorkstationStateComponent fromExtra = extraParse.getOrThrow(msg -> new AssertionError("Extra-field NBT parse failed: " + msg));
        context.assertEquals(Identifier.of("k3_diegetic", "sword_blueprint"), fromExtra.activeBlueprint(), "Extra field NBT activeBlueprint mismatch");
        context.assertEquals(2, fromExtra.strikeCount(), "Extra field NBT strikeCount mismatch");
        context.assertTrue(Math.abs(fromExtra.thermalState() - 0.45f) < 1e-6f, "Extra field NBT thermalState mismatch");

        context.complete();
    }

    // =========================================================================
    // ASSERTION HELPER
    // =========================================================================

    private static void assertComponentEquals(TestContext context, WorkstationStateComponent expected, WorkstationStateComponent actual, String channel) {
        context.assertTrue(expected != null && actual != null,
                "[" + channel + "] Components must not be null");
        context.assertEquals(expected.activeBlueprint(), actual.activeBlueprint(),
                "[" + channel + "] Blueprint mismatch: expected " + expected.activeBlueprint() + ", got " + actual.activeBlueprint());
        context.assertEquals(expected.strikeCount(), actual.strikeCount(),
                "[" + channel + "] Strike count mismatch: expected " + expected.strikeCount() + ", got " + actual.strikeCount());
        context.assertTrue(Math.abs(expected.thermalState() - actual.thermalState()) < 1e-6f,
                "[" + channel + "] Thermal state mismatch: expected " + expected.thermalState() + ", got " + actual.thermalState());
        context.assertEquals(expected.stagedIngredients().size(), actual.stagedIngredients().size(),
                "[" + channel + "] Staged ingredients count mismatch");
        for (int i = 0; i < expected.stagedIngredients().size(); i++) {
            ItemStack expStack = expected.stagedIngredients().get(i);
            ItemStack actStack = actual.stagedIngredients().get(i);
            context.assertTrue(ItemStack.areEqual(expStack, actStack),
                    "[" + channel + "] Staged item " + i + " mismatch (expected " + expStack + ", got " + actStack + ")");
        }
    }
}
