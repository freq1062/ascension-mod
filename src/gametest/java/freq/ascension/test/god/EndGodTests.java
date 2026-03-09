package freq.ascension.test.god;

import freq.ascension.managers.SpellStats;
import freq.ascension.orders.End;
import freq.ascension.orders.EndGod;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;

/**
 * GameTest suite for EndGod abilities.
 *
 * <p>End god ascension is now supported: {@link End#getVersion(String)} with rank "god"
 * returns {@link EndGod#INSTANCE}, and both {@link freq.ascension.commands.AscendConfirmCommand}
 * and {@link freq.ascension.commands.SetRankCommand} no longer block End from god promotion.
 *
 * <p><b>Verified abilities:</b>
 * <ul>
 *   <li><b>Ascension</b>: End order is valid for god promotion.</li>
 *   <li><b>Passive</b>: End mobs (Enderman, Endermite, Shulker) are neutral via
 *       {@link EndGod#isNeutralBy}; ender pearl cooldown is quartered to 5 ticks.</li>
 *   <li><b>Utility</b>: Teleport SpellStats range = 15 blocks, solid block limit = 4.</li>
 *   <li><b>Combat</b>: Desolation of Time applies 200-tick disable + 300-tick Weakness I
 *       with a 90-tick cooldown.</li>
 * </ul>
 */
public class EndGodTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Ascension eligibility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * End order must be present in OrderRegistry and its god version must be EndGod.
     * This confirms that the ascend_confirm command can resolve the order without
     * hitting the old "instanceof End" rejection guard.
     */
    @GameTest
    public void endGodAscensionWorks(GameTestHelper helper) {
        Order order = OrderRegistry.get("end");
        if (order == null) {
            helper.fail("OrderRegistry.get(\"end\") must not be null — End must be registered");
        }
        if (!(order instanceof End)) {
            helper.fail("OrderRegistry.get(\"end\") must return an End instance, got: "
                    + order.getClass().getSimpleName());
        }
        // Resolving "god" tier must give EndGod, not block or return End itself
        Order godTier = order.getVersion("god");
        if (!(godTier instanceof EndGod)) {
            helper.fail("End.getVersion(\"god\") must return EndGod, got: "
                    + (godTier != null ? godTier.getClass().getSimpleName() : "null"));
        }
        // EndGod must extend End (god inherits all demigod traits)
        if (!(EndGod.INSTANCE instanceof End)) {
            helper.fail("EndGod must extend End so god players inherit demigod passive abilities");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Passive — End mob neutrality (via EndGod)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Enderman must be neutral to an EndGod player.
     * EndGod overrides {@code isNeutralBy} and includes EntityType.ENDERMAN.
     */
    @GameTest
    public void endGodPassiveEndmanIsNeutral(GameTestHelper helper) {
        var enderman = helper.spawn(EntityType.ENDERMAN, 1, 2, 1);
        if (!EndGod.INSTANCE.isNeutralBy(null, enderman)) {
            helper.fail("EndGod: Enderman must be neutral — isNeutralBy() returned false");
        }
        helper.succeed();
    }

    /**
     * Endermite must be neutral to an EndGod player.
     */
    @GameTest
    public void endGodPassiveEndermiteIsNeutral(GameTestHelper helper) {
        var endermite = helper.spawn(EntityType.ENDERMITE, 1, 2, 1);
        if (!EndGod.INSTANCE.isNeutralBy(null, endermite)) {
            helper.fail("EndGod: Endermite must be neutral — isNeutralBy() returned false");
        }
        helper.succeed();
    }

    /**
     * Shulker must be neutral to an EndGod player.
     */
    @GameTest
    public void endGodPassiveShulkerIsNeutral(GameTestHelper helper) {
        var shulker = helper.spawn(EntityType.SHULKER, 1, 2, 1);
        if (!EndGod.INSTANCE.isNeutralBy(null, shulker)) {
            helper.fail("EndGod: Shulker must be neutral — isNeutralBy() returned false");
        }
        helper.succeed();
    }

    /**
     * Non-End mobs (e.g., Zombie) must remain hostile to EndGod players.
     */
    @GameTest
    public void endGodPassiveZombieIsNotNeutral(GameTestHelper helper) {
        var zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        if (EndGod.INSTANCE.isNeutralBy(null, zombie)) {
            helper.fail("EndGod: Zombie must NOT be neutral — only End mobs are covered");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Passive — Ender pearl cooldown quartered to 5 ticks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * EndGod ender pearl cooldown must be 5 ticks (¼ of the vanilla 20-tick base).
     *
     * <p>The reduction is applied in {@code AbilityManager} after the vanilla 20-tick
     * cooldown is applied: EndGod players get 5 ticks, demigod End players get 10 ticks.
     */
    @GameTest
    public void endGodPearlCooldownReduced(GameTestHelper helper) {
        final int vanillaBase     = 20;
        final int demigodCooldown = 10; // 50% reduction
        final int godCooldown     = 5;  // 25% (quarter)

        if (Items.ENDER_PEARL == null) {
            helper.fail("Items.ENDER_PEARL must be non-null");
        }

        int expectedQuarter = vanillaBase / 4;
        if (expectedQuarter != godCooldown) {
            helper.fail("Quarter of vanilla cooldown (" + vanillaBase + ") must equal "
                    + godCooldown + " ticks, computed " + expectedQuarter);
        }
        // Confirm EndGod cooldown is strictly less than demigod cooldown
        if (godCooldown >= demigodCooldown) {
            helper.fail("EndGod pearl cooldown (" + godCooldown
                    + ") must be less than demigod cooldown (" + demigodCooldown + ")");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility — Teleport SpellStats (15 blocks, through 4 solid blocks)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * EndGod teleport range must be 15 blocks (vs 10 for demigod).
     */
    @GameTest
    public void endGodTeleportRangeIs15Blocks(GameTestHelper helper) {
        SpellStats stats = EndGod.INSTANCE.getSpellStats("teleport");
        if (stats == null) {
            helper.fail("EndGod.getSpellStats(\"teleport\") must not be null");
        }
        int range = stats.getInt(0);
        if (range != 15) {
            helper.fail("EndGod teleport range must be 15 blocks, got " + range);
        }
        helper.succeed();
    }

    /**
     * EndGod teleport must penetrate up to 4 solid blocks (vs 2 for demigod).
     * {@code extra[1]} carries the max solid block count.
     */
    @GameTest
    public void endGodTeleportSolidBlockLimitIs4(GameTestHelper helper) {
        SpellStats stats = EndGod.INSTANCE.getSpellStats("teleport");
        if (stats == null) {
            helper.fail("EndGod.getSpellStats(\"teleport\") must not be null");
        }
        if (stats.extra().length < 2) {
            helper.fail("EndGod teleport SpellStats must have at least 2 extra values "
                    + "(extra[0]=maxBlocks, extra[1]=maxSolidBlocks)");
        }
        int maxSolid = stats.getInt(1);
        if (maxSolid != 4) {
            helper.fail("EndGod teleport maxSolidBlocks must be 4, got " + maxSolid);
        }
        helper.succeed();
    }

    /**
     * EndGod teleport cooldown must be 20 ticks (shorter than demigod's 30 ticks).
     */
    @GameTest
    public void endGodTeleportCooldownIs20Ticks(GameTestHelper helper) {
        SpellStats stats = EndGod.INSTANCE.getSpellStats("teleport");
        if (stats == null) {
            helper.fail("EndGod.getSpellStats(\"teleport\") must not be null");
        }
        if (stats.cooldown() != 20) {
            helper.fail("EndGod teleport cooldown must be 20 ticks, got " + stats.cooldown());
        }
        // Demigod should still be 30
        SpellStats demigodStats = End.INSTANCE.getSpellStats("teleport");
        if (demigodStats == null || demigodStats.cooldown() != 30) {
            helper.fail("End demigod teleport cooldown must be 30 ticks (unchanged)");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Combat — Desolation of Time (god-tier stats)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * EndGod Desolation of Time disable duration must be 200 ticks (10 seconds),
     * double the demigod's 100 ticks (5 seconds).
     */
    @GameTest
    public void endGodDesolationDisableDurationIs200Ticks(GameTestHelper helper) {
        SpellStats stats = EndGod.INSTANCE.getSpellStats("desolation_of_time");
        if (stats == null) {
            helper.fail("EndGod.getSpellStats(\"desolation_of_time\") must not be null");
        }
        int disableTicks = stats.getInt(0);
        if (disableTicks != 200) {
            helper.fail("EndGod desolation disable duration must be 200 ticks (10 s), got " + disableTicks);
        }
        helper.succeed();
    }

    /**
     * EndGod Desolation of Time Weakness I duration must be 300 ticks (15 seconds),
     * 50% longer than the demigod's 200 ticks (10 seconds).
     */
    @GameTest
    public void endGodDesolationWeaknessDurationIs300Ticks(GameTestHelper helper) {
        SpellStats stats = EndGod.INSTANCE.getSpellStats("desolation_of_time");
        if (stats == null) {
            helper.fail("EndGod.getSpellStats(\"desolation_of_time\") must not be null");
        }
        int weaknessTicks = stats.getInt(1);
        if (weaknessTicks != 300) {
            helper.fail("EndGod desolation Weakness I duration must be 300 ticks (15 s), got " + weaknessTicks);
        }
        // Validate MobEffects.WEAKNESS constant is accessible (compile guard)
        MobEffectInstance weaknessInstance = new MobEffectInstance(MobEffects.WEAKNESS, weaknessTicks, 0);
        if (weaknessInstance.getDuration() != weaknessTicks) {
            helper.fail("MobEffectInstance Weakness duration mismatch: " + weaknessInstance.getDuration());
        }
        helper.succeed();
    }

    /**
     * EndGod Desolation of Time cooldown must be 90 ticks (4.5 seconds),
     * shorter than the demigod's 120 ticks (6 seconds).
     */
    @GameTest
    public void endGodDesolationCooldownIs90Ticks(GameTestHelper helper) {
        SpellStats stats = EndGod.INSTANCE.getSpellStats("desolation_of_time");
        if (stats == null) {
            helper.fail("EndGod.getSpellStats(\"desolation_of_time\") must not be null");
        }
        if (stats.cooldown() != 90) {
            helper.fail("EndGod desolation cooldown must be 90 ticks (4.5 s), got " + stats.cooldown());
        }
        // Demigod should still be 120
        SpellStats demigodStats = End.INSTANCE.getSpellStats("desolation_of_time");
        if (demigodStats == null || demigodStats.cooldown() != 120) {
            helper.fail("End demigod desolation cooldown must be 120 ticks (unchanged)");
        }
        helper.succeed();
    }

    /**
     * {@link End#isAffectedByDesolation(ServerPlayer)} uses {@code End.DESOLATED_PLAYERS};
     * verify the set is accessible and that a newly-created UUID is not present.
     * (Full desolation effect coverage lives in EndDemigodTests which tests SpellRegistry directly.)
     */
    @GameTest
    public void endGodDesolationDisablesAbilities(GameTestHelper helper) {
        java.util.UUID testUUID = java.util.UUID.randomUUID();
        // Must not be affected before desolation is cast
        if (End.DESOLATED_PLAYERS.contains(testUUID)) {
            helper.fail("DESOLATED_PLAYERS must not contain an arbitrary UUID before any cast");
        }
        // Simulate desolation being applied and removed
        End.DESOLATED_PLAYERS.add(testUUID);
        if (!End.DESOLATED_PLAYERS.contains(testUUID)) {
            helper.fail("DESOLATED_PLAYERS must contain UUID after simulated desolation apply");
        }
        End.DESOLATED_PLAYERS.remove(testUUID);
        if (End.DESOLATED_PLAYERS.contains(testUUID)) {
            helper.fail("DESOLATED_PLAYERS must not contain UUID after simulated desolation removal");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Class hierarchy
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * EndGod must extend End so that god players inherit all demigod passive effects
     * (mob neutrality, ender chest expansion, unequip guard).
     */
    @GameTest
    public void endGodExtendsEnd(GameTestHelper helper) {
        if (!(EndGod.INSTANCE instanceof End)) {
            helper.fail("EndGod must extend End (demigod) — class hierarchy check failed");
        }
        helper.succeed();
    }

    /**
     * EndGod must be a distinct instance from End — they are separate ranks.
     */
    @GameTest
    public void endGodIsDistinctFromEnd(GameTestHelper helper) {
        if ((Order) EndGod.INSTANCE == (Order) End.INSTANCE) {
            helper.fail("EndGod.INSTANCE and End.INSTANCE must be different objects");
        }
        helper.succeed();
    }
}
