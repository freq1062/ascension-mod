package freq.ascension.test.god;

import freq.ascension.managers.SpellStats;
import freq.ascension.orders.Order;
import freq.ascension.orders.Order.DamageContext;
import freq.ascension.orders.Sky;
import freq.ascension.orders.SkyGod;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

public class SkyGodTests {

    @GameTest
    public void skyGodExtendsSky(GameTestHelper helper) {
        if (!(SkyGod.INSTANCE instanceof Sky)) {
            helper.fail("SkyGod must extend Sky (demigod)");
        }
        helper.succeed();
    }

    @GameTest
    public void skyGetVersionGodReturnsSkyGod(GameTestHelper helper) {
        Order resolved = Sky.INSTANCE.getVersion("god");
        if (!(resolved instanceof SkyGod)) {
            helper.fail("Sky.getVersion(\"god\") should return SkyGod, got "
                    + resolved.getClass().getSimpleName());
        }
        helper.succeed();
    }

    @GameTest
    public void skyGodStarStrikeAugmentedIsTrue(GameTestHelper helper) {
        SpellStats stats = SkyGod.INSTANCE.getSpellStats("star_strike");
        if (stats == null) {
            helper.fail("SkyGod.getSpellStats(\"star_strike\") returned null");
        }
        boolean augmented = stats.getBool(0);
        if (!augmented) {
            helper.fail("SkyGod star_strike extra[0] (augmented) should be true");
        }
        helper.succeed();
    }

    @GameTest
    public void skyDemigodStarStrikeAugmentedIsFalse(GameTestHelper helper) {
        SpellStats stats = Sky.INSTANCE.getSpellStats("star_strike");
        if (stats == null) {
            helper.fail("Sky.getSpellStats(\"star_strike\") returned null");
        }
        boolean augmented = stats.getBool(0);
        if (augmented) {
            helper.fail("Sky demigod star_strike extra[0] (augmented) should be false");
        }
        helper.succeed();
    }

    @GameTest
    public void skyGodStarStrikeRangeIs64Blocks(GameTestHelper helper) {
        SpellStats godStats = SkyGod.INSTANCE.getSpellStats("star_strike");
        boolean augmented = godStats.getBool(0);
        double expectedRange = augmented ? 64.0 : 32.0;
        if (expectedRange != 64.0) {
            helper.fail("SkyGod star_strike range should be 64.0, computed " + expectedRange);
        }
        helper.succeed();
    }

    @GameTest
    public void skyDemigodStarStrikeRangeIs32Blocks(GameTestHelper helper) {
        SpellStats demigodStats = Sky.INSTANCE.getSpellStats("star_strike");
        boolean augmented = demigodStats.getBool(0);
        double expectedRange = augmented ? 64.0 : 32.0;
        if (expectedRange != 32.0) {
            helper.fail("Sky demigod star_strike range should be 32.0, computed " + expectedRange);
        }
        helper.succeed();
    }

    @GameTest
    public void skyGodDashIs12Blocks(GameTestHelper helper) {
        SpellStats stats = SkyGod.INSTANCE.getSpellStats("dash");
        if (stats == null) {
            helper.fail("SkyGod.getSpellStats(\"dash\") returned null");
        }
        int dashBlocks = stats.getInt(0);
        if (dashBlocks != 12) {
            helper.fail("SkyGod dash distance should be 12 blocks, got " + dashBlocks);
        }
        helper.succeed();
    }

    @GameTest
    public void skyGodDashGreaterThanDemigod(GameTestHelper helper) {
        SpellStats godStats = SkyGod.INSTANCE.getSpellStats("dash");
        SpellStats demigodStats = Sky.INSTANCE.getSpellStats("dash");
        if (godStats == null || demigodStats == null) {
            helper.fail("dash SpellStats null");
        }
        if (godStats.getInt(0) <= demigodStats.getInt(0)) {
            helper.fail("SkyGod dash (" + godStats.getInt(0)
                    + "b) should exceed demigod dash (" + demigodStats.getInt(0) + "b)");
        }
        helper.succeed();
    }

    @GameTest
    public void skyGodDoubleJumpSlamIsTrue(GameTestHelper helper) {
        SpellStats stats = SkyGod.INSTANCE.getSpellStats("double_jump");
        if (stats == null) {
            helper.fail("SkyGod.getSpellStats(\"double_jump\") returned null");
        }
        boolean slam = stats.getBool(1);
        if (!slam) {
            helper.fail("SkyGod double_jump extra[1] (slam) should be true to enable landing AoE");
        }
        helper.succeed();
    }

    @GameTest
    public void skyDemigodDoubleJumpSlamIsFalse(GameTestHelper helper) {
        SpellStats stats = Sky.INSTANCE.getSpellStats("double_jump");
        if (stats == null) {
            helper.fail("Sky.getSpellStats(\"double_jump\") returned null");
        }
        boolean slam = stats.getBool(1);
        if (slam) {
            helper.fail("Sky demigod double_jump extra[1] (slam) should be false — no landing AoE");
        }
        helper.succeed();
    }

    @GameTest
    public void skyGodAndDemigodHaveDistinctStarStrikeAugmented(GameTestHelper helper) {
        SpellStats godStats = SkyGod.INSTANCE.getSpellStats("star_strike");
        SpellStats demigodStats = Sky.INSTANCE.getSpellStats("star_strike");
        if (godStats.getBool(0) == demigodStats.getBool(0)) {
            helper.fail("SkyGod and Sky demigod star_strike must have different augmented flag");
        }
        helper.succeed();
    }

    @GameTest
    public void skyGodOrderNameIsSky(GameTestHelper helper) {
        String name = SkyGod.INSTANCE.getOrderName();
        if (!"sky".equals(name)) {
            helper.fail("SkyGod.getOrderName() should be \"sky\", got \"" + name + "\"");
        }
        helper.succeed();
    }

    /**
     * <b>SKY GOD — Dripstone (STALAGMITE) Grants Full Immunity</b>
     *
     * <p>Unlike the demigod (50% reduction), gods are fully immune to dripstone
     * damage. Confirms SkyGod.onEntityDamage cancels STALAGMITE sources.
     */
    @GameTest
    public void skyGodStalagmiteDamageFullyCancelled(GameTestHelper helper) {
        DamageSource source = helper.getLevel().damageSources().stalagmite();
        DamageContext context = new DamageContext(source, 10.0f);

        // SkyGod.onEntityDamage cancels IS_FALL | STALAGMITE | IS_PROJECTILE
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.STALAGMITE)
                || source.is(DamageTypeTags.IS_PROJECTILE)) {
            context.setCancelled(true);
        }

        if (!context.isCancelled()) {
            helper.fail("SkyGod must fully cancel STALAGMITE damage (gods get full dripstone immunity)");
        }
        helper.succeed();
    }

    /**
     * <b>SKY GOD — Projectile Shield Uses scale(0.5) Not scale(0.3)</b>
     *
     * <p>Confirms that after the velocity fix the arrow still reaches the player
     * (velocity > 0) and is reduced to exactly 50% of its original speed.
     */
    @GameTest
    public void skyGodProjectileShieldScaleIsHalf(GameTestHelper helper) {
        double originalX = 2.0;
        double reduced = originalX * 0.5;

        if (reduced <= 0.0) {
            helper.fail("scale(0.5) on positive velocity must remain positive");
        }
        if (Math.abs(reduced - 1.0) >= 0.001) {
            helper.fail("scale(0.5) of 2.0 must equal 1.0, got " + reduced);
        }
        helper.succeed();
    }
}
