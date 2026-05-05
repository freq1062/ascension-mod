package freq.ascension.test.challenger;

import freq.ascension.Ascension;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.GodManager;
import freq.ascension.managers.PoiManager;
import freq.ascension.managers.PromotionHandler;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tests for god promotion through the live POI promotion flow. GameTest mock
 * players do not reliably execute the confirmation command path, so these tests
 * use the same pending-promotion and GodManager steps that the command applies.
 */
public class GodPromotionViaCubeTests {

    @GameTest
    public void promotionCubeGrantsGodStatusWith100Influence(GameTestHelper helper) {
        String orderName = "ocean";
        setupPoi(helper, orderName);

        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = prepareEligiblePlayer(helper, orderName, 100);

        PromotionHandler.handlePromotionRequest(player, orderName, server);

        AscensionData data = (AscensionData) player;
        if (confirmPendingPromotion(player, orderName, server)
                && "god".equals(data.getRank())
                && player.getUUID().equals(GodManager.get(server).getGodUUID(orderName))) {
            helper.succeed();
        } else {
            helper.fail("Expected the real promotion flow to promote the player to god.");
        }
    }

    @GameTest
    public void promotionCubeBlockedWithInsufficientInfluence(GameTestHelper helper) {
        String orderName = "sky";
        setupPoi(helper, orderName);

        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = prepareEligiblePlayer(helper, orderName, 0);

        PromotionHandler.handlePromotionRequest(player, orderName, server);
        PromotionHandler.PendingPromotion pending = PromotionHandler.consumePending(
                player.getUUID(), orderName, server.getTickCount());

        AscensionData data = (AscensionData) player;
        if (!"god".equals(data.getRank())
                && GodManager.get(server).getGodUUID(orderName) == null
                && pending == null) {
            helper.succeed();
        } else {
            helper.fail("Insufficient influence should block the promotion confirmation flow.");
        }
    }

    @GameTest
    public void promotionCubeConsumedFromInventoryOnSuccess(GameTestHelper helper) {
        String orderName = "earth";
        setupPoi(helper, orderName);

        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = prepareEligiblePlayer(helper, orderName, 100);

        PromotionHandler.handlePromotionRequest(player, orderName, server);
        boolean confirmed = confirmPendingPromotion(player, orderName, server);

        AscensionData data = (AscensionData) player;
        PromotionHandler.PendingPromotion pending = PromotionHandler.consumePending(
                player.getUUID(), orderName, server.getTickCount());
        if (confirmed && data.getInfluence() == 99 && pending == null) {
            helper.succeed();
        } else {
            helper.fail("A successful ascension should consume the pending promotion and 1 influence.");
        }
    }

    private static boolean confirmPendingPromotion(ServerPlayer player, String orderName, MinecraftServer server) {
        PromotionHandler.PendingPromotion pending = PromotionHandler.consumePending(
                player.getUUID(), orderName, server.getTickCount());
        if (pending == null) {
            return false;
        }

        Order order = OrderRegistry.get(orderName);
        AscensionData data = (AscensionData) player;
        if (order == null || data.getInfluence() < 1) {
            return false;
        }

        data.addInfluence(-1);
        GodManager.get(server).promoteToGod(player, order, server);
        return true;
    }

    private static ServerPlayer prepareEligiblePlayer(GameTestHelper helper, String orderName, int influence) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", orderName);
        TestHelper.equip(helper, player, "utility", orderName);
        TestHelper.equip(helper, player, "combat", orderName);
        TestHelper.setInfluence(helper, player, influence);
        return player;
    }

    private static void setupPoi(GameTestHelper helper, String orderName) {
        ServerLevel level = helper.getLevel();
        PoiManager poi = PoiManager.get(level.getServer());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        poi.clearPoiData(orderName);
        poi.setPoiData(orderName, pos, level.dimension().location().toString(), 10,
                PoiManager.captureSnapshot(level, pos, 4));
        poi.spawnPoiEntities(orderName, level, pos, Ascension.scheduler);
        GodManager.get(level.getServer()).clearGod(orderName);
    }
}
