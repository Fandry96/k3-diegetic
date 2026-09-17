package com.k3.diegetic.client;

import com.k3.diegetic.K3DiegeticMod;
import net.fabricmc.api.ClientModInitializer;
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
        
        // Milestone 4: Client render layer registrations, entity rendering layers,
        // particle factories, or custom block model mappings.
    }
}
