package com.k3.diegetic.item;

import com.k3.diegetic.K3DiegeticMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

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

    public static void registerModItems() {
        K3DiegeticMod.LOGGER.info("Registering ModItems for {}", K3DiegeticMod.MOD_ID);

        // Add Forging Hammer to Tools creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(FORGING_HAMMER);
        });
    }
}
