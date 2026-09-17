package com.k3.diegetic.component;

import com.k3.diegetic.K3DiegeticMod;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.function.UnaryOperator;

/**
 * Data Component Type registration for K3 Diegetic Workstation.
 * Registers custom components into Registries.DATA_COMPONENT_TYPE.
 *
 * Fabric 1.21.1 Yarn mappings:
 * - Registry: Registries.DATA_COMPONENT_TYPE
 * - Component Class: ComponentType<T>
 * - Builder: ComponentType.<T>builder().codec(CODEC).packetCodec(PACKET_CODEC).cache().build()
 */
public class ModDataComponentTypes {

    /**
     * Primary workstation state component tracking recipe progression, tool strikes,
     * thermal state, and active status.
     */
    public static final ComponentType<WorkstationStateComponent> WORKSTATION_STATE = register(
            "workstation_state",
            builder -> builder
                    .codec(WorkstationStateComponent.CODEC)
                    .packetCodec(WorkstationStateComponent.PACKET_CODEC)
                    .cache()
    );

    private static <T> ComponentType<T> register(String name, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        Identifier id = K3DiegeticMod.id(name);
        ComponentType.Builder<T> builder = ComponentType.builder();
        return Registry.register(Registries.DATA_COMPONENT_TYPE, id, builderOperator.apply(builder).build());
    }

    /**
     * Called during ModInitializer#onInitialize to trigger static classloading and component registration.
     */
    public static void registerDataComponentTypes() {
        K3DiegeticMod.LOGGER.info("Registered modern Data Component Types for {}", K3DiegeticMod.MOD_ID);
    }

    /**
     * Alias for registerDataComponentTypes for concise caller invocation.
     */
    public static void register() {
        registerDataComponentTypes();
    }
}
