package com.k3.diegetic;

import com.k3.diegetic.block.ModBlocks;
import com.k3.diegetic.component.ModDataComponentTypes;
import com.k3.diegetic.recipe.ModRecipes;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for K3 Diegetic Workstation.
 * Initializes Data Components, Blocks, Block Entities, and custom recipe serializers.
 */
public class K3DiegeticMod implements ModInitializer {
    public static final String MOD_ID = "k3_diegetic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing K3 Diegetic Workstation (modid: {})", MOD_ID);
        
        // Milestone 3: Data Component Types
        ModDataComponentTypes.registerDataComponentTypes();
        
        // Milestone 4: ModBlocks & ModRecipes
        ModBlocks.registerModBlocks();
        ModRecipes.registerModRecipes();
        
        LOGGER.info("K3 Diegetic Workstation common initialization complete.");
    }

    /**
     * Helper factory for creating namespaced Identifiers in Minecraft 1.21+.
     * Uses the modern Identifier.of factory method.
     */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
