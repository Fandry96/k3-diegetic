package com.k3.diegetic.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.input.RecipeInput;

import java.util.List;

/**
 * Modern Fabric 1.21.1 RecipeInput combining an active blueprint stencil
 * with an ordered list of staged workpiece materials on an artisan anvil.
 */
public record ArtisanRecipeInput(ItemStack blueprint, List<ItemStack> ingredients) implements RecipeInput {

    public ArtisanRecipeInput {
        if (blueprint == null) {
            blueprint = ItemStack.EMPTY;
        }
        if (ingredients == null) {
            ingredients = List.of();
        } else {
            ingredients = List.copyOf(ingredients);
        }
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == 0) {
            return this.blueprint;
        }
        int idx = slot - 1;
        if (idx >= 0 && idx < this.ingredients.size()) {
            return this.ingredients.get(idx);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSize() {
        return 1 + this.ingredients.size();
    }

    @Override
    public boolean isEmpty() {
        return this.blueprint.isEmpty() && this.ingredients.isEmpty();
    }
}
