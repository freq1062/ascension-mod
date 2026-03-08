package freq.ascension.test.challenger;

import freq.ascension.managers.PoiManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Set;

/**
 * GameTest suite for {@link PoiManager} — covering POI data storage, radius handling,
 * terrain snapshot capture and reset.
 *
 * <p>All tests that require persistent state use unique synthetic order name keys to prevent
 * cross-test pollution within the shared server-side {@link PoiManager} instance.
 *
 * <p><b>Invariants verified:</b>
 * <ul>
 *   <li>Position stored via {@link PoiManager#setPoiData} is retrievable via
 *       {@link PoiManager#getPoiPosition}.</li>
 *   <li>Default radius is 10 when not explicitly set.</li>
 *   <li>Custom radii are stored and retrieved exactly (setter does not clamp).</li>
 *   <li>POI data for different orders is fully independent.</li>
 *   <li>{@link PoiManager#captureSnapshot} excludes air blocks and includes solid blocks.</li>
 *   <li>{@link PoiManager#resetTerrain} restores the snapshotted blocks.</li>
 *   <li>{@link PoiManager#clearPoiData} removes all data for the given order.</li>
 *   <li>{@link PoiManager#getAllPoiOrders} reflects all orders that have been registered.</li>
 * </ul>
 */
public class PoiTests {

    // ─── Position storage ─────────────────────────────────────────────────────

    /**
     * {@link PoiManager#setPoiData} must store the provided {@link BlockPos} and
     * {@link PoiManager#getPoiPosition} must return an equal value.
     */
    @GameTest
    public void poiPositionStoredCorrectly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PoiManager poi = PoiManager.get(server);

        BlockPos pos = new BlockPos(100, 64, 100);
        poi.setPoiData("__poi_pos_test__", pos, "minecraft:overworld", 10, List.of());

        BlockPos result = poi.getPoiPosition("__poi_pos_test__");
        if (!pos.equals(result)) {
            helper.fail("Expected stored BlockPos " + pos + " but got " + result);
        }
        helper.succeed();
    }

    // ─── Radius ───────────────────────────────────────────────────────────────

    /**
     * {@link PoiManager#getPoiRadius} must return 10 for an order that has never had its radius
     * explicitly set — confirming the default value documented in the API.
     */
    @GameTest
    public void poiDefaultRadiusIs10(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PoiManager poi = PoiManager.get(server);

        int radius = poi.getPoiRadius("__never_set_order_poi_default_xyz__");
        if (radius != 10) {
            helper.fail("Default POI radius must be 10, got " + radius);
        }
        helper.succeed();
    }

    /**
     * {@link PoiManager#setPoiRadius} must store and return the given value via
     * {@link PoiManager#getPoiRadius}.
     */
    @GameTest
    public void poiCustomRadiusStored(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PoiManager poi = PoiManager.get(server);

        poi.setPoiRadius("__poi_custom_radius_test__", 15);
        int radius = poi.getPoiRadius("__poi_custom_radius_test__");
        if (radius != 15) {
            helper.fail("Expected stored radius 15 but got " + radius);
        }
        helper.succeed();
    }

    /**
     * {@link PoiManager#setPoiRadius} does <em>not</em> clamp values above 20 in the setter;
     * the raw value is stored. The clamping to 20 happens inside
     * {@link PoiManager#captureSnapshot}, not at storage time.
     */
    @GameTest
    public void poiRadiusSetterDoesNotClamp(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PoiManager poi = PoiManager.get(server);

        poi.setPoiRadius("__poi_clamp_test__", 25);
        int stored = poi.getPoiRadius("__poi_clamp_test__");
        if (stored != 25) {
            helper.fail("setPoiRadius(25) should store 25 without clamping, got " + stored);
        }
        helper.succeed();
    }

    // ─── Order independence ───────────────────────────────────────────────────

    /**
     * Setting POI data for one order must not create or modify data for an unrelated order.
     */
    @GameTest
    public void poiDifferentOrdersAreIndependent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PoiManager poi = PoiManager.get(server);

        BlockPos earthPos = new BlockPos(200, 64, 200);
        poi.setPoiData("__poi_indep_earth__", earthPos, "minecraft:overworld", 10, List.of());

        // The sky order key was never registered — must remain absent
        BlockPos skyPos = poi.getPoiPosition("__poi_indep_sky_unset__");
        if (skyPos != null) {
            helper.fail("Setting earth POI must not create an entry for the sky order");
        }
        helper.succeed();
    }

    // ─── Snapshot capture ─────────────────────────────────────────────────────

    /**
     * {@link PoiManager#captureSnapshot} with radius 0 must include the center block if it is
     * a non-air solid block.
     */
    @GameTest
    public void terrainSnapshotCapturesNonAirBlocks(GameTestHelper helper) {
        BlockPos rel = new BlockPos(0, 1, 0);
        helper.setBlock(rel, Blocks.STONE);
        BlockPos abs = helper.absolutePos(rel);

        List<PoiManager.SnapshotEntry> snapshot =
                PoiManager.captureSnapshot(helper.getLevel(), abs, 0);

        boolean foundStone = snapshot.stream().anyMatch(e -> e.state().is(Blocks.STONE));
        if (!foundStone) {
            helper.fail("captureSnapshot must include the placed STONE block at center (radius=0); snapshot=" + snapshot);
        }
        helper.succeed();
    }

    /**
     * {@link PoiManager#captureSnapshot} must never include air blocks — entries whose
     * {@link PoiManager.SnapshotEntry#state()} is air are invalid and must be absent.
     */
    @GameTest
    public void terrainSnapshotExcludesAirBlocks(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        helper.setBlock(rel, Blocks.AIR);
        BlockPos abs = helper.absolutePos(rel);

        List<PoiManager.SnapshotEntry> snapshot =
                PoiManager.captureSnapshot(helper.getLevel(), abs, 0);

        boolean hasAir = snapshot.stream().anyMatch(e -> e.state().isAir());
        if (hasAir) {
            helper.fail("captureSnapshot must never include air blocks");
        }
        helper.succeed();
    }

    /**
     * After calling {@link PoiManager#resetTerrain}, the center block that was part of the
     * snapshot must be restored to its original state even if it was replaced with a different
     * block between the snapshot and the reset.
     */
    @GameTest
    public void terrainResetRestoresSavedBlocks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PoiManager poi = PoiManager.get(server);

        BlockPos rel = new BlockPos(0, 3, 0);
        helper.setBlock(rel, Blocks.STONE);
        BlockPos abs = helper.absolutePos(rel);

        // Capture snapshot while STONE is present (radius 0 = center only)
        List<PoiManager.SnapshotEntry> snapshot =
                PoiManager.captureSnapshot(helper.getLevel(), abs, 0);

        poi.setPoiData("__poi_terrain_reset__", abs, "minecraft:overworld", 0, snapshot);

        // Replace with DIRT
        helper.setBlock(rel, Blocks.DIRT);

        // Reset terrain — must restore STONE
        poi.resetTerrain("__poi_terrain_reset__", helper.getLevel());

        if (!helper.getLevel().getBlockState(abs).is(Blocks.STONE)) {
            helper.fail("resetTerrain must restore STONE at center, but found: "
                    + helper.getLevel().getBlockState(abs));
        }
        helper.succeed();
    }

    // ─── Clear ────────────────────────────────────────────────────────────────

    /**
     * After {@link PoiManager#clearPoiData}, {@link PoiManager#hasPoiPosition} must return
     * {@code false} for the cleared order.
     */
    @GameTest
    public void poiDataClearedOnClearPoiData(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PoiManager poi = PoiManager.get(server);

        BlockPos pos = new BlockPos(300, 64, 300);
        poi.setPoiData("__poi_clear_test__", pos, "minecraft:overworld", 10, List.of());
        poi.clearPoiData("__poi_clear_test__");

        if (poi.hasPoiPosition("__poi_clear_test__")) {
            helper.fail("hasPoiPosition must return false after clearPoiData");
        }
        helper.succeed();
    }

    // ─── getAllPoiOrders ──────────────────────────────────────────────────────

    /**
     * After registering two orders, {@link PoiManager#getAllPoiOrders} must contain both
     * order keys.
     */
    @GameTest
    public void getAllPoiOrdersReturnsRegisteredOrders(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PoiManager poi = PoiManager.get(server);

        poi.setPoiData("__poi_all_earth__", new BlockPos(400, 64, 400),
                "minecraft:overworld", 10, List.of());
        poi.setPoiData("__poi_all_sky__", new BlockPos(500, 64, 500),
                "minecraft:overworld", 10, List.of());

        Set<String> orders = poi.getAllPoiOrders();
        if (!orders.contains("__poi_all_earth__") || !orders.contains("__poi_all_sky__")) {
            helper.fail("getAllPoiOrders must contain both registered orders; got: " + orders);
        }
        helper.succeed();
    }

    // ─── ChallengerSigil ─────────────────────────────────────────────────────

    /**
     * {@link freq.ascension.items.ChallengerSigil#isSigil} must return {@code true} for a stack
     * produced by {@link freq.ascension.items.ChallengerSigil#createSigil()}.
     */
    @GameTest
    public void challengerSigilIsSigilWithCustomModelData(GameTestHelper helper) {
        net.minecraft.world.item.ItemStack stack = freq.ascension.items.ChallengerSigil.createSigil();
        if (!freq.ascension.items.ChallengerSigil.isSigil(stack)) {
            helper.fail("isSigil must return true for a stack produced by createSigil()");
        }
        helper.succeed();
    }

    /**
     * A plain {@code heart_of_the_sea} (no CustomModelData) must NOT be identified as a sigil.
     */
    @GameTest
    public void challengerSigilNotIdentifiedWithWrongItem(GameTestHelper helper) {
        net.minecraft.world.item.ItemStack plain =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HEART_OF_THE_SEA);
        if (freq.ascension.items.ChallengerSigil.isSigil(plain)) {
            helper.fail("isSigil must return false for a plain heart_of_the_sea with no CMData");
        }
        helper.succeed();
    }

    /**
     * A {@code diamond} ItemStack must NOT be identified as a sigil.
     */
    @GameTest
    public void challengerSigilNotIdentifiedWithDiamond(GameTestHelper helper) {
        net.minecraft.world.item.ItemStack diamond =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        if (freq.ascension.items.ChallengerSigil.isSigil(diamond)) {
            helper.fail("isSigil must return false for a diamond ItemStack");
        }
        helper.succeed();
    }

    /**
     * {@link freq.ascension.items.ChallengerSigil#createSigil()} must produce a
     * {@code heart_of_the_sea} base item with {@code maxStackSize == 1} (non-stackable,
     * so each challenge consumes exactly one sigil).
     */
    @GameTest
    public void challengerSigilCreatesHeartOfTheSeaBase(GameTestHelper helper) {
        net.minecraft.world.item.ItemStack stack = freq.ascension.items.ChallengerSigil.createSigil();
        if (!stack.is(net.minecraft.world.item.Items.HEART_OF_THE_SEA)) {
            helper.fail("createSigil() must produce a heart_of_the_sea base item, got: " + stack);
        }
        if (stack.getMaxStackSize() != 1) {
            helper.fail("createSigil() must produce a non-stackable item (maxStackSize=1), got: "
                    + stack.getMaxStackSize());
        }
        helper.succeed();
    }

    // ─── Rotation task cleanup ────────────────────────────────────────────────

    /**
     * {@link PoiManager#stopAllRotationTasks} must leave no active rotation tasks and must
     * complete without throwing even when no tasks are registered.
     */
    @GameTest
    public void rotationTaskStopsOnServerStopCleanup(GameTestHelper helper) {
        PoiManager poi = PoiManager.get(helper.getLevel().getServer());
        poi.stopAllRotationTasks(helper.getLevel());
        if (poi.hasActiveRotationTasks()) {
            helper.fail("rotationTasks must be empty after stopAllRotationTasks()");
        }
        helper.succeed();
    }
}
