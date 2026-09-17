package com.k3.diegetic.client;

import com.k3.diegetic.K3DiegeticMod;
import com.k3.diegetic.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated client-only entrypoint for K3 Diegetic Workstation.
 * Isolated into src/client/java to prevent dedicated server crashes.
 */
public class K3DiegeticClient implements ClientModInitializer {
    public static final Logger CLIENT_LOGGER = LoggerFactory.getLogger(K3DiegeticMod.MOD_ID + "/client");

    @Override
    public void onInitializeClient() {
        CLIENT_LOGGER.info("Initializing K3 Diegetic Workstation client environment.");
        
        BlockEntityRendererFactories.register(
                ModBlocks.ARTISAN_ANVIL_BLOCK_ENTITY,
                ArtisanAnvilBlockEntityRenderer::new
        );
    }
}
