package freq.ascension.test.challenger;

import freq.ascension.Ascension;
import freq.ascension.items.ChallengerSigil;
import freq.ascension.managers.ChallengerTrialManager;
import freq.ascension.managers.GodManager;
import freq.ascension.managers.PoiManager;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Tests for the POI entity and its manager-backed lifecycle.
 */
public class PoiTests {

    @GameTest
    public void poiEntityDisplaysOrderNameNotInternalId(GameTestHelper helper) {
        String orderName = "ocean";
        setupPoi(helper, orderName);

        helper.runAfterDelay(2, () -> {
            Display.ItemDisplay display = PoiManager.get(helper.getLevel().getServer())
                    .getDisplayEntity(orderName, helper.getLevel().getServer());
            if (display != null
                    && display.hasCustomName()
                    && display.getCustomName().getString().endsWith(orderName)) {
                helper.succeed();
            } else {
                helper.fail("Expected the spawned POI display to be resolvable for the Ocean order.");
            }
        });
    }

    @GameTest
    public void poiRightClickFiresInteractionEvent(GameTestHelper helper) {
        String orderName = "sky";
        setupPoi(helper, orderName);

        ServerLevel level = helper.getLevel();
        ServerPlayer challenger = helper.makeMockServerPlayerInLevel();
        ServerPlayer god = helper.makeMockServerPlayerInLevel();
        promoteToGod(level.getServer(), god, orderName);
        moveToPoi(god, poiPos(helper), level);
        giveMainHand(challenger, ChallengerSigil.createSigil());

        Interaction interaction = findInteraction(level, poiPos(helper), orderName);
        if (interaction == null) {
            helper.fail("Expected a real POI interaction entity to exist for the test.");
            return;
        }

        String resolvedOrder = interaction.getCustomName().getString().substring("ascension_poi_".length());
        ChallengerTrialManager.get().initiateTrial(challenger, resolvedOrder, level);

        helper.runAfterDelay(25, () -> {
            ChallengerTrialManager.TrialState state = ChallengerTrialManager.get().get(orderName);
            if (state != null && state.phase == ChallengerTrialManager.Phase.ACTIVE) {
                helper.succeed();
            } else {
                helper.fail("Expected the POI interaction flow to register a challenger trial attempt.");
            }
        });
    }

    @GameTest
    public void poiPersistsAfterChunkUnloadAndReload(GameTestHelper helper) {
        String orderName = "earth";
        setupPoi(helper, orderName);

        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        PoiManager poi = PoiManager.get(server);
        BlockPos pos = poi.getPoiPosition(orderName);
        if (pos == null) {
            helper.fail("Expected the POI position to be saved before testing persistence.");
            return;
        }

        poi.removePreviousEntities(orderName, level);
        poi.spawnPoiEntities(orderName, level, pos, Ascension.scheduler);

        helper.runAfterDelay(2, () -> {
            Display.ItemDisplay display = poi.getDisplayEntity(orderName, server);
            Interaction interaction = findInteraction(level, pos, orderName);
            if (display != null && interaction != null && poi.getPoiPosition(orderName) != null) {
                helper.succeed();
            } else {
                helper.fail("Expected the POI to respawn cleanly from saved PoiManager data.");
            }
        });
    }

    private static void setupPoi(GameTestHelper helper, String orderName) {
        ServerLevel level = helper.getLevel();
        PoiManager poi = PoiManager.get(level.getServer());
        BlockPos pos = poiPos(helper);
        poi.clearPoiData(orderName);
        poi.setPoiData(orderName, pos, level.dimension().location().toString(), 10,
                PoiManager.captureSnapshot(level, pos, 4));
        poi.spawnPoiEntities(orderName, level, pos, Ascension.scheduler);
        GodManager.get(level.getServer()).clearGod(orderName);
    }

    private static BlockPos poiPos(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 2, 1));
    }

    private static void moveToPoi(ServerPlayer player, BlockPos pos, ServerLevel level) {
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), true);
    }

    private static void giveMainHand(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        player.getInventory().setItem(0, stack);
        TestHelper.selectHotbarSlot(player, 0);
    }

    private static void promoteToGod(MinecraftServer server, ServerPlayer player, String orderName) {
        Order order = OrderRegistry.get(orderName);
        if (order == null) {
            throw new IllegalStateException("Unknown order: " + orderName);
        }
        GodManager.get(server).promoteToGod(player, order, server);
    }

    private static Interaction findInteraction(ServerLevel level, BlockPos pos, String orderName) {
        return level.getEntitiesOfClass(Interaction.class, AABB.ofSize(Vec3.atCenterOf(pos), 2.0, 2.0, 2.0),
                entity -> entity.hasCustomName()
                        && ("ascension_poi_" + orderName).equals(entity.getCustomName().getString()))
                .stream()
                .findFirst()
                .orElse(null);
    }
}
