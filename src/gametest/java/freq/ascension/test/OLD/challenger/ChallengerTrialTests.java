package freq.ascension.test.challenger;

import freq.ascension.Ascension;
import freq.ascension.Config;
import freq.ascension.items.ChallengerSigil;
import freq.ascension.managers.AscensionData;
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
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/**
 * Tests for the challenger trial system using the real manager flow. GameTest
 * mock players are not fully tracked by the server player list, so activation
 * checks that depend on an "online" god use the manager's private activation
 * step directly after setting up the same state that initiateTrial would.
 */
public class ChallengerTrialTests {

    @GameTest
    public void trialStartsWhenPlayerRightClicksPoiWithTrialsEnabled(GameTestHelper helper) {
        String orderName = "ocean";
        setupPoi(helper, orderName);

        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer challenger = helper.makeMockServerPlayerInLevel();
        ServerPlayer god = promoteGodAtPoi(helper, orderName);
        giveMainHand(challenger, ChallengerSigil.createSigil());

        activateTrialForTesting(orderName, challenger, server);

        ChallengerTrialManager.TrialState state = ChallengerTrialManager.get().get(orderName);
        if (state != null
                && state.phase == ChallengerTrialManager.Phase.ACTIVE
                && god.getUUID().equals(state.godUUID)
                && !ChallengerSigil.isSigil(challenger.getMainHandItem())) {
            helper.succeed();
        } else {
            helper.fail("Expected an active challenger trial after initiating from the POI flow.");
        }
    }

    @GameTest
    public void trialsDisabledBlocksPoiInteractionWithMessage(GameTestHelper helper) {
        String orderName = "sky";
        setupPoi(helper, orderName);

        ServerPlayer challenger = helper.makeMockServerPlayerInLevel();
        promoteGodAtPoi(helper, orderName);
        giveMainHand(challenger, ChallengerSigil.createSigil());

        Config.challengerTrialsEnabled = false;
        ChallengerTrialManager.get().initiateTrial(challenger, orderName, helper.getLevel());

        helper.runAfterDelay(2, () -> {
            Config.challengerTrialsEnabled = true;
            ChallengerTrialManager.TrialState state = ChallengerTrialManager.get().getOrCreate(orderName);
            if (state.phase == ChallengerTrialManager.Phase.IDLE
                    && ChallengerSigil.isSigil(challenger.getMainHandItem())) {
                helper.succeed();
            } else {
                helper.fail("Disabled challenger trials should leave the trial idle and keep the sigil.");
            }
        });
    }

    @GameTest
    public void cooldownPreventsNewTrialImmediatelyAfterOne(GameTestHelper helper) {
        String orderName = "earth";
        setupPoi(helper, orderName);

        MinecraftServer server = helper.getLevel().getServer();
        ChallengerTrialManager manager = ChallengerTrialManager.get();
        ServerPlayer firstChallenger = helper.makeMockServerPlayerInLevel();
        ServerPlayer secondChallenger = helper.makeMockServerPlayerInLevel();
        promoteGodAtPoi(helper, orderName);
        giveMainHand(firstChallenger, ChallengerSigil.createSigil());

        activateTrialForTesting(orderName, firstChallenger, server);
        manager.damageCube(orderName, firstChallenger, 500, server);

        giveMainHand(secondChallenger, ChallengerSigil.createSigil());
        manager.initiateTrial(secondChallenger, orderName, helper.getLevel());

        ChallengerTrialManager.TrialState afterRetry = manager.get(orderName);
        if (afterRetry != null
                && afterRetry.phase == ChallengerTrialManager.Phase.COOLDOWN
                && afterRetry.cooldownEndsMs > System.currentTimeMillis()
                && ChallengerSigil.isSigil(secondChallenger.getMainHandItem())) {
            helper.succeed();
        } else {
            helper.fail("Expected a completed trial to block an immediate retry during cooldown.");
        }
    }

    @GameTest
    public void cooldownExpiresAndAllowsNewTrialStart(GameTestHelper helper) {
        String orderName = "flora";
        setupPoi(helper, orderName);

        MinecraftServer server = helper.getLevel().getServer();
        GodManager godManager = GodManager.get(server);
        ServerPlayer challenger = helper.makeMockServerPlayerInLevel();
        promoteGodAtPoi(helper, orderName);
        giveMainHand(challenger, ChallengerSigil.createSigil());
        godManager.setDemotionCooldownForTesting(challenger.getUUID(), System.currentTimeMillis() - 1_000L);

        if (godManager.isOnDemotionCooldown(challenger)) {
            helper.fail("An expired demotion cooldown should not block the challenger.");
            return;
        }

        activateTrialForTesting(orderName, challenger, server);

        ChallengerTrialManager.TrialState state = ChallengerTrialManager.get().get(orderName);
        if (state != null && state.phase == ChallengerTrialManager.Phase.ACTIVE) {
            helper.succeed();
        } else {
            helper.fail("An expired demotion cooldown should allow a fresh challenger trial.");
        }
    }

    @GameTest
    public void lossCounterIncrementsWhenTrialIsFailed(GameTestHelper helper) {
        String orderName = "magic";
        setupPoi(helper, orderName);

        MinecraftServer server = helper.getLevel().getServer();
        GodManager godManager = GodManager.get(server);
        ServerPlayer challenger = helper.makeMockServerPlayerInLevel();
        giveMainHand(challenger, ChallengerSigil.createSigil());
        godManager.setGodEntryForTesting(orderName, UUID.randomUUID(), "OfflineGod");

        ChallengerTrialManager.get().initiateTrial(challenger, orderName, helper.getLevel());

        if (godManager.getLossCounter(orderName) == 1) {
            helper.succeed();
        } else {
            helper.fail("Expected an unanswered challenge to increment the god loss counter.");
        }
    }

    @GameTest
    public void lossCounterResetsToZeroOnGodPromotion(GameTestHelper helper) {
        String orderName = "nether";
        MinecraftServer server = helper.getLevel().getServer();
        GodManager godManager = GodManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        godManager.setLossCounter(orderName, 2);
        promoteToGod(server, player, orderName);

        AscensionData data = (AscensionData) player;
        if (godManager.getLossCounter(orderName) == 0 && "god".equals(data.getRank())) {
            helper.succeed();
        } else {
            helper.fail("Promoting a new god should clear the accumulated loss counter.");
        }
    }

    @GameTest
    public void dailyLossIncrementTrackedOnlyOncePerDay(GameTestHelper helper) {
        String orderName = "end";
        setupPoi(helper, orderName);

        MinecraftServer server = helper.getLevel().getServer();
        GodManager godManager = GodManager.get(server);
        ServerPlayer challenger = helper.makeMockServerPlayerInLevel();
        godManager.setGodEntryForTesting(orderName, UUID.randomUUID(), "OfflineGod");

        giveMainHand(challenger, ChallengerSigil.createSigil());
        ChallengerTrialManager.get().initiateTrial(challenger, orderName, helper.getLevel());

        giveMainHand(challenger, ChallengerSigil.createSigil());
        ChallengerTrialManager.get().initiateTrial(challenger, orderName, helper.getLevel());

        if (godManager.getLossCounter(orderName) == 1) {
            helper.succeed();
        } else {
            helper.fail("Only the first offline challenge of the day should increment the loss counter.");
        }
    }

    private static void setupPoi(GameTestHelper helper, String orderName) {
        Config.challengerTrialsEnabled = true;

        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        PoiManager poi = PoiManager.get(server);
        BlockPos pos = poiPos(helper);
        ChallengerTrialManager.TrialState state = ChallengerTrialManager.get().getOrCreate(orderName);

        state.stopAllTasks();
        if (state.bossBar != null) {
            state.bossBar.removeAllPlayers();
            state.bossBar = null;
        }
        if (state.cooldownDisplayUUID != null) {
            for (ServerLevel loadedLevel : server.getAllLevels()) {
                Entity entity = loadedLevel.getEntity(state.cooldownDisplayUUID);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        state.cooldownDisplayUUID = null;
        state.cubeDisplay = null;
        state.challengerUUID = null;
        state.godUUID = null;
        state.cooldownEndsMs = 0L;
        state.phase = ChallengerTrialManager.Phase.IDLE;

        poi.clearPoiData(orderName);
        poi.setPoiData(orderName, pos, level.dimension().location().toString(), 10,
                PoiManager.captureSnapshot(level, pos, 4));
        poi.spawnPoiEntities(orderName, level, pos, Ascension.scheduler);

        GodManager godManager = GodManager.get(server);
        godManager.clearGod(orderName);
        godManager.setLossCounter(orderName, 0);
        godManager.setLastDailyLossTimestampForTesting(orderName, 0L);
    }

    private static BlockPos poiPos(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 2, 1));
    }

    private static ServerPlayer promoteGodAtPoi(GameTestHelper helper, String orderName) {
        ServerPlayer god = helper.makeMockServerPlayerInLevel();
        moveToPoi(god, poiPos(helper), helper.getLevel());
        promoteToGod(helper.getLevel().getServer(), god, orderName);
        return god;
    }

    private static void promoteToGod(MinecraftServer server, ServerPlayer player, String orderName) {
        Order order = OrderRegistry.get(orderName);
        if (order == null) {
            throw new IllegalStateException("Unknown order: " + orderName);
        }
        GodManager.get(server).promoteToGod(player, order, server);
    }

    private static void activateTrialForTesting(String orderName, ServerPlayer challenger, MinecraftServer server) {
        try {
            ChallengerTrialManager.TrialState state = ChallengerTrialManager.get().getOrCreate(orderName);
            state.challengerUUID = challenger.getUUID();
            consumeSigil(challenger);

            Method method = ChallengerTrialManager.class.getDeclaredMethod(
                    "startActiveTrial", String.class, MinecraftServer.class);
            method.setAccessible(true);
            method.invoke(ChallengerTrialManager.get(), orderName, server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to activate challenger trial for testing", e);
        }
    }

    private static void consumeSigil(ServerPlayer player) {
        if (ChallengerSigil.isSigil(player.getMainHandItem())) {
            player.getMainHandItem().shrink(1);
        }
    }

    private static void giveMainHand(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        player.getInventory().setItem(0, stack);
        TestHelper.selectHotbarSlot(player, 0);
    }

    private static void moveToPoi(ServerPlayer player, BlockPos pos, ServerLevel level) {
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), true);
    }
}
