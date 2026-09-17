package com.k3.diegetic.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.List;

/**
 * Modern Fabric 1.21.1 custom recipe representing physical artisan workstation blacksmithing.
 * Combines an in-world blueprint stencil with an ordered list of workpiece ingredients
 * and a handheld strike tool over N strikes.
 *
 * Implements Recipe<ArtisanRecipeInput>.
 * Dual-channel serialization via RecordCodecBuilder (JSON MapCodec) and PacketCodec (binary Netty).
 */
public class ArtisanCraftingRecipe implements Recipe<ArtisanRecipeInput> {
    private final Ingredient blueprint;
    private final DefaultedList<Ingredient> ingredients;
    private final Ingredient tool;
    private final int requiredStrikes;
    private final ItemStack result;

    public ArtisanCraftingRecipe(Ingredient blueprint, DefaultedList<Ingredient> ingredients,
                                 Ingredient tool, int requiredStrikes, ItemStack result) {
        this.blueprint = blueprint != null ? blueprint : Ingredient.EMPTY;
        this.ingredients = ingredients != null ? ingredients : DefaultedList.of();
        this.tool = tool;
        this.requiredStrikes = Math.max(1, requiredStrikes);
        this.result = result;
    }

    /**
     * Backward-compatible convenience constructor for single-ingredient workflows.
     */
    public ArtisanCraftingRecipe(Ingredient ingredient, Ingredient tool, int requiredStrikes, ItemStack result) {
        this(Ingredient.EMPTY, createSingleIngredientList(ingredient), tool, requiredStrikes, result);
    }

    private static DefaultedList<Ingredient> createSingleIngredientList(Ingredient ingredient) {
        DefaultedList<Ingredient> list = DefaultedList.of();
        if (ingredient != null && !ingredient.isEmpty()) {
            list.add(ingredient);
        }
        return list;
    }

    public Ingredient blueprint() {
        return this.blueprint;
    }

    public DefaultedList<Ingredient> ingredients() {
        return this.ingredients;
    }

    /**
     * Legacy single ingredient accessor for backward compatibility.
     */
    public Ingredient ingredient() {
        return this.ingredients.isEmpty() ? Ingredient.EMPTY : this.ingredients.get(0);
    }

    public Ingredient tool() {
        return this.tool;
    }

    public int requiredStrikes() {
        return this.requiredStrikes;
    }

    public ItemStack result() {
        return this.result;
    }

    @Override
    public boolean matches(ArtisanRecipeInput input, World world) {
        if (!this.blueprint.isEmpty()) {
            if (!this.blueprint.test(input.blueprint())) {
                return false;
            }
        } else if (!input.blueprint().isEmpty()) {
            return false;
        }

        if (input.ingredients().size() != this.ingredients.size()) {
            return false;
        }

        for (int i = 0; i < this.ingredients.size(); i++) {
            if (!this.ingredients.get(i).test(input.ingredients().get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Backward-compatible single-stack match for legacy tests.
     */
    public boolean matches(SingleStackRecipeInput input, World world) {
        if (this.ingredients.isEmpty()) {
            return false;
        }
        return this.ingredients.get(0).test(input.item());
    }

    /**
     * Checks whether the held tool matches the required striking tool for this recipe.
     */
    public boolean matchesTool(ItemStack toolStack) {
        return this.tool.test(toolStack);
    }

    /**
     * Checks whether the blueprint matches this recipe.
     */
    public boolean matchesBlueprint(ItemStack blueprintStack) {
        if (this.blueprint.isEmpty()) {
            return blueprintStack.isEmpty();
        }
        return this.blueprint.test(blueprintStack);
    }

    /**
     * Checks whether the given ingredient matches the next expected ingredient in sequence.
     */
    public boolean matchesNextIngredient(int stagedCount, ItemStack nextStack) {
        if (stagedCount >= 0 && stagedCount < this.ingredients.size()) {
            return this.ingredients.get(stagedCount).test(nextStack);
        }
        return false;
    }

    /**
     * Validates dual-input workpiece and tool match.
     */
    public boolean matches(ItemStack inputStack, ItemStack toolStack) {
        if (!this.tool.test(toolStack)) return false;
        if (this.ingredients.isEmpty()) return false;
        return this.ingredients.get(0).test(inputStack);
    }

    @Override
    public ItemStack craft(ArtisanRecipeInput input, RegistryWrapper.WrapperLookup registriesLookup) {
        return this.result.copy();
    }

    /**
     * Backward-compatible craft method for SingleStackRecipeInput.
     */
    public ItemStack craft(SingleStackRecipeInput input, RegistryWrapper.WrapperLookup registriesLookup) {
        return this.result.copy();
    }

    @Override
    public boolean fits(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResult(RegistryWrapper.WrapperLookup registriesLookup) {
        return this.result;
    }

    @Override
    public DefaultedList<Ingredient> getIngredients() {
        DefaultedList<Ingredient> list = DefaultedList.of();
        if (!this.blueprint.isEmpty()) {
            list.add(this.blueprint);
        }
        list.addAll(this.ingredients);
        list.add(this.tool);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ARTISAN_CRAFTING_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ARTISAN_CRAFTING_TYPE;
    }

    @Override
    public boolean isIgnoredInRecipeBook() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    /**
     * Dual-channel RecipeSerializer implementation.
     */
    public static class Serializer implements RecipeSerializer<ArtisanCraftingRecipe> {
        public static final MapCodec<ArtisanCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Ingredient.ALLOW_EMPTY_CODEC.optionalFieldOf("blueprint", Ingredient.EMPTY).forGetter(ArtisanCraftingRecipe::blueprint),
                Ingredient.DISALLOW_EMPTY_CODEC.listOf().optionalFieldOf("ingredients", List.of()).xmap(
                    list -> {
                        DefaultedList<Ingredient> dl = DefaultedList.of();
                        dl.addAll(list);
                        return dl;
                    },
                    dl -> dl
                ).forGetter(ArtisanCraftingRecipe::ingredients),
                Ingredient.DISALLOW_EMPTY_CODEC.fieldOf("tool").forGetter(ArtisanCraftingRecipe::tool),
                Codec.INT.optionalFieldOf("required_strikes", 1).forGetter(ArtisanCraftingRecipe::requiredStrikes),
                ItemStack.VALIDATED_CODEC.fieldOf("result").forGetter(ArtisanCraftingRecipe::result),
                Ingredient.ALLOW_EMPTY_CODEC.optionalFieldOf("ingredient", Ingredient.EMPTY).forGetter(r -> Ingredient.EMPTY)
            ).apply(instance, (blueprint, ingredients, tool, requiredStrikes, result, legacyIngredient) -> {
                DefaultedList<Ingredient> finalIngredients = DefaultedList.of();
                if (!ingredients.isEmpty()) {
                    finalIngredients.addAll(ingredients);
                } else if (!legacyIngredient.isEmpty()) {
                    finalIngredients.add(legacyIngredient);
                }
                return new ArtisanCraftingRecipe(blueprint, finalIngredients, tool, requiredStrikes, result);
            })
        );

        public static final PacketCodec<RegistryByteBuf, ArtisanCraftingRecipe> PACKET_CODEC = PacketCodec.tuple(
            Ingredient.PACKET_CODEC, ArtisanCraftingRecipe::blueprint,
            PacketCodecs.collection(DefaultedList::ofSize, Ingredient.PACKET_CODEC), ArtisanCraftingRecipe::ingredients,
            Ingredient.PACKET_CODEC, ArtisanCraftingRecipe::tool,
            PacketCodecs.VAR_INT, ArtisanCraftingRecipe::requiredStrikes,
            ItemStack.PACKET_CODEC, ArtisanCraftingRecipe::result,
            ArtisanCraftingRecipe::new
        );

        @Override
        public MapCodec<ArtisanCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, ArtisanCraftingRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
