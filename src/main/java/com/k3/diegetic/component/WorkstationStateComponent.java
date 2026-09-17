package com.k3.diegetic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Immutable Data Component tracking diegetic crafting workstation state on item stacks.
 * Records active blueprint stencil identifier, staged workpiece ingredients list,
 * hammer strike count, and thermal state.
 *
 * Provides dual-channel serialization:
 * - Mojang DFU Codec for world disk persistence, commands (/give), and JSON data packs.
 * - Netty PacketCodec for zero-overhead multiplayer synchronization across sockets.
 */
public record WorkstationStateComponent(
    Identifier activeBlueprint,
    List<ItemStack> stagedIngredients,
    int strikeCount,
    float thermalState
) implements TooltipAppender {

    /** Fallback empty blueprint identifier for unassigned or idle workpieces. */
    public static final Identifier EMPTY_BLUEPRINT = Identifier.of("k3_diegetic", "empty");

    /** Default immutable instance representing an empty workstation state. */
    public static final WorkstationStateComponent DEFAULT = new WorkstationStateComponent(
        EMPTY_BLUEPRINT, List.of(), 0, 0.0f
    );

    /** Alias for DEFAULT constant naming convention. */
    public static final WorkstationStateComponent EMPTY = DEFAULT;

    /**
     * Compact constructor enforcing non-null identifiers and non-negative metric bounds.
     */
    public WorkstationStateComponent {
        if (activeBlueprint == null) {
            activeBlueprint = EMPTY_BLUEPRINT;
        }
        if (stagedIngredients == null) {
            stagedIngredients = List.of();
        } else {
            stagedIngredients = List.copyOf(stagedIngredients);
        }
        strikeCount = Math.max(0, strikeCount);
        thermalState = Math.max(0.0f, thermalState);
    }

    /**
     * Convenience constructor supporting legacy 5-parameter signatures.
     */
    public WorkstationStateComponent(Identifier recipeId, int progressTicks, int strikeCount, float thermalState, boolean active) {
        this(recipeId != null ? recipeId : EMPTY_BLUEPRINT, List.of(), strikeCount, thermalState);
    }

    /* =========================================================================
     * DUAL-CHANNEL SERIALIZATION CODECS
     * ========================================================================= */

    /**
     * Channel 1: Mojang DFU Codec for NBT world disk storage, command parsing, and data packs.
     */
    public static final Codec<WorkstationStateComponent> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Identifier.CODEC.optionalFieldOf("active_blueprint", EMPTY_BLUEPRINT).forGetter(WorkstationStateComponent::activeBlueprint),
            ItemStack.CODEC.listOf().optionalFieldOf("staged_ingredients", List.of()).forGetter(WorkstationStateComponent::stagedIngredients),
            Codec.INT.optionalFieldOf("strike_count", 0).forGetter(WorkstationStateComponent::strikeCount),
            Codec.FLOAT.optionalFieldOf("thermal_state", 0.0f).forGetter(WorkstationStateComponent::thermalState)
        ).apply(instance, WorkstationStateComponent::new)
    );

    /**
     * Channel 2: High-performance binary PacketCodec for Netty multiplayer delta sync.
     */
    public static final PacketCodec<RegistryByteBuf, WorkstationStateComponent> PACKET_CODEC = PacketCodec.tuple(
        Identifier.PACKET_CODEC, WorkstationStateComponent::activeBlueprint,
        ItemStack.PACKET_CODEC.collect(PacketCodecs.toList()), WorkstationStateComponent::stagedIngredients,
        PacketCodecs.VAR_INT, WorkstationStateComponent::strikeCount,
        PacketCodecs.FLOAT, WorkstationStateComponent::thermalState,
        WorkstationStateComponent::new
    );

    /* =========================================================================
     * ACCESSOR / ALIAS METHODS FOR CONTRACT AND BACKWARD COMPATIBILITY
     * ========================================================================= */

    public Identifier recipeId() {
        return this.activeBlueprint;
    }

    public int progressTicks() {
        return this.strikeCount;
    }

    public int progress() {
        return this.strikeCount;
    }

    public boolean active() {
        return !this.activeBlueprint.equals(EMPTY_BLUEPRINT) || !this.stagedIngredients.isEmpty() || this.strikeCount > 0;
    }

    /* =========================================================================
     * IMMUTABLE WITHER EVOLUTION METHODS
     * ========================================================================= */

    public WorkstationStateComponent withBlueprint(Identifier blueprintId) {
        return new WorkstationStateComponent(blueprintId, this.stagedIngredients, this.strikeCount, this.thermalState);
    }

    public WorkstationStateComponent withStagedIngredients(List<ItemStack> ingredients) {
        return new WorkstationStateComponent(this.activeBlueprint, ingredients, this.strikeCount, this.thermalState);
    }

    public WorkstationStateComponent withStrike(int strikeCount) {
        return new WorkstationStateComponent(this.activeBlueprint, this.stagedIngredients, strikeCount, this.thermalState);
    }

    public WorkstationStateComponent withStrike(int strikeCount, int progressTicks) {
        return withStrike(strikeCount);
    }

    public WorkstationStateComponent withThermalState(float thermalState) {
        return new WorkstationStateComponent(this.activeBlueprint, this.stagedIngredients, this.strikeCount, thermalState);
    }

    public WorkstationStateComponent withProgress(int progressTicks) {
        return this;
    }

    public WorkstationStateComponent withActive(boolean active) {
        return this;
    }

    public WorkstationStateComponent withRecipe(Identifier recipeId) {
        return withBlueprint(recipeId);
    }

    public WorkstationStateComponent reset() {
        return DEFAULT;
    }

    /* =========================================================================
     * TOOLTIP INTEGRATION
     * ========================================================================= */

    @Override
    public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type) {
        if (this.activeBlueprint.equals(EMPTY_BLUEPRINT) && this.stagedIngredients.isEmpty() && this.strikeCount == 0) {
            return;
        }

        if (!this.activeBlueprint.equals(EMPTY_BLUEPRINT)) {
            textConsumer.accept(Text.translatable("tooltip.k3_diegetic.blueprint", this.activeBlueprint.toString())
                .formatted(Formatting.GRAY));
        }

        if (!this.stagedIngredients.isEmpty()) {
            textConsumer.accept(Text.literal("Staged items: " + this.stagedIngredients.size())
                .formatted(Formatting.YELLOW));
        }

        textConsumer.accept(Text.translatable("tooltip.k3_diegetic.strikes", this.strikeCount)
            .formatted(Formatting.GOLD));

        Formatting tempColor = this.thermalState > 0.5f ? Formatting.RED : Formatting.AQUA;
        textConsumer.accept(Text.translatable("tooltip.k3_diegetic.thermal", String.format(Locale.ROOT, "%.2f", this.thermalState))
            .formatted(tempColor));
    }
}
