package com.k3.diegetic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Immutable Data Component tracking diegetic crafting workstation state on item stacks.
 * Stores recipe identifier, current progress counter, tool strike count, thermal state,
 * and active status.
 *
 * Provides dual-channel serialization:
 * - Mojang DFU Codec for world disk persistence, commands (/give), and JSON recipes.
 * - Netty PacketCodec for zero-overhead delta synchronization across multiplayer sockets.
 */
public record WorkstationStateComponent(
    Identifier recipeId,
    int progressTicks,
    int strikeCount,
    float thermalState,
    boolean active
) implements TooltipAppender {

    /** Fallback recipe ID for unassigned or idle workpieces. */
    public static final Identifier EMPTY_RECIPE_ID = Identifier.of("k3_diegetic", "empty");

    /** Default immutable instance representing an unworked workpiece. */
    public static final WorkstationStateComponent DEFAULT = new WorkstationStateComponent(
        EMPTY_RECIPE_ID, 0, 0, 0.0f, false
    );

    /** Alias for DEFAULT to support EMPTY constant naming convention. */
    public static final WorkstationStateComponent EMPTY = DEFAULT;

    /**
     * Compact constructor enforcing non-null identifiers and non-negative metric bounds.
     */
    public WorkstationStateComponent {
        if (recipeId == null) {
            recipeId = EMPTY_RECIPE_ID;
        }
        progressTicks = Math.max(0, progressTicks);
        strikeCount = Math.max(0, strikeCount);
        thermalState = Math.max(0.0f, thermalState);
    }

    /**
     * Convenience 4-parameter constructor defaulting active status.
     */
    public WorkstationStateComponent(Identifier recipeId, int progressTicks, int strikeCount, float thermalState) {
        this(recipeId, progressTicks, strikeCount, thermalState, progressTicks > 0 || strikeCount > 0);
    }

    /* =========================================================================
     * DUAL-CHANNEL SERIALIZATION CODECS
     * ========================================================================= */

    /**
     * Channel 1: Mojang DFU Codec for NBT world disk storage, command parsing, and data packs.
     * All fields are safely defaulted to prevent deserialization crashes on partial input.
     */
    public static final Codec<WorkstationStateComponent> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Identifier.CODEC.optionalFieldOf("recipe_id", EMPTY_RECIPE_ID).forGetter(WorkstationStateComponent::recipeId),
            Codec.INT.optionalFieldOf("progress_ticks", 0).forGetter(WorkstationStateComponent::progressTicks),
            Codec.INT.optionalFieldOf("strike_count", 0).forGetter(WorkstationStateComponent::strikeCount),
            Codec.FLOAT.optionalFieldOf("thermal_state", 0.0f).forGetter(WorkstationStateComponent::thermalState),
            Codec.BOOL.optionalFieldOf("active", false).forGetter(WorkstationStateComponent::active)
        ).apply(instance, WorkstationStateComponent::new)
    );

    /**
     * Channel 2: High-performance binary PacketCodec for Netty multiplayer delta sync.
     * Encodes into variable-length integers and raw IEEE-754 floats (~10 bytes total payload).
     */
    public static final PacketCodec<ByteBuf, WorkstationStateComponent> PACKET_CODEC = PacketCodec.tuple(
        Identifier.PACKET_CODEC, WorkstationStateComponent::recipeId,
        PacketCodecs.VAR_INT, WorkstationStateComponent::progressTicks,
        PacketCodecs.VAR_INT, WorkstationStateComponent::strikeCount,
        PacketCodecs.FLOAT, WorkstationStateComponent::thermalState,
        PacketCodecs.BOOL, WorkstationStateComponent::active,
        WorkstationStateComponent::new
    );

    /* =========================================================================
     * ACCESSOR / ALIAS METHODS
     * ========================================================================= */

    /** Alias for progressTicks for interface contract parity with milestone specs. */
    public int progress() {
        return this.progressTicks;
    }

    /* =========================================================================
     * IMMUTABLE WITHER EVOLUTION METHODS
     * ========================================================================= */

    /** Returns a new component updated with new strike count. */
    public WorkstationStateComponent withStrike(int strikeCount) {
        return new WorkstationStateComponent(this.recipeId, this.progressTicks, strikeCount, this.thermalState, this.active);
    }

    /** Returns a new component updated with new strike count and progress ticks. */
    public WorkstationStateComponent withStrike(int strikeCount, int progressTicks) {
        return new WorkstationStateComponent(this.recipeId, progressTicks, strikeCount, this.thermalState, this.active);
    }

    /** Returns a new component with an updated thermal state metric. */
    public WorkstationStateComponent withThermalState(float thermalState) {
        return new WorkstationStateComponent(this.recipeId, this.progressTicks, this.strikeCount, thermalState, this.active);
    }

    /** Returns a new component with updated progress ticks. */
    public WorkstationStateComponent withProgress(int progressTicks) {
        return new WorkstationStateComponent(this.recipeId, progressTicks, this.strikeCount, this.thermalState, this.active);
    }

    /** Returns a new component with updated active status. */
    public WorkstationStateComponent withActive(boolean active) {
        return new WorkstationStateComponent(this.recipeId, this.progressTicks, this.strikeCount, this.thermalState, active);
    }

    /** Returns a new component bound to a newly locked recipe. */
    public WorkstationStateComponent withRecipe(Identifier recipeId) {
        return new WorkstationStateComponent(recipeId, 0, 0, this.thermalState, true);
    }

    /** Resets component to default empty state. */
    public WorkstationStateComponent reset() {
        return DEFAULT;
    }

    /* =========================================================================
     * TOOLTIP INTEGRATION
     * ========================================================================= */

    @Override
    public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type) {
        if (!this.active && this.recipeId.equals(EMPTY_RECIPE_ID) && this.strikeCount == 0) {
            return;
        }

        if (!this.recipeId.equals(EMPTY_RECIPE_ID)) {
            textConsumer.accept(Text.translatable("tooltip.k3_diegetic.recipe", this.recipeId.toString())
                .formatted(Formatting.GRAY));
        }

        textConsumer.accept(Text.translatable("tooltip.k3_diegetic.strikes", this.strikeCount)
            .formatted(Formatting.GOLD));

        textConsumer.accept(Text.translatable("tooltip.k3_diegetic.progress", this.progressTicks)
            .formatted(Formatting.YELLOW));

        Formatting tempColor = this.thermalState > 0.5f ? Formatting.RED : Formatting.AQUA;
        textConsumer.accept(Text.translatable("tooltip.k3_diegetic.thermal", String.format(Locale.ROOT, "%.2f", this.thermalState))
            .formatted(tempColor));

        if (this.active) {
            textConsumer.accept(Text.translatable("tooltip.k3_diegetic.status.active")
                .formatted(Formatting.GREEN));
        }
    }
}
