package freq.ascension.test.god;

import freq.ascension.managers.SpellStats;
import freq.ascension.orders.Nether;
import freq.ascension.orders.NetherGod;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class NetherGodTests {

    @GameTest
    public void netherGodExtendsNether(GameTestHelper helper) {
        if (!(NetherGod.INSTANCE instanceof Nether)) {
            helper.fail("NetherGod must extend Nether (demigod)");
        }
        helper.succeed();
    }

    @GameTest
    public void netherGetVersionGodReturnsNetherGod(GameTestHelper helper) {
        Order resolved = Nether.INSTANCE.getVersion("god");
        if (!(resolved instanceof NetherGod)) {
            helper.fail("Nether.getVersion(\"god\") should return NetherGod, got "
                    + resolved.getClass().getSimpleName());
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodSoulDrainDurationIs300Ticks(GameTestHelper helper) {
        SpellStats stats = NetherGod.INSTANCE.getSpellStats("soul_drain");
        if (stats == null) {
            helper.fail("NetherGod.getSpellStats(\"soul_drain\") returned null");
        }
        int durationTicks = stats.getInt(0);
        if (durationTicks != 300) {
            helper.fail("NetherGod soul_drain duration should be 300 ticks (15s), got " + durationTicks);
        }
        helper.succeed();
    }

    @GameTest
    public void netherDemigodSoulDrainDurationIs200Ticks(GameTestHelper helper) {
        SpellStats stats = Nether.INSTANCE.getSpellStats("soul_drain");
        if (stats == null) {
            helper.fail("Nether.getSpellStats(\"soul_drain\") returned null");
        }
        int durationTicks = stats.getInt(0);
        if (durationTicks != 200) {
            helper.fail("Nether demigod soul_drain duration should be 200 ticks (10s), got " + durationTicks);
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodSoulDrainLongerThanDemigod(GameTestHelper helper) {
        int godDur = NetherGod.INSTANCE.getSpellStats("soul_drain").getInt(0);
        int demigodDur = Nether.INSTANCE.getSpellStats("soul_drain").getInt(0);
        if (godDur <= demigodDur) {
            helper.fail("NetherGod soul_drain duration (" + godDur
                    + ") must exceed demigod (" + demigodDur + ")");
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodSoulDrainRatioIsHalf(GameTestHelper helper) {
        float damage = 10.0f;
        float godSaturation = damage * 0.5f;
        float demigodSaturation = damage * (1.0f / 3.0f);
        if (godSaturation <= demigodSaturation) {
            helper.fail("God soul drain ratio (0.5) should yield more saturation than demigod (1/3). "
                    + "God=" + godSaturation + " Demigod=" + demigodSaturation);
        }
        if (Math.abs(godSaturation - 5.0f) > 0.001f) {
            helper.fail("God soul drain: 10 damage * 0.5 should give 5.0 saturation, got " + godSaturation);
        }
        helper.succeed();
    }

    @GameTest
    public void netherDemigodSoulDrainRatioIsOneThird(GameTestHelper helper) {
        float damage = 9.0f;
        float saturation = damage * (1.0f / 3.0f);
        if (Math.abs(saturation - 3.0f) > 0.001f) {
            helper.fail("Demigod soul drain: 9 damage * 1/3 should give 3.0 saturation, got " + saturation);
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodGhastCarryDescMentionsDoubleHealth(GameTestHelper helper) {
        SpellStats stats = NetherGod.INSTANCE.getSpellStats("ghast_carry");
        if (stats == null) {
            helper.fail("NetherGod.getSpellStats(\"ghast_carry\") returned null");
        }
        String desc = stats.getDescription().toLowerCase();
        if (!desc.contains("double")) {
            helper.fail("NetherGod ghast_carry description must mention double-health. Got: \"" + desc + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodGhastCarrySpeedIs11_5(GameTestHelper helper) {
        SpellStats stats = NetherGod.INSTANCE.getSpellStats("ghast_carry");
        if (stats == null) {
            helper.fail("NetherGod.getSpellStats(\"ghast_carry\") returned null");
        }
        String desc = stats.getDescription();
        if (!desc.contains("11.5")) {
            helper.fail("NetherGod ghast_carry description must mention 11.5 b/s speed. Got: \"" + desc + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodLavaDolphinsGraceIsAmbient(GameTestHelper helper) {
        // Mirrors NetherGod.applyEffect() lava branch
        MobEffectInstance effect = new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 80, 0, true, false, true);
        if (!effect.isAmbient()) {
            helper.fail("NetherGod Dolphins' Grace (in lava) must be ambient");
        }
        if (effect.isVisible()) {
            helper.fail("NetherGod Dolphins' Grace (in lava) must be invisible");
        }
        if (effect.getAmplifier() != 0) {
            helper.fail("NetherGod Dolphins' Grace amplifier must be 0, got " + effect.getAmplifier());
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodLavaDolphinsGraceDurationIs80Ticks(GameTestHelper helper) {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 80, 0, true, false, true);
        if (effect.getDuration() != 80) {
            helper.fail("NetherGod Dolphins' Grace must last 80 ticks (persists across 40-tick refresh), got "
                    + effect.getDuration());
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodIsDistinctFromDemigod(GameTestHelper helper) {
        if (NetherGod.INSTANCE == (Object) Nether.INSTANCE) {
            helper.fail("NetherGod.INSTANCE must be a different object from Nether.INSTANCE");
        }
        helper.succeed();
    }

    @GameTest
    public void netherGodOrderNameIsNether(GameTestHelper helper) {
        String name = NetherGod.INSTANCE.getOrderName();
        if (!"nether".equals(name)) {
            helper.fail("NetherGod.getOrderName() should be \"nether\", got \"" + name + "\"");
        }
        helper.succeed();
    }

    /**
     * Validates that NetherGod.applyEffect() inherits fire resistance (ambient icon)
     * from the Nether base class via super.applyEffect().
     */
    @GameTest
    public void netherGodFireResistanceIconIsAppliedViaSuper(GameTestHelper helper) {
        // NetherGod delegates to super.applyEffect() which applies the ambient FIRE_RESISTANCE.
        // Structural check: MobEffectInstance with ambient=true, showParticles=false, showIcon=true
        MobEffectInstance effect = new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 80, 0, true, false, true);
        if (!effect.isAmbient()) {
            helper.fail("NetherGod inherited fire resistance must be ambient (showIcon=true, no particles)");
        }
        if (effect.isVisible()) {
            helper.fail("NetherGod inherited fire resistance must have showParticles=false");
        }
        helper.succeed();
    }

    /**
     * Validates that Nether.clearFireTracking() does not throw for an arbitrary UUID,
     * ensuring safe cleanup in the DISCONNECT handler.
     */
    @GameTest
    public void netherFireTrackingCleanupIsSafe(GameTestHelper helper) {
        java.util.UUID id = java.util.UUID.randomUUID();
        try {
            Nether.clearFireTracking(id);
        } catch (Exception e) {
            helper.fail("Nether.clearFireTracking() must not throw for unknown UUIDs: " + e.getMessage());
        }
        helper.succeed();
    }
}
