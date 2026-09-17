package com.k3.diegetic.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Blueprint item defining an artisan smithing template stencil.
 * Displays output workpiece, required tool, and required sequential materials in its tooltip.
 */
public class BlueprintItem extends Item {
    private final Text resultText;
    private final List<Text> requiredIngredientsText;
    private final Text toolText;

    public BlueprintItem(Settings settings, Text resultText, List<Text> requiredIngredientsText, Text toolText) {
        super(settings);
        this.resultText = resultText;
        this.requiredIngredientsText = List.copyOf(requiredIngredientsText);
        this.toolText = toolText;
    }

    public Text getResultText() {
        return this.resultText;
    }

    public List<Text> getRequiredIngredientsText() {
        return this.requiredIngredientsText;
    }

    public Text getToolText() {
        return this.toolText;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.k3_diegetic.blueprint.output", this.resultText).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.k3_diegetic.blueprint.tool", this.toolText).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.k3_diegetic.blueprint.requires_header").formatted(Formatting.YELLOW));
        for (Text ingredientText : this.requiredIngredientsText) {
            tooltip.add(Text.literal("  • ").formatted(Formatting.DARK_GRAY).append(ingredientText.copy().formatted(Formatting.WHITE)));
        }
    }
}
