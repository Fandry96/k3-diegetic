package com.k3.diegetic.block;

import com.k3.diegetic.K3DiegeticMod;
import com.k3.diegetic.block.entity.ArtisanAnvilBlockEntity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Registration catalog for K3 Diegetic Workstation blocks, block entities, and items.
 * Target: Fabric 1.21.1 (Yarn mappings).
 */
public class ModBlocks {

    /**
     * Physical diegetic crafting station block.
     * Non-opaque, heavy anvil acoustics, blast-resistant, piston-immovable.
     */
    public static final Block ARTISAN_ANVIL = registerBlock(
            "artisan_anvil",
            new ArtisanAnvilBlock(
                    AbstractBlock.Settings.create()
                            .mapColor(MapColor.IRON_GRAY)
                            .requiresTool()
                            .strength(5.0f, 1200.0f)
                            .sounds(BlockSoundGroup.ANVIL)
                            .nonOpaque()
                            .pistonBehavior(PistonBehavior.BLOCK)
            )
    );

    /**
     * BlockEntityType for ArtisanAnvilBlockEntity.
     * Uses Fabric-extended BlockEntityType.Builder.create(...) idiom.
     */
    public static final BlockEntityType<ArtisanAnvilBlockEntity> ARTISAN_ANVIL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            K3DiegeticMod.id("artisan_anvil"),
            BlockEntityType.Builder.create(ArtisanAnvilBlockEntity::new, ARTISAN_ANVIL).build()
    );

    /**
     * Helper to register Block and corresponding BlockItem.
     */
    private static Block registerBlock(String name, Block block) {
        Identifier id = K3DiegeticMod.id(name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    /**
     * Called from K3DiegeticMod#onInitialize to trigger classloading and registration.
     */
    public static void registerModBlocks() {
        K3DiegeticMod.LOGGER.info("Registering ModBlocks and BlockEntities for {}", K3DiegeticMod.MOD_ID);

        // Add Artisan Anvil to Functional Blocks creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(ARTISAN_ANVIL);
        });
    }

    /**
     * Alias for registerModBlocks.
     */
    public static void register() {
        registerModBlocks();
    }
}
