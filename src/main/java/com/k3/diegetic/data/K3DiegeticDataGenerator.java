package com.k3.diegetic.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric Data Generation entrypoint for K3 Diegetic Workstation.
 * Programmatically generates recipe JSONs, models, blockstates, tags, and language files.
 */
public class K3DiegeticDataGenerator implements DataGeneratorEntrypoint {
    public static final Logger DATA_LOGGER = LoggerFactory.getLogger("k3_diegetic/datagen");

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        DATA_LOGGER.info("Starting K3 Diegetic Data Generation process.");
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

        // Providers registered in subsequent milestones:
        // pack.addProvider(K3DiegeticModelProvider::new);
        // pack.addProvider(K3DiegeticRecipeProvider::new);
        // pack.addProvider(K3DiegeticBlockTagProvider::new);
        // pack.addProvider(K3DiegeticItemTagProvider::new);
        
        DATA_LOGGER.info("K3 Diegetic Data Generation providers configured.");
    }
}
