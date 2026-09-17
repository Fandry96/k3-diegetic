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

/**
 * Modern Fabric 1.21.1 custom recipe representing physical artisan workstation crafting.
 * Combines an in-world workpiece ingredient with a handheld strike tool over N strikes.
 *
 * Implements Recipe<SingleStackRecipeInput> where the input is the workpiece placed on the anvil.
 * Dual-channel serialization via RecordCodecBuilder (JSON MapCodec) and PacketCodec (binary Netty).
 */
public class ArtisanCraftingRecipe implements Recipe<SingleStackRecipeInput> {
    private final Ingredient ingredient;
    private final Ingredient tool;
    private final int requiredStrikes;
    private final ItemStack result;

    public ArtisanCraftingRecipe(Ingredient ingredient, Ingredient tool, int requiredStrikes, ItemStack result) {
        this.ingredient = ingredient;
        this.tool = tool;
        this.requiredStrikes = Math.max(1, requiredStrikes);
        this.result = result;
    }

    public Ingredient ingredient() {
        return this.ingredient;
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
    public boolean matches(SingleStackRecipeInput input, World world) {
        return this.ingredient.test(input.item());
    }

    /**
     * Checks whether the held tool matches the required striking tool for this recipe.
     */
    public boolean matchesTool(ItemStack toolStack) {
        return this.tool.test(toolStack);
    }

    /**
     * Complete dual-input match validation for both workpiece and held tool.
     */
    public boolean matches(ItemStack inputStack, ItemStack toolStack) {
        return this.ingredient.test(inputStack) && this.tool.test(toolStack);
    }

    @Override
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
        list.add(this.ingredient);
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
                Ingredient.DISALLOW_EMPTY_CODEC.fieldOf("ingredient").forGetter(ArtisanCraftingRecipe::ingredient),
                Ingredient.DISALLOW_EMPTY_CODEC.fieldOf("tool").forGetter(ArtisanCraftingRecipe::tool),
                Codec.INT.optionalFieldOf("required_strikes", 1).forGetter(ArtisanCraftingRecipe::requiredStrikes),
                ItemStack.VALIDATED_CODEC.fieldOf("result").forGetter(ArtisanCraftingRecipe::result)
            ).apply(instance, ArtisanCraftingRecipe::new)
        );

        public static final PacketCodec<RegistryByteBuf, ArtisanCraftingRecipe> PACKET_CODEC = PacketCodec.tuple(
            Ingredient.PACKET_CODEC, ArtisanCraftingRecipe::ingredient,
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
