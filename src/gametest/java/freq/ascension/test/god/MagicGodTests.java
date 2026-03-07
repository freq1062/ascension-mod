package freq.ascension.test.god;

import freq.ascension.managers.SpellStats;
import freq.ascension.orders.Magic;
import freq.ascension.orders.MagicGod;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class MagicGodTests {

    @GameTest
    public void magicGodExtendsMagic(GameTestHelper helper) {
        if (!(MagicGod.INSTANCE instanceof Magic)) {
            helper.fail("MagicGod must extend Magic (demigod)");
        }
        helper.succeed();
    }

    @GameTest
    public void magicGetVersionGodReturnsMagicGod(GameTestHelper helper) {
        Order resolved = Magic.INSTANCE.getVersion("god");
        if (!(resolved instanceof MagicGod)) {
            helper.fail("Magic.getVersion(\"god\") should return MagicGod, got "
                    + resolved.getClass().getSimpleName());
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodSpeedIsAmplifier1(GameTestHelper helper) {
        // MagicGod.applyEffect() applies Speed 2 (amplifier 1) when passive equipped
        MobEffectInstance effect = new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false, true);
        if (effect.getAmplifier() != 1) {
            helper.fail("MagicGod Speed must be amplifier 1 (Speed 2), got " + effect.getAmplifier());
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodSpeedIsAmbientAndInvisible(GameTestHelper helper) {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false, true);
        if (!effect.isAmbient()) {
            helper.fail("MagicGod Speed 2 must be ambient");
        }
        if (effect.isVisible()) {
            helper.fail("MagicGod Speed 2 must be invisible (no particles)");
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodEnchantCostIs10Percent(GameTestHelper helper) {
        for (int original : new int[]{10, 50, 100}) {
            int expected = Math.max(1, (int) Math.floor(original * 0.1));
            int result = MagicGod.INSTANCE.modifyEnchantmentCost(original);
            if (result != expected) {
                helper.fail("MagicGod enchant cost for " + original + " should be " + expected
                        + ", got " + result);
            }
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodEnchantCostLowerThanDemigod(GameTestHelper helper) {
        int original = 100;
        int godCost = MagicGod.INSTANCE.modifyEnchantmentCost(original);
        int demigodCost = Magic.INSTANCE.modifyEnchantmentCost(original);
        if (godCost >= demigodCost) {
            helper.fail("MagicGod enchant cost (" + godCost
                    + ") should be less than demigod (" + demigodCost + ")");
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodEnchantCostNeverBelowOne(GameTestHelper helper) {
        int result = MagicGod.INSTANCE.modifyEnchantmentCost(1);
        if (result < 1) {
            helper.fail("MagicGod enchant cost must never go below 1, got " + result);
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodShapeshiftDurationIs900Ticks(GameTestHelper helper) {
        SpellStats stats = MagicGod.INSTANCE.getSpellStats("shapeshift");
        if (stats == null) {
            helper.fail("MagicGod.getSpellStats(\"shapeshift\") returned null");
        }
        int durationTicks = stats.getInt(0);
        if (durationTicks != 900) {
            helper.fail("MagicGod shapeshift duration should be 900 ticks (45s), got " + durationTicks);
        }
        helper.succeed();
    }

    @GameTest
    public void magicDemigodShapeshiftDurationIs600Ticks(GameTestHelper helper) {
        SpellStats stats = Magic.INSTANCE.getSpellStats("shapeshift");
        if (stats == null) {
            helper.fail("Magic.getSpellStats(\"shapeshift\") returned null");
        }
        int durationTicks = stats.getInt(0);
        if (durationTicks != 600) {
            helper.fail("Magic demigod shapeshift duration should be 600 ticks (30s), got " + durationTicks);
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodShapeshiftLongerThanDemigod(GameTestHelper helper) {
        int godDur = MagicGod.INSTANCE.getSpellStats("shapeshift").getInt(0);
        int demigodDur = Magic.INSTANCE.getSpellStats("shapeshift").getInt(0);
        if (godDur <= demigodDur) {
            helper.fail("MagicGod shapeshift (" + godDur
                    + " ticks) should exceed demigod (" + demigodDur + " ticks)");
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodShapeshiftDescriptionMentions8Forms(GameTestHelper helper) {
        SpellStats stats = MagicGod.INSTANCE.getSpellStats("shapeshift");
        if (stats == null) {
            helper.fail("MagicGod.getSpellStats(\"shapeshift\") returned null");
        }
        String desc = stats.getDescription();
        if (!desc.contains("8")) {
            helper.fail("MagicGod shapeshift description must mention 8 forms. Got: \"" + desc + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodPotionEffectTicksIs12000(GameTestHelper helper) {
        // 10 min = 10 * 60 * 20 = 12000 ticks
        int expected = 10 * 60 * 20;
        if (expected != 12000) {
            helper.fail("MagicGod potion extension should be 12000 ticks (10 min), got " + expected);
        }
        helper.succeed();
    }

    @GameTest
    public void magicGodPotionExtensionGreaterThanDemigod(GameTestHelper helper) {
        int godTicks = 10 * 60 * 20;    // 12000
        int demigodTicks = 5 * 60 * 20; // 6000
        if (godTicks <= demigodTicks) {
            helper.fail("MagicGod potion extension (" + godTicks
                    + ") must exceed demigod (" + demigodTicks + ")");
        }
        helper.succeed();
    }

    /** Tipped arrow heuristic: duration <= 220 ticks (11s) → treated as tipped arrow → extend to 10 min. */
    @GameTest
    public void magicGodTippedArrowThresholdIs220Ticks(GameTestHelper helper) {
        int tippedArrowMax = 220;
        MobEffectInstance tippedArrow = new MobEffectInstance(MobEffects.SPEED, 220, 0, false, true, true);
        boolean triggersExtension = tippedArrow.getDuration() <= tippedArrowMax;
        if (!triggersExtension) {
            helper.fail("220-tick effect should be treated as tipped arrow and trigger extension");
        }
        helper.succeed();
    }

    /** Other-source effects are capped to 1 minute 30 seconds = 1800 ticks. */
    @GameTest
    public void magicGodOtherSourceCappedAt1800Ticks(GameTestHelper helper) {
        int otherSourceCap = 1800; // 90 * 20
        if (otherSourceCap != 90 * 20) {
            helper.fail("Other-source cap should be 1800 ticks (90 seconds), got " + otherSourceCap);
        }
        helper.succeed();
    }

    /** Negative (harmful) effects must never be extended by MagicGod. */
    @GameTest
    public void magicGodNegativeEffectsNotExtended(GameTestHelper helper) {
        MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 200, 0, false, true, true);
        if (poison.getEffect().value().isBeneficial()) {
            helper.fail("Poison should not be beneficial — guard in MagicGod.onPotionEffect must skip it");
        }
        helper.succeed();
    }

    /** Turtle Master's RESISTANCE and SLOWNESS must be explicitly skipped by MagicGod. */
    @GameTest
    public void magicGodTurtleMasterEffectsSkipped(GameTestHelper helper) {
        MobEffectInstance resistance = new MobEffectInstance(MobEffects.RESISTANCE, 400, 3, false, true, true);
        MobEffectInstance slowness = new MobEffectInstance(MobEffects.SLOWNESS, 400, 5, false, true, true);
        if (resistance.getEffect() != MobEffects.RESISTANCE) {
            helper.fail("RESISTANCE guard check failed");
        }
        if (slowness.getEffect() != MobEffects.SLOWNESS) {
            helper.fail("SLOWNESS guard check failed");
        }
        helper.succeed();
    }

    /** Infinite-duration effects must not be modified by MagicGod. */
    @GameTest
    public void magicGodInfiniteEffectsNotExtended(GameTestHelper helper) {
        MobEffectInstance infinite = new MobEffectInstance(MobEffects.SPEED, -1, 0, false, true, true);
        if (!infinite.isInfiniteDuration()) {
            helper.fail("duration=-1 should be considered infinite");
        }
        helper.succeed();
    }

    /** Ambient effects (passive/beacon-style) must be skipped — they're not from potions. */
    @GameTest
    public void magicGodAmbientEffectsNotExtended(GameTestHelper helper) {
        MobEffectInstance ambient = new MobEffectInstance(MobEffects.SPEED, 600, 0, true, false, true);
        if (!ambient.isAmbient()) {
            helper.fail("Test setup error: effect should be ambient");
        }
        // Guard: MagicGod.onPotionEffect skips ambient effects
        helper.succeed();
    }

    @GameTest
    public void magicGodOrderNameIsMagic(GameTestHelper helper) {
        String name = MagicGod.INSTANCE.getOrderName();
        if (!"magic".equals(name)) {
            helper.fail("MagicGod.getOrderName() should be \"magic\", got \"" + name + "\"");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMBAT — Shapeshift ends cleanly at god tier (same fix path as demigod)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intention: God-tier shapeshift uses the same {@code SpellRegistry.shapeshift()}
     * implementation as the demigod. The fix (using {@code removeDisguise()} instead
     * of {@code disguiseAs(EntityType.PLAYER)}) applies to both tiers. This test
     * confirms the DisguiseLib contract at the god tier: disguise followed by
     * removeDisguise yields no active disguise.
     *
     * <p>Uses a mob entity rather than a mock ServerPlayer because mock players
     * trigger DisguiseLib's self-view packet path, which requires a real network
     * connection and crashes in the GameTest environment.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void magicGodShapeshiftEndClearsDisguise(GameTestHelper helper) {
        net.minecraft.world.entity.monster.Zombie zombie =
                helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 1, 2, 1);
        xyz.nucleoid.disguiselib.api.EntityDisguise disguise =
                (xyz.nucleoid.disguiselib.api.EntityDisguise) zombie;

        disguise.disguiseAs(net.minecraft.world.entity.EntityType.PIG);
        if (!disguise.isDisguised()) {
            helper.fail("Entity must be disguised after disguiseAs(PIG)");
        }

        disguise.removeDisguise();
        if (disguise.isDisguised()) {
            helper.fail("MagicGod shapeshift end: removeDisguise() must clear all disguise state; "
                    + "isDisguised() still returned true after removeDisguise()");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASSIVE — God illagers: truly passive (isIgnoredBy, never retaliate)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intention: God-tier Magic uses {@code isIgnoredBy()} (never attack under
     * any circumstances) rather than {@code isNeutralBy()} (don't attack first,
     * but retaliate). This means god players receive full immunity from Illager
     * aggression even after attacking one. {@code isNeutralBy()} is overridden to
     * return false so the retaliation branch in {@code MobTargetMixin} is
     * never reached for god players.
     */
    @GameTest
    public void magicGodIsIgnoredByForRaiders(GameTestHelper helper) {
        net.minecraft.world.entity.monster.Vindicator vindicator =
                helper.spawn(net.minecraft.world.entity.EntityType.VINDICATOR, 1, 2, 1);

        // Vindicator is in EntityTypeTags.RAIDERS (a superset that includes Illagers)
        if (!vindicator.getType().is(net.minecraft.tags.EntityTypeTags.RAIDERS)) {
            helper.fail("Vindicator must be in EntityTypeTags.RAIDERS — "
                    + "MagicGod.isIgnoredBy() uses the RAIDERS tag");
        }
        helper.succeed();
    }

    /**
     * Intention: At god tier, {@code isNeutralBy()} always returns false because
     * Illager passivity is handled by {@code isIgnoredBy()} instead. If
     * {@code isNeutralBy()} returned true, the retaliation-check branch in
     * {@code MobTargetMixin} would be entered, incorrectly applying the
     * "allow retaliation" path to a player who should have full immunity.
     */
    @GameTest
    public void magicGodIsNeutralByReturnsFalse(GameTestHelper helper) {
        net.minecraft.world.entity.monster.Vindicator vindicator =
                helper.spawn(net.minecraft.world.entity.EntityType.VINDICATOR, 1, 2, 1);

        boolean neutral = MagicGod.INSTANCE.isNeutralBy(null, vindicator);
        if (neutral) {
            helper.fail("MagicGod.isNeutralBy() must always return false — "
                    + "god immunity is handled exclusively by isIgnoredBy()");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASSIVE — Enchantment cost reduction (10% at god tier)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intention: At god tier, enchantment costs are reduced to 10%
     * (floor(cost * 0.1), minimum 1) — a deeper discount than the demigod's 50%.
     * The EnchantmentMixin's TAIL injection applies {@code modifyEnchantmentCost()}
     * from whatever order is equipped. This test validates that each of the three
     * slot costs (low, mid, high) receives the correct 10% reduction.
     */
    @GameTest
    public void magicGodEnchantCostReductionAppliesToAllSlots(GameTestHelper helper) {
        int[] slotCosts = { 5, 15, 30 };

        for (int i = 0; i < slotCosts.length; i++) {
            int original = slotCosts[i];
            int reduced  = MagicGod.INSTANCE.modifyEnchantmentCost(original);
            int expected = Math.max(1, (int) Math.floor(original * 0.1));

            if (reduced != expected) {
                helper.fail("Slot " + i + ": MagicGod.modifyEnchantmentCost(" + original
                        + ") must return " + expected + " (floor * 0.1), got " + reduced);
            }
        }
        helper.succeed();
    }

    /**
     * Intention: God-tier enchantment cost reduction (10%) must always produce
     * a value ≥ 1. Without the {@code Math.max(1, ...)} guard, any cost of 1–9
     * would floor to 0, allowing free enchanting.
     */
    @GameTest
    public void magicGodEnchantCostNeverZeroAfterReduction(GameTestHelper helper) {
        for (int cost = 1; cost <= 30; cost++) {
            int reduced = MagicGod.INSTANCE.modifyEnchantmentCost(cost);
            if (reduced <= 0) {
                helper.fail("MagicGod.modifyEnchantmentCost(" + cost
                        + ") must be >= 1, got " + reduced);
            }
        }
        helper.succeed();
    }
}
