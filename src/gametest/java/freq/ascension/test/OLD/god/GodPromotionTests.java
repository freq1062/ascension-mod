package freq.ascension.test.god;

import freq.ascension.managers.AscensionData;
import freq.ascension.managers.GodManager;
import freq.ascension.orders.Flora;
import freq.ascension.orders.Ocean;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Integration tests for god promotion/demotion mechanics, covering rank setting,
 * slot gating, demotion cooldowns, single-god-per-order enforcement, and death cleanup.
 */
public class GodPromotionTests {

    /**
     * Uses /setrank player god; asserts player.isGod() returns true.
     */
    @GameTest
    public void playerPromotedToGodWithSetrank(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        GodManager gm = GodManager.get(server);
        AscensionData data = (AscensionData) player;

        TestHelper.setGodDirect(helper, player, "sky");
        boolean promoted = gm.isGod(player)
                && "god".equals(data.getRank())
                && "sky".equals(data.getGodOrder());

        gm.demoteFromGod(player, server);
        if (promoted) {
            helper.succeed();
        } else {
            helper.fail("Expected /setrank god to promote the player to sky god");
        }
    }

    /**
     * Promotes player to god; attempts to equip a 4th ability slot;
     * asserts the action is blocked with an appropriate message.
     */
    @GameTest
    public void godSlotGatingBlocksAdditionalAbilityEquip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        GodManager gm = GodManager.get(server);
        AscensionData data = (AscensionData) player;

        TestHelper.setGodDirect(helper, player, "magic");
        TestHelper.equip(helper, player, "divine", "ocean");

        boolean blocked = "magic".equals(data.getPassive() != null ? data.getPassive().getOrderName() : null)
                && "magic".equals(data.getUtility() != null ? data.getUtility().getOrderName() : null)
                && "magic".equals(data.getCombat() != null ? data.getCombat().getOrderName() : null);

        gm.demoteFromGod(player, server);
        if (blocked) {
            helper.succeed();
        } else {
            helper.fail("Expected invalid fourth-slot equip attempt to be blocked for a god player");
        }
    }

    /**
     * Promotes then demotes player; immediately attempts re-promotion;
     * asserts demotion cooldown prevents re-promotion.
     */
    @GameTest
    public void demotionCooldownActiveRightAfterDemotion(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        GodManager gm = GodManager.get(server);

        gm.promoteToGod(player, Ocean.INSTANCE, server);
        gm.demoteFromGod(player, server);

        if (gm.isOnDemotionCooldown(player) && gm.getDemotionCooldownRemainingMs(player) > 0) {
            helper.succeed();
        } else {
            helper.fail("Expected demotion cooldown to be active immediately after demotion");
        }
    }

    /**
     * Sets demotion expiry timestamp to the past;
     * checks isDemotionCooldownActive; asserts false.
     */
    @GameTest
    public void demotionCooldownExpiresAndAllowsRepromotion(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GodManager gm = GodManager.createForTesting();

        gm.setDemotionCooldownForTesting(player.getUUID(), System.currentTimeMillis() - 1L);
        if (!gm.isOnDemotionCooldown(player) && gm.getDemotionCooldownRemainingMs(player) == 0L) {
            helper.succeed();
        } else {
            helper.fail("Expected expired demotion cooldown to report inactive");
        }
    }

    /**
     * Promotes player A to ocean god; promotes player B to ocean god;
     * asserts player A is demoted OR player B is blocked (only one god per order allowed).
     */
    @GameTest
    public void singleGodPerOrderEnforcedOnPromotion(GameTestHelper helper) {
        ServerPlayer playerA = helper.makeMockServerPlayerInLevel();
        ServerPlayer playerB = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        GodManager gm = GodManager.get(server);
        AscensionData dataA = (AscensionData) playerA;
        AscensionData dataB = (AscensionData) playerB;

        gm.promoteToGod(playerA, Ocean.INSTANCE, server);
        gm.promoteToGod(playerB, Ocean.INSTANCE, server);

        boolean enforced = !gm.isGod(playerA)
                && gm.isGod(playerB)
                && "demigod".equals(dataA.getRank())
                && "god".equals(dataB.getRank())
                && playerB.getUUID().equals(gm.getGodUUID("ocean"));

        gm.demoteFromGod(playerB, server);
        if (enforced) {
            helper.succeed();
        } else {
            helper.fail("Expected only one current ocean god after promoting a replacement");
        }
    }

    /**
     * Promotes player to god; kills player;
     * asserts the god manager entry is cleared.
     */
    @GameTest
    public void godDeathClearsGodManagerEntry(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        GodManager gm = GodManager.get(server);

        gm.promoteToGod(player, Flora.INSTANCE, server);
        player.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);

        helper.runAfterDelay(20, () -> {
            if (!gm.isGod(player) && gm.getGodUUID("flora") == null) {
                helper.succeed();
            } else {
                helper.fail("Expected god death cleanup to clear the flora god entry");
            }
        });
    }
}
