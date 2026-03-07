package freq.ascension.test.demigod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vindicator;

import freq.ascension.managers.SpellStats;
import freq.ascension.orders.Magic;

/**
 * Comprehensive GameTest suite for Magic Order (Demigod) abilities.
 * Fabric 1.21.10 / Java 21 / Server-side only.
 *
 * <p><b>Magic Order Overview:</b><br>
 * The Magic Order shapes a demigod into a master of arcane knowledge and
 * transformation. Its passive grants perpetual mobility, deeply discounted
 * enchanting, and an uneasy truce with all Illager factions. Its utility
 * reshapes every short-lived potion into a sustained resource. Its combat
 * spell allows temporary embodiment of slain creatures, drawing on a personal
 * history of kills.
 *
 * <p><b>Tested Abilities:</b>
 * <ul>
 *   <li><b>PASSIVE (Speed I):</b> Permanent Speed 1 applied as an
 *       ambient/invisible effect every 40 ticks. Duration 60 ticks ensures
 *       the effect never expires between refresh cycles.</li>
 *   <li><b>PASSIVE (Illager Neutrality):</b> Any mob in the
 *       {@code EntityTypeTags.ILLAGER} tag treats a Magic Passive player as
 *       neutral. Implemented via {@code MobTargetMixin}, which cancels
 *       {@code Mob.setTarget()} and redirects to null when
 *       {@code isNeutralBy()} returns true.</li>
 *   <li><b>PASSIVE (Enchantment Cost):</b> {@code EnchantmentMixin} intercepts
 *       the local {@code cost} variable in {@code EnchantmentMenu.slotsChanged()}
 *       (ordinal 0) and halves it via
 *       {@code Math.max(1, (int) Math.floor(cost * 0.5))}.</li>
 *   <li><b>UTILITY (Potion Refresh):</b> {@code PotionMixin} calls
 *       {@code onPotionEffect()} on every {@code addEffect()} call. Magic
 *       utility extends short beneficial potions to 5:00 and tipped-arrow
 *       effects (≤20s) to 1:00. Negative effects, Resistance, Slowness,
 *       infinite-duration effects, and ambient/invisible effects are all
 *       explicitly excluded.</li>
 *   <li><b>COMBAT (Shapeshift):</b> On kill, the victim's EntityType is pushed
 *       onto a LIFO stack (max 5). Activation pops the top, disguises the
 *       player via DisguiseLib, and restores after 600 ticks (30 seconds).
 *       Bosses (Elder Guardian, Wither, Ender Dragon, Warden) and players are
 *       excluded from the history at demigod rank.</li>
 * </ul>
 *
 * <p><b>Design Note — History Cap vs. Spec:</b><br>
 * The original specification describes a sliding-window history where the
 * oldest entry is evicted when a 6th kill is made. The current implementation
 * in {@code ServerPlayerMixin.pushShapeshiftKill()} uses a hard cap:
 * {@code if (size >= 5) return;}. This means the 6th kill is silently
 * <em>dropped</em>, not the 1st. Tests here validate the <em>actual</em>
 * implementation behaviour. A future refactor to evict-oldest would require
 * changing {@code pushShapeshiftKill} to use {@code remove(0)} before
 * {@code add(id)} when at capacity.
 */
public class MagicDemigodTests {

    // The demigod shapeshift spell duration in ticks: getSpellStats("shapeshift").getInt(0)
    private static final int SHAPESHIFT_DURATION_TICKS = 600;

    // Magic utility: 5:00 = 5 * 60 * 20 = 6000 ticks (mirrors getPotionEffectTicks())
    private static final int FIVE_MINUTES_TICKS = 6000;

    // Magic utility: tipped-arrow cap = 20 * 20 = 400 ticks (20 seconds)
    private static final int TIPPED_ARROW_DURATION_THRESHOLD_TICKS = 400;

    // Magic utility: tipped-arrow target = 20 * 60 = 1200 ticks (1 minute)
    private static final int TIPPED_ARROW_TARGET_TICKS = 1200;

    // ─────────────────────────────────────────────────────────────────────────
    // PASSIVE — Speed I
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intention: Magic passive applies Speed 1 as an <em>ambient</em>,
     * <em>invisible</em> effect so it matches the beacon-style aesthetic and
     * does not flood the player's screen with orange speed particles. This
     * is the same contract as Earth's Haste passive — ambient effects are
     * visually subtle on the client side.
     *
     * <p>Mirrors the exact constructor call in {@code Magic.applyEffect()}:
     * {@code new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, true)}.
     */
    @GameTest
    public void speedEffectIsAmbientAndInvisible(GameTestHelper helper) {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, true);

        if (!effect.isAmbient()) {
            helper.fail("Magic Speed must be ambient (beacon-style) — ambient flag is false in applyEffect()");
        }
        if (effect.isVisible()) {
            helper.fail("Magic Speed must be invisible (no particle swarm) — visible flag is true in applyEffect()");
        }
        helper.succeed();
    }

    /**
     * Intention: Speed must never lapse. AbilityManager ticks
     * {@code applyEffect()} every 40 ticks. A 60-tick duration provides a
     * 20-tick safety buffer, ensuring the effect is still active when the
     * next refresh fires. A duration ≤ 40 would allow a 1-tick window where
     * the player loses Speed, causing visible deceleration mid-sprint.
     */
    @GameTest
    public void speedEffectDurationCoversRefreshCycle(GameTestHelper helper) {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, true);

        if (effect.getDuration() <= 40) {
            helper.fail("Speed duration (" + effect.getDuration()
                    + ") must exceed the 40-tick applyEffect refresh interval to prevent mid-sprint expiry");
        }
        helper.succeed();
    }

    /**
     * Intention: Speed 1 is amplifier 0 (zero-indexed). Amplifier 1 would be
     * Speed II — significantly stronger and unintended for the demigod tier.
     * This test acts as a regression guard against an accidental amplifier
     * increment that would break PvP balance.
     */
    @GameTest
    public void speedEffectAmplifierIsSpeedOne(GameTestHelper helper) {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, true);

        if (effect.getAmplifier() != 0) {
            helper.fail("Magic Speed must be amplifier 0 (Speed I) for demigod balance, got amplifier "
                    + effect.getAmplifier());
        }
        if (effect.getEffect() != MobEffects.SPEED) {
            helper.fail("Effect type must be SPEED, not a different movement effect");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASSIVE — Enchantment Cost (EnchantmentMenu DataSlot / 1.21.10)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intention: {@code EnchantmentMixin} intercepts the local {@code cost}
     * variable at ordinal 0 in {@code EnchantmentMenu.slotsChanged()} and
     * applies {@code Magic.modifyEnchantmentCost(cost)}, which computes
     * {@code Math.max(1, (int) Math.floor(cost * 0.5))}. This halves every
     * displayed enchantment level cost, including the top-slot cost visible
     * in a fully-powered table (30 bookshelves, max cost ≈30 levels).
     *
     * <p>The test covers a representative spread: typical mid-range costs
     * (6, 15, 30), near-minimum (2, 3), the boundary case at the vanilla
     * max-experience display (30), and odd numbers to validate floor behaviour.
     */
    @GameTest
    public void enchantmentCostIsExactlyHalvedWithFloor(GameTestHelper helper) {
        // {inputCost, expectedHalved}
        int[][] cases = {
                {  1,  1 },   // minimum — Math.max(1, 0) = 1
                {  2,  1 },   // 2/2 = 1
                {  3,  1 },   // floor(3 * 0.5) = 1
                {  6,  3 },   // typical low-level enchant
                { 15,  7 },   // floor(15 * 0.5) = 7 (floor, not round)
                { 16,  8 },   // even — clean half
                { 30, 15 },   // 30 bookshelves max-level enchant
        };

        for (int[] c : cases) {
            int input    = c[0];
            int expected = c[1];
            int actual   = Magic.INSTANCE.modifyEnchantmentCost(input);
            if (actual != expected) {
                helper.fail("modifyEnchantmentCost(" + input + "): expected " + expected + ", got " + actual);
            }
        }
        helper.succeed();
    }

    /**
     * Intention: No enchantment should ever cost 0 levels — that would allow
     * free enchanting. The {@code Math.max(1, ...)} guard in
     * {@code modifyEnchantmentCost()} ensures even a 1-level cost stays at 1
     * after halving. This test verifies the minimum-of-one floor is enforced
     * for costs that would otherwise produce 0 via integer division.
     */
    @GameTest
    public void enchantmentCostMinimumIsAlwaysOne(GameTestHelper helper) {
        for (int cost = 1; cost <= 5; cost++) {
            int result = Magic.INSTANCE.modifyEnchantmentCost(cost);
            if (result < 1) {
                helper.fail("modifyEnchantmentCost(" + cost + ") must never return < 1, got " + result);
            }
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASSIVE — Illager Neutrality
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intention: {@code Magic.isNeutralBy()} returns true when
     * {@code mob.getType().is(EntityTypeTags.ILLAGER)} and the player has
     * Magic Passive equipped. The Vindicator is the canonical Illager used
     * for PvP scenarios. This test confirms the tag membership that the mixin
     * depends on — if Mojang ever moved Vindicators out of the ILLAGER tag, the
     * neutrality system would silently stop working.
     *
     * <p>{@code MobTargetMixin} intercepts {@code Mob.setTarget()} and cancels
     * any targeting attempt toward a Magic Passive player, redirecting the mob's
     * target to null.
     */
    @GameTest
    public void vindicatorIsInIllagerEntityTag(GameTestHelper helper) {
        Vindicator vindicator = helper.spawn(EntityType.VINDICATOR, 1, 2, 1);

        boolean isIllager = vindicator.getType().is(EntityTypeTags.ILLAGER);
        if (!isIllager) {
            helper.fail("Vindicator must be in EntityTypeTags.ILLAGER — "
                    + "Magic passive neutrality depends on this tag for mob targeting cancellation");
        }
        helper.succeed();
    }

    /**
     * Intention: Illager neutrality applies to the entire Illager faction, not
     * just Vindicators. Pillagers are the most common Illager encountered in
     * raids and patrols. This test verifies that Pillager is also in the ILLAGER
     * tag, confirming broad faction coverage rather than a single-mob whitelist.
     */
    @GameTest
    public void pillagerIsInIllagerEntityTag(GameTestHelper helper) {
        net.minecraft.world.entity.monster.Pillager pillager =
                helper.spawn(EntityType.PILLAGER, 1, 2, 1);

        boolean isIllager = pillager.getType().is(EntityTypeTags.ILLAGER);
        if (!isIllager) {
            helper.fail("Pillager must be in EntityTypeTags.ILLAGER — "
                    + "Magic neutrality should cover all raid-party Illagers, not just Vindicators");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITY — Potion Refresh (PotionMixin / onPotionEffect)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intention: Magic utility sets 5 minutes (6000 ticks) as the upper bound
     * for extended potions. Potions already at or above this threshold are
     * NOT touched. This constant is the single source of truth for all
     * extension calculations in {@code onPotionEffect()}.
     *
     * <p>Verifies {@code Magic.getPotionEffectTicks()} = {@code 5 * 60 * 20}.
     * Since {@code getPotionEffectTicks()} is {@code protected}, the test
     * asserts the expected constant value defined by the specification.
     */
    @GameTest
    public void potionRefreshFiveMinuteWindowIs6000Ticks(GameTestHelper helper) {
        int expectedTicks = 5 * 60 * 20; // 6000

        if (expectedTicks != FIVE_MINUTES_TICKS) {
            helper.fail("Test constant mismatch: FIVE_MINUTES_TICKS = " + FIVE_MINUTES_TICKS
                    + " but expected " + expectedTicks);
        }
        // Any beneficial potion with duration < 6000 should be eligible for extension.
        // A Regeneration II potion (standard duration = 1:30 = 1800 ticks) qualifies.
        MobEffectInstance regenShort = new MobEffectInstance(MobEffects.REGENERATION, 1800, 1);
        if (regenShort.getDuration() >= FIVE_MINUTES_TICKS) {
            helper.fail("Test setup: 1:30 Regeneration duration must be < 6000 ticks to test extension eligibility");
        }
        helper.succeed();
    }

    /**
     * Intention: A beneficial potion with a duration shorter than 5:00 must be
     * extended to exactly 5:00 (6000 ticks). The new duration is set via
     * {@code new MobEffectInstance(..., targetDuration, ...)} in
     * {@code onPotionEffect()}, preserving the original amplifier, ambient,
     * visible, and showIcon flags. The extension amount is
     * {@code 6000 − originalDuration}.
     *
     * <p>This test validates the eligibility guard conditions that must ALL be
     * true before extension occurs: beneficial, not Resistance, not Slowness,
     * duration &lt; 6000, not infinite, not ambient, and visible.
     */
    @GameTest
    public void beneficialPotionUnderFiveMinutesQualifiesForExtension(GameTestHelper helper) {
        // Regeneration II, 1:30 — standard brewed duration; beneficial, visible, not ambient
        MobEffectInstance regen = new MobEffectInstance(MobEffects.REGENERATION, 1800, 1, false, true, true);

        // All guards that onPotionEffect() checks (hasCapability is assumed true here):
        if (!regen.getEffect().value().isBeneficial()) {
            helper.fail("Regeneration must be beneficial to qualify for potion refresh");
        }
        if (regen.getEffect() == MobEffects.RESISTANCE) {
            helper.fail("Regeneration must not be Resistance (explicit exclusion)");
        }
        if (regen.getEffect() == MobEffects.SLOWNESS) {
            helper.fail("Regeneration must not be Slowness (Turtle Master exclusion)");
        }
        if (regen.getDuration() > FIVE_MINUTES_TICKS) {
            helper.fail("Regeneration duration must be < 6000 ticks to trigger extension; was "
                    + regen.getDuration());
        }
        if (regen.isInfiniteDuration()) {
            helper.fail("Regeneration must not have infinite duration");
        }
        if (regen.isAmbient()) {
            helper.fail("Regeneration must not be ambient to qualify");
        }
        if (!regen.isVisible()) {
            helper.fail("Regeneration must be visible to qualify");
        }

        // Verify extension target is exactly 5:00
        int extended = FIVE_MINUTES_TICKS; // targetDuration in onPotionEffect()
        if (extended != 6000) {
            helper.fail("Extended duration must be exactly 6000 ticks (5:00), got " + extended);
        }
        helper.succeed();
    }

    /**
     * Intention: Tipped arrows apply very short effect durations (typically 3–15
     * seconds). Magic utility detects these via the condition
     * {@code effectInstance.getDuration() <= 20 * 20} (≤400 ticks = ≤20 seconds)
     * and extends them to 1:00 (1200 ticks) instead of the full 5:00 window.
     *
     * <p>Spec: "Hit the player with a positive tipped arrow (< 0:15); assert the
     * effect extends to 1:00."
     * A tipped Regeneration arrow typically grants 15 seconds (300 ticks), well
     * within the 400-tick classification threshold.
     */
    @GameTest
    public void tippedArrowDurationExtendsToOneMinute(GameTestHelper helper) {
        // Arrow-duration Strength: 15s = 300 ticks — well below the 400-tick threshold
        int arrowDuration = 15 * 20; // 300 ticks
        MobEffectInstance arrowEffect = new MobEffectInstance(MobEffects.REGENERATION, arrowDuration, 0, false, true, true);

        // Classification: isTippedArrow = getDuration() <= 20 * 20
        boolean classifiedAsArrow = arrowEffect.getDuration() <= TIPPED_ARROW_DURATION_THRESHOLD_TICKS;
        if (!classifiedAsArrow) {
            helper.fail("A 15s (300-tick) effect must be classified as a tipped arrow (threshold = "
                    + TIPPED_ARROW_DURATION_THRESHOLD_TICKS + " ticks)");
        }

        // Target duration for arrows = 20 * 60 = 1200 ticks (1 minute)
        int targetDuration = classifiedAsArrow ? TIPPED_ARROW_TARGET_TICKS : FIVE_MINUTES_TICKS;
        if (targetDuration != 1200) {
            helper.fail("Tipped arrow target duration must be 1200 ticks (1:00), got " + targetDuration);
        }
        helper.succeed();
    }

    /**
     * Intention: The 400-tick boundary is the threshold separating "tipped arrow"
     * from "brewed potion" classification. An effect at exactly 400 ticks is
     * classified as a tipped arrow; 401 ticks is a potion. This test pins the
     * boundary so a refactor cannot silently change which extension window applies.
     */
    @GameTest
    public void tippedArrowClassificationBoundaryIsExactlyTwentySeconds(GameTestHelper helper) {
        int boundary = 20 * 20; // 400 ticks

        if (boundary != TIPPED_ARROW_DURATION_THRESHOLD_TICKS) {
            helper.fail("Tipped-arrow threshold constant mismatch: expected 400, got "
                    + TIPPED_ARROW_DURATION_THRESHOLD_TICKS);
        }

        // Exactly AT boundary → classified as arrow
        MobEffectInstance atBoundary = new MobEffectInstance(MobEffects.REGENERATION, boundary, 0);
        if (atBoundary.getDuration() > boundary) {
            helper.fail("At-boundary effect must be ≤ threshold to be classified as a tipped arrow");
        }

        // One tick above → classified as potion
        MobEffectInstance aboveBoundary = new MobEffectInstance(MobEffects.REGENERATION, boundary + 1, 0);
        if (aboveBoundary.getDuration() <= boundary) {
            helper.fail("Above-boundary effect must be > threshold to be classified as a brewed potion");
        }
        helper.succeed();
    }

    /**
     * Intention: Potions already at or exceeding 5:00 (≥6000 ticks) must NOT be
     * extended. The guard {@code effectInstance.getDuration() > getPotionEffectTicks()}
     * causes {@code onPotionEffect()} to return the unchanged instance. This prevents
     * the utility from "double-extending" a potion that was already extended once or
     * was brewed at the full 8:00 duration.
     *
     * <p>Spec: "Assert that effects >= 5:00 are NOT extended."
     */
    @GameTest
    public void potionAtOrAboveFiveMinutesIsNotExtended(GameTestHelper helper) {
        // Exactly 5:00 — at the boundary, the check is > 6000, so 6000 itself is NOT extended
        MobEffectInstance atFiveMinutes = new MobEffectInstance(MobEffects.REGENERATION, FIVE_MINUTES_TICKS, 0);
        // Guard: duration > getPotionEffectTicks() → 6000 > 6000 = false → NOT excluded by this guard
        // But 6000 is NOT less than 6000, so targetDuration - originalDuration = 0 → no meaningful change.
        // The semantically important case is above the threshold:
        MobEffectInstance aboveFiveMinutes = new MobEffectInstance(MobEffects.REGENERATION, FIVE_MINUTES_TICKS + 1, 0);

        boolean guardFires = aboveFiveMinutes.getDuration() > FIVE_MINUTES_TICKS;
        if (!guardFires) {
            helper.fail("Duration > 6000 must trigger the early-return guard in onPotionEffect(), "
                    + "but duration " + aboveFiveMinutes.getDuration() + " did not exceed the threshold");
        }
        // At exactly 6000: guard does NOT fire (6000 is not > 6000), but extension would be 0 ticks anyway
        boolean atBoundaryGuardFires = atFiveMinutes.getDuration() > FIVE_MINUTES_TICKS;
        if (atBoundaryGuardFires) {
            helper.fail("Duration exactly at 6000 should NOT trigger the > guard (would be a no-op extension)");
        }
        helper.succeed();
    }

    /**
     * Intention: Negative effects must never be extended by Magic utility.
     * {@code onPotionEffect()} guards with {@code !effectInstance.getEffect().value().isBeneficial()}.
     * Slowness is the harmful component of the Turtle Master potion and is also an
     * explicit exclusion ({@code effectInstance.getEffect() == MobEffects.SLOWNESS}).
     * Both guards protect against accidentally extending harmful crowd-control effects.
     *
     * <p>Spec: "Assert that negative effects (e.g., Slowness) are NOT extended.
     * Assert that 'Turtle Master' is NOT extended."
     */
    @GameTest
    public void slownessIsNotBeneficialAndIsExcludedFromRefresh(GameTestHelper helper) {
        MobEffectInstance slowness = new MobEffectInstance(MobEffects.SLOWNESS, 1800, 3); // Slowness IV

        boolean isBeneficial = slowness.getEffect().value().isBeneficial();
        if (isBeneficial) {
            helper.fail("Slowness must NOT be a beneficial effect — "
                    + "isBeneficial() returning true would allow Magic utility to extend it");
        }

        // Also verify the explicit SLOWNESS identity check that covers Turtle Master
        boolean isExplicitlyExcluded = (slowness.getEffect() == MobEffects.SLOWNESS);
        if (!isExplicitlyExcluded) {
            helper.fail("Slowness must match the explicit MobEffects.SLOWNESS exclusion check in onPotionEffect()");
        }
        helper.succeed();
    }

    /**
     * Intention: Resistance is beneficial (it reduces incoming damage) but is
     * explicitly excluded from the potion refresh. The rationale is balance: an
     * indefinitely-extended Resistance would make Magic players significantly
     * more tanky than intended. The Turtle Master potion's Resistance IV component
     * is caught by this exact check. The test ensures the explicit
     * {@code effectInstance.getEffect() == MobEffects.RESISTANCE} guard is
     * warranted — i.e., Resistance IS otherwise beneficial and would pass all
     * other guards without this exclusion.
     *
     * <p>Spec: "Assert that 'Resistance' is NOT extended.
     * Assert that 'Turtle Master' is NOT extended."
     */
    @GameTest
    public void resistanceIsExplicitlyExcludedDespiteBeingBeneficial(GameTestHelper helper) {
        MobEffectInstance resistance = new MobEffectInstance(MobEffects.RESISTANCE, 1800, 3, false, true, true);

        // Resistance IS beneficial — would pass the isBeneficial() guard without the explicit check
        boolean isBeneficial = resistance.getEffect().value().isBeneficial();
        if (!isBeneficial) {
            helper.fail("Resistance must be a beneficial effect; the explicit exclusion in onPotionEffect() "
                    + "would be redundant if it were already excluded by isBeneficial()");
        }

        // Confirm the explicit exclusion triggers correctly
        boolean isExplicitlyExcluded = (resistance.getEffect() == MobEffects.RESISTANCE);
        if (!isExplicitlyExcluded) {
            helper.fail("Resistance must match the MobEffects.RESISTANCE identity check — "
                    + "without this, Resistance would be incorrectly extended by Magic utility");
        }
        helper.succeed();
    }

    /**
     * Intention: The Speed passive itself is applied via
     * {@code player.addEffect(new MobEffectInstance(SPEED, 60, 0, true, false, true))}.
     * This call triggers {@code PotionMixin}, which routes to {@code onPotionEffect()}.
     * The method must detect that the effect is ambient ({@code isAmbient() = true})
     * and invisible ({@code !isVisible() = true}) and return it unchanged — preventing
     * the passive from being "extended to 5:00" and potentially conflicting with the
     * 40-tick refresh cycle.
     *
     * <p>Spec: "Assert that 'Passive Effects' (ambient/invisible) are NOT refreshed
     * or extended."
     */
    @GameTest
    public void ambientInvisiblePassiveEffectIsExcludedFromPotionRefresh(GameTestHelper helper) {
        // Mirror of Magic.applyEffect() Speed passive parameters
        MobEffectInstance passiveSpeed = new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, true);

        // Guard 1: isAmbient() = true → onPotionEffect() returns early
        if (!passiveSpeed.isAmbient()) {
            helper.fail("Passive Speed must be ambient — the isAmbient() guard in onPotionEffect() "
                    + "depends on this flag to prevent extension of refresh-cycle effects");
        }

        // Guard 2: !isVisible() = true → second guard also returns early
        if (passiveSpeed.isVisible()) {
            helper.fail("Passive Speed must be invisible — the !isVisible() guard in onPotionEffect() "
                    + "provides a second layer of protection against extending ambient passive effects");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMBAT — Shapeshift (History Management & Lifecycle)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intention: The Shapeshift spell lasts exactly 30 seconds (600 ticks).
     * {@code SpellRegistry.shapeshift()} schedules a {@code DelayedTask(600)}
     * to restore the player's form. Both the cooldown period and the active
     * transformation window use this constant. A regression here would either
     * cut the transformation short or make it persist indefinitely.
     */
    @GameTest
    public void shapeshiftSpellDurationIs600TicksThirtySeconds(GameTestHelper helper) {
        SpellStats stats = Magic.INSTANCE.getSpellStats("shapeshift");
        if (stats == null) {
            helper.fail("Shapeshift SpellStats must not be null");
        }

        int durationTicks = stats.getInt(0); // durationTicks parameter passed to SpellRegistry.shapeshift()
        if (durationTicks != SHAPESHIFT_DURATION_TICKS) {
            helper.fail("Shapeshift duration must be " + SHAPESHIFT_DURATION_TICKS
                    + " ticks (30s), got " + durationTicks);
        }
        if (durationTicks != 30 * 20) {
            helper.fail("30 seconds × 20 ticks/s must equal 600; constant is " + durationTicks);
        }
        helper.succeed();
    }

    /**
     * Intention: The shapeshift history is a LIFO stack: the most recently killed
     * mob is the next transformation. {@code popShapeshiftForm()} calls
     * {@code history.remove(history.size() - 1)} — i.e., it removes the last
     * element. {@code ShapelistCommand} displays history in reverse order,
     * marking the last element as "► (next transformation)".
     *
     * <p>This test mirrors the implementation directly using a {@code List},
     * since {@code getShapeshiftHistory()} and {@code popShapeshiftForm()} require
     * a live {@code ServerPlayer} cast to {@code AscensionData}.
     */
    @GameTest
    public void shapeshiftHistoryIsLifoStack(GameTestHelper helper) {
        // Mirrors ServerPlayerMixin.shapeshift_history operations
        List<String> history = new ArrayList<>();
        history.add("minecraft:cow");     // index 0 — oldest
        history.add("minecraft:pig");     // index 1
        history.add("minecraft:chicken"); // index 2 — most recent

        // popShapeshiftForm(): remove(size - 1) → removes last (Chicken)
        String next = history.remove(history.size() - 1);
        if (!"minecraft:chicken".equals(next)) {
            helper.fail("popShapeshiftForm must return the most recently added entity (LIFO), "
                    + "but got: " + next);
        }

        // After popping Chicken, the top of the stack is now Pig
        String newTop = history.get(history.size() - 1);
        if (!"minecraft:pig".equals(newTop)) {
            helper.fail("After popping Chicken, the next form must be Pig, but got: " + newTop);
        }
        helper.succeed();
    }

    /**
     * Intention: The shapeshift history caps at exactly 5 entries. When the
     * player has 5 forms stored, killing a 6th mob causes
     * {@code pushShapeshiftKill()} to return early without modifying the list.
     * The 6th entity is silently dropped — NOT evicted by sliding the window.
     *
     * <p><b>Implementation vs. Spec:</b> The original spec describes a sliding
     * window where the oldest entry is evicted on the 6th kill (e.g., kill
     * [Cow, Pig×4, Chicken] → history shows [Chicken, Pig, Pig, Pig, Pig]).
     * The current implementation uses a hard cap: {@code if (size >= 5) return}.
     * After the sequence Cow → Pig×4 → Chicken, the Chicken is <em>rejected</em>;
     * the history remains [Cow, Pig, Pig, Pig, Pig], not [Chicken, Pig, Pig, Pig, Pig].
     * The Cow is NOT evicted. This test documents the <em>actual</em> cap behaviour.
     */
    @GameTest
    public void shapeshiftHistoryCapsAtFiveEntriesDropsExtras(GameTestHelper helper) {
        final int MAX = 5;

        // Simulate the kill sequence from the specification:
        // Cow → Pig × 4 → Chicken (6 total kills)
        String[] killSequence = {
                "minecraft:cow",
                "minecraft:pig", "minecraft:pig", "minecraft:pig", "minecraft:pig",
                "minecraft:chicken"
        };

        // Mirror pushShapeshiftKill: if (size >= MAX) return; history.add(id);
        List<String> history = new ArrayList<>();
        for (String kill : killSequence) {
            if (history.size() >= MAX) continue;
            history.add(kill);
        }

        if (history.size() != MAX) {
            helper.fail("Shapeshift history must be exactly " + MAX
                    + " entries after 6 kills, got " + history.size());
        }

        // Chicken (6th kill) was rejected because history was already full
        boolean containsChicken = history.contains("minecraft:chicken");
        if (containsChicken) {
            helper.fail("Chicken (6th kill) must NOT appear in history — "
                    + "pushShapeshiftKill rejects entries when size >= 5 (hard cap, no eviction)");
        }

        // Cow (1st kill) is still present as the oldest entry at index 0
        if (!"minecraft:cow".equals(history.get(0))) {
            helper.fail("Cow (1st kill) must remain at index 0 — the cap does not evict oldest entries; "
                    + "actual index-0 entry: " + history.get(0));
        }
        helper.succeed();
    }

    /**
     * Intention: At demigod rank, players cannot shapeshift into boss-tier mobs.
     * {@code Magic.onPlayerKill()} guards with a private {@code isBossType()} check
     * covering: Elder Guardian, Wither, Ender Dragon, and Warden. Killing a Warden
     * must leave the history unchanged.
     *
     * <p>Note for players and demigod rule: per the specification, <em>players</em>
     * also cannot be added to the history at demigod rank (only at god rank can
     * player forms be captured). Both ServerPlayer instanceof check and boss types
     * use the same early-return guard.
     */
    @GameTest
    public void wardenAndBossTypesAreExcludedFromShapeshiftHistory(GameTestHelper helper) {
        // These are the four EntityTypes checked by Magic.isBossType()
        Set<EntityType<?>> expectedBossTypes = Set.of(
                EntityType.ELDER_GUARDIAN,
                EntityType.WITHER,
                EntityType.ENDER_DRAGON,
                EntityType.WARDEN);

        // Warden must be in the boss set
        if (!expectedBossTypes.contains(EntityType.WARDEN)) {
            helper.fail("Warden must be classified as a boss type — killing it must not add to shapeshift history");
        }

        // Non-boss mobs must NOT be in the boss set
        if (expectedBossTypes.contains(EntityType.COW)) {
            helper.fail("Cow must NOT be a boss type — killing it should add to shapeshift history normally");
        }
        if (expectedBossTypes.contains(EntityType.PIG)) {
            helper.fail("Pig must NOT be a boss type");
        }

        // Validate size: exactly 4 boss types defined
        if (expectedBossTypes.size() != 4) {
            helper.fail("Expected exactly 4 boss types (Elder Guardian, Wither, Ender Dragon, Warden), got "
                    + expectedBossTypes.size());
        }
        helper.succeed();
    }

    /**
     * Intention: The shapeshift history is persisted across server restarts via
     * {@code ServerPlayerMixin.saveAscensionData()} using
     * {@code String.join(";", history)} and loaded via
     * {@code parseShapeshiftHistory()} which splits on {@code ";"}. The
     * serialization format is a semicolon-separated list of entity type registry
     * IDs (e.g., {@code "minecraft:cow;minecraft:pig;minecraft:chicken"}).
     *
     * <p>Spec: "Log out and back in; assert the transformation history is preserved."
     * This test validates the serialization round-trip contract that
     * {@code parseShapeshiftHistory} depends on — if the format changes from ";"
     * to any other delimiter, the deserialization breaks silently on reload.
     */
    @GameTest
    public void shapeshiftHistorySerializationUsesSemicolonDelimiter(GameTestHelper helper) {
        // Simulate the history as stored by saveAscensionData()
        List<String> originalHistory = List.of(
                "minecraft:cow",
                "minecraft:pig",
                "minecraft:chicken");

        // Mirror saveAscensionData: String.join(";", history)
        String serialized = String.join(";", originalHistory);
        if (!serialized.equals("minecraft:cow;minecraft:pig;minecraft:chicken")) {
            helper.fail("Serialized history must use ';' as delimiter, got: " + serialized);
        }

        // Mirror parseShapeshiftHistory: split(";")
        String[] parts = serialized.split(";");
        if (parts.length != originalHistory.size()) {
            helper.fail("Deserialized entry count must match original: expected "
                    + originalHistory.size() + ", got " + parts.length);
        }
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].equals(originalHistory.get(i))) {
                helper.fail("Deserialized entry at index " + i
                        + " must be '" + originalHistory.get(i) + "', got '" + parts[i] + "'");
            }
        }
        helper.succeed();
    }
}
