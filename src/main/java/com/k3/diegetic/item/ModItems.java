package com.k3.diegetic.item;

import com.k3.diegetic.K3DiegeticMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Registration catalog for custom items in K3 Diegetic Workstation.
 */
public class ModItems {

    /**
     * Dedicated smithing tool for physical diegetic anvil crafting.
     * Has 250 durability and fits in the Tools creative tab.
     */
    public static final Item FORGING_HAMMER = Registry.register(
            Registries.ITEM,
            K3DiegeticMod.id("forging_hammer"),
            new Item(new Item.Settings().maxDamage(250))
    );

    /**
     * Blueprint item for forging Iron Swords (2 Iron Ingots + 1 Stick).
     */
    public static final BlueprintItem SWORD_BLUEPRINT = Registry.register(
            Registries.ITEM,
            K3DiegeticMod.id("sword_blueprint"),
            new BlueprintItem(
                    new Item.Settings().maxCount(16),
                    Text.translatable("item.minecraft.iron_sword"),
                    List.of(
                            Text.translatable("item.minecraft.iron_ingot").append(" (x2)"),
                            Text.translatable("item.minecraft.stick").append(" (x1)")
                    ),
                    Text.translatable("item.k3_diegetic.forging_hammer")
            )
    );

    /**
     * Blueprint item for forging Iron Pickaxes (3 Iron Ingots + 2 Sticks).
     */
    public static final BlueprintItem PICKAXE_BLUEPRINT = Registry.register(
            Registries.ITEM,
            K3DiegeticMod.id("pickaxe_blueprint"),
            new BlueprintItem(
                    new Item.Settings().maxCount(16),
                    Text.translatable("item.minecraft.iron_pickaxe"),
                    List.of(
                            Text.translatable("item.minecraft.iron_ingot").append(" (x3)"),
                            Text.translatable("item.minecraft.stick").append(" (x2)")
                    ),
                    Text.translatable("item.k3_diegetic.forging_hammer")
            )
    );

    /**
     * Blueprint item for forging Iron Axes (3 Iron Ingots + 2 Sticks).
     */
    public static final BlueprintItem AXE_BLUEPRINT = Registry.register(
            Registries.ITEM,
            K3DiegeticMod.id("axe_blueprint"),
            new BlueprintItem(
                    new Item.Settings().maxCount(16),
                    Text.translatable("item.minecraft.iron_axe"),
                    List.of(
                            Text.translatable("item.minecraft.iron_ingot").append(" (x3)"),
                            Text.translatable("item.minecraft.stick").append(" (x2)")
                    ),
                    Text.translatable("item.k3_diegetic.forging_hammer")
            )
    );

    public static void registerModItems() {
        K3DiegeticMod.LOGGER.info("Registering ModItems for {}", K3DiegeticMod.MOD_ID);

        // Add items to Tools creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(FORGING_HAMMER);
            entries.add(SWORD_BLUEPRINT);
            entries.add(PICKAXE_BLUEPRINT);
            entries.add(AXE_BLUEPRINT);
        });

        // Add blueprints to Functional creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(SWORD_BLUEPRINT);
            entries.add(PICKAXE_BLUEPRINT);
            entries.add(AXE_BLUEPRINT);
        });
    }
}
