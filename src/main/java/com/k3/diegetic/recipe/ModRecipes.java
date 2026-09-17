package com.k3.diegetic.recipe;

import com.k3.diegetic.K3DiegeticMod;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registration container for custom RecipeTypes and RecipeSerializers in K3 Diegetic.
 */
public class ModRecipes {
    public static final Identifier ARTISAN_CRAFTING_ID = K3DiegeticMod.id("artisan_crafting");

    public static final RecipeType<ArtisanCraftingRecipe> ARTISAN_CRAFTING_TYPE = Registry.register(
        Registries.RECIPE_TYPE,
        ARTISAN_CRAFTING_ID,
        new RecipeType<ArtisanCraftingRecipe>() {
            @Override
            public String toString() {
                return ARTISAN_CRAFTING_ID.toString();
            }
        }
    );

    public static final RecipeSerializer<ArtisanCraftingRecipe> ARTISAN_CRAFTING_SERIALIZER = Registry.register(
        Registries.RECIPE_SERIALIZER,
        ARTISAN_CRAFTING_ID,
        new ArtisanCraftingRecipe.Serializer()
    );

    /**
     * Triggers static initialization and registration.
     */
    public static void registerModRecipes() {
        K3DiegeticMod.LOGGER.info("Registered Artisan Crafting recipe type and serializer for {}", K3DiegeticMod.MOD_ID);
    }

    public static void register() {
        registerModRecipes();
    }
}
