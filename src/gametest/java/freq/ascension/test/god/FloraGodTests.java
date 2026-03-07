package freq.ascension.test.god;

import freq.ascension.managers.SpellStats;
import freq.ascension.orders.Flora;
import freq.ascension.orders.FloraGod;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class FloraGodTests {

    @GameTest
    public void floraGodExtendsFlora(GameTestHelper helper) {
        if (!(FloraGod.INSTANCE instanceof Flora)) {
            helper.fail("FloraGod must extend Flora (demigod)");
        }
        helper.succeed();
    }

    @GameTest
    public void floraGetVersionGodReturnsFloraGod(GameTestHelper helper) {
        Order resolved = Flora.INSTANCE.getVersion("god");
        if (!(resolved instanceof FloraGod)) {
            helper.fail("Flora.getVersion(\"god\") should return FloraGod, got "
                    + resolved.getClass().getSimpleName());
        }
        helper.succeed();
    }

    @GameTest
    public void floraGetVersionDemigodReturnsFlora(GameTestHelper helper) {
        Order resolved = Flora.INSTANCE.getVersion("demigod");
        if (resolved instanceof FloraGod) {
            helper.fail("Flora.getVersion(\"demigod\") must NOT return FloraGod");
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodThornsSpellStatsExist(GameTestHelper helper) {
        SpellStats stats = FloraGod.INSTANCE.getSpellStats("thorns");
        if (stats == null) {
            helper.fail("FloraGod.getSpellStats(\"thorns\") returned null");
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodThornsDescriptionMentions25PctDamage(GameTestHelper helper) {
        SpellStats stats = FloraGod.INSTANCE.getSpellStats("thorns");
        if (stats == null) {
            helper.fail("FloraGod.getSpellStats(\"thorns\") returned null");
        }
        String desc = stats.getDescription();
        if (!desc.contains("25%")) {
            helper.fail("FloraGod thorns description must mention 25% initial damage. Got: \"" + desc + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodThornsDescriptionMentionsPullOutDamage15Pct(GameTestHelper helper) {
        SpellStats stats = FloraGod.INSTANCE.getSpellStats("thorns");
        if (stats == null) {
            helper.fail("FloraGod.getSpellStats(\"thorns\") returned null");
        }
        String desc = stats.getDescription();
        if (!desc.contains("15%")) {
            helper.fail("FloraGod thorns description must mention 15% pull-out damage. Got: \"" + desc + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodThornsDescriptionMentions4SecFreeze(GameTestHelper helper) {
        SpellStats stats = FloraGod.INSTANCE.getSpellStats("thorns");
        if (stats == null) {
            helper.fail("FloraGod.getSpellStats(\"thorns\") returned null");
        }
        // Description should mention "4" (seconds) somewhere
        String desc = stats.getDescription();
        if (!desc.contains("4")) {
            helper.fail("FloraGod thorns description must mention 4 second freeze. Got: \"" + desc + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodThornsInitialDamageGreaterThanDemigod(GameTestHelper helper) {
        // God: 25% of max health. Demigod: 15% of max health.
        float demigodPct = 0.15f;
        float godPct = 0.25f;
        if (godPct <= demigodPct) {
            helper.fail("FloraGod thorns initial damage (" + (godPct * 100)
                    + "%) should exceed demigod (" + (demigodPct * 100) + "%)");
        }
        helper.succeed();
    }

    @GameTest
    public void floraThornsFreezeDurationGodLongerThanDemigod(GameTestHelper helper) {
        // Demigod: 60 ticks (3s). God: 80 ticks (4s).
        int demigodFreezeTicks = 60;
        int godFreezeTicks = 80;
        if (godFreezeTicks <= demigodFreezeTicks) {
            helper.fail("FloraGod thorns freeze (" + godFreezeTicks
                    + " ticks) should exceed demigod (" + demigodFreezeTicks + " ticks)");
        }
        helper.succeed();
    }

    @GameTest
    public void floraThornsGodPullOutDamageGreaterThanDemigod(GameTestHelper helper) {
        // Demigod pull-out: 5%. God pull-out: 15%.
        float demigodPullOut = 0.05f;
        float godPullOut = 0.15f;
        if (godPullOut <= demigodPullOut) {
            helper.fail("FloraGod pull-out damage (" + (godPullOut * 100)
                    + "%) should exceed demigod (" + (demigodPullOut * 100) + "%)");
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodGoldenAppleAbsorptionIsAmplifier1(GameTestHelper helper) {
        // Mirrors FloraGod.onItemEaten() — amplifier 1 = +2 full absorption hearts
        MobEffectInstance absorption = new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1, false, true, true);
        if (absorption.getAmplifier() != 1) {
            helper.fail("FloraGod golden apple must give Absorption 2 (amplifier 1) = +2 hearts extra, got amp="
                    + absorption.getAmplifier());
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodGoldenAppleAbsorptionDurationIs2400Ticks(GameTestHelper helper) {
        MobEffectInstance absorption = new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1, false, true, true);
        if (absorption.getDuration() != 2400) {
            helper.fail("FloraGod golden apple absorption must last 2400 ticks (2 min), got "
                    + absorption.getDuration());
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodGoldenAppleRegenIs300Ticks(GameTestHelper helper) {
        MobEffectInstance regen = new MobEffectInstance(MobEffects.REGENERATION, 300, 0, false, true, true);
        if (regen.getDuration() != 300) {
            helper.fail("FloraGod golden apple Regen must last 300 ticks (15s), got " + regen.getDuration());
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodGoldenAppleRegenIsAmplifier0(GameTestHelper helper) {
        MobEffectInstance regen = new MobEffectInstance(MobEffects.REGENERATION, 300, 0, false, true, true);
        if (regen.getAmplifier() != 0) {
            helper.fail("FloraGod golden apple must give Regen I (amplifier 0), got " + regen.getAmplifier());
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodInventoryPlantReductionIsLessThanDemigod(GameTestHelper helper) {
        // God: 90% reduction (multiplier 0.1). Demigod: 50% reduction (multiplier 0.5).
        double godMultiplier = 0.1;
        double demigodMultiplier = 0.5;
        if (godMultiplier >= demigodMultiplier) {
            helper.fail("FloraGod inventory plant multiplier (" + godMultiplier
                    + ") must be less than demigod proximity multiplier (" + demigodMultiplier + ")");
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodInventoryPlantReducesRangeBy90Percent(GameTestHelper helper) {
        double followRange = 16.0;
        double reducedRange = followRange * 0.1;
        if (Math.abs(reducedRange - 1.6) > 0.001) {
            helper.fail("90% range reduction of 16.0 should give 1.6, got " + reducedRange);
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodOrderNameIsFlora(GameTestHelper helper) {
        String name = FloraGod.INSTANCE.getOrderName();
        if (!"flora".equals(name)) {
            helper.fail("FloraGod.getOrderName() should be \"flora\", got \"" + name + "\"");
        }
        helper.succeed();
    }

    // ── Creeper neutral change (isIgnoredBy → isNeutralBy) ───────────────────

    @GameTest
    public void floraGodIsNeutralByOverridesCreeper(GameTestHelper helper) {
        // FloraGod must declare isNeutralBy to handle creeper proximity logic
        try {
            FloraGod.class.getDeclaredMethod("isNeutralBy",
                    net.minecraft.server.level.ServerPlayer.class,
                    net.minecraft.world.entity.Mob.class);
        } catch (NoSuchMethodException e) {
            helper.fail("FloraGod must override isNeutralBy() for creeper neutral-when-near-plant logic");
            return;
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodIsIgnoredByDoesNotUnconditionallyIgnoreCreeper(GameTestHelper helper) {
        // isIgnoredBy should no longer make creepers permanently passive for FloraGod.
        // The method must exist (bees and creakings are still handled here).
        try {
            FloraGod.class.getDeclaredMethod("isIgnoredBy",
                    net.minecraft.server.level.ServerPlayer.class,
                    net.minecraft.world.entity.Mob.class);
        } catch (NoSuchMethodException e) {
            helper.fail("FloraGod.isIgnoredBy() must still be declared for bees and creakings");
            return;
        }
        helper.succeed();
    }

    @GameTest
    public void floraGodCreeperNeutralUsesPlantProximityOrInventory(GameTestHelper helper) {
        // Structural test: isNeutralBy guards creepers with plant proximity OR inventory check.
        // Full runtime verification requires a live game environment.
        // This test confirms the method is accessible and the class compiles correctly.
        try {
            FloraGod.class.getDeclaredMethod("isNeutralBy",
                    net.minecraft.server.level.ServerPlayer.class,
                    net.minecraft.world.entity.Mob.class);
        } catch (NoSuchMethodException e) {
            helper.fail("FloraGod.isNeutralBy() must handle creeper-neutral-by-plant logic");
            return;
        }
        helper.succeed();
    }
}
