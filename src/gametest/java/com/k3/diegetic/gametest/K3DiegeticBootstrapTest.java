package com.k3.diegetic.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * Minimal bootstrap GameTest validating the Fabric GameTest headless execution harness,
 * environment readiness, and engine tick progress for k3_diegetic.
 */
public class K3DiegeticBootstrapTest implements FabricGameTest {

	/**
	 * Asserts that the headless test server initializes successfully,
	 * loads the empty 8x8 structure from fabric-gametest-api-v1,
	 * validates relative coordinate (0, 1, 0) above the structure block is air,
	 * and advances game ticks cleanly across 5 engine ticks.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void testEnvironmentBootstrap(TestContext context) {
		context.expectBlock(Blocks.AIR, 0, 1, 0);
		context.waitAndRun(5, () -> {
			context.expectBlock(Blocks.AIR, 0, 1, 0);
			context.complete();
		});
	}
}

