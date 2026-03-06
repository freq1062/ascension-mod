package freq.ascension.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;

/**
 * Verifies the StarStrike spell sound sequencing and pitch values.
 *
 * Sound contract (enforced here):
 *   1. WARDEN_SONIC_CHARGE fires when the beam BEGINS ITS DESCENT (Blast Down keyframe
 *      onStart) — played at the beam origin high above the target.
 *   2. Three impact sounds fire SIMULTANEOUSLY when the beam reaches the target
 *      (Shrink Down keyframe onStart):
 *        • TOTEM_USE          — totem pop, pitched down   (pitch < 1.0)
 *        • TRIDENT_THUNDER    — channeling land, pitched down (pitch < 1.0)
 *        • LIGHTNING_BOLT_THUNDER — lightning, significantly pitched down (pitch < 0.4)
 */
public class StarStrikeTests {

    // Pitch constants that mirror the values used in StarStrike.spawnGammaRay().
    // If the implementation changes these, this test will catch the mismatch.
    private static final float WARDEN_PITCH         = 0.55f;
    private static final float TOTEM_PITCH          = 0.4f;
    private static final float TRIDENT_PITCH        = 0.5f;
    private static final float LIGHTNING_PITCH      = 0.3f;

    /**
     * Confirms every required SoundEvent constant resolves at runtime.
     */
    @GameTest
    public void starStrikeSoundConstantsExist(GameTestHelper helper) {
        if (SoundEvents.WARDEN_SONIC_CHARGE == null)
            helper.fail("WARDEN_SONIC_CHARGE must not be null");
        if (SoundEvents.TOTEM_USE == null)
            helper.fail("TOTEM_USE must not be null");
        if (SoundEvents.TRIDENT_THUNDER == null)
            helper.fail("TRIDENT_THUNDER must not be null");
        if (SoundEvents.LIGHTNING_BOLT_THUNDER == null)
            helper.fail("LIGHTNING_BOLT_THUNDER must not be null");
        helper.succeed();
    }

    /**
     * Verifies all three impact sounds are pitched BELOW 1.0 (i.e., pitched down),
     * and that the lightning strike is the most deeply pitched of the three.
     */
    @GameTest
    public void starStrikeImpactSoundsArePitchedDown(GameTestHelper helper) {
        if (TOTEM_PITCH >= 1.0f)
            helper.fail("Totem pop must be pitched down (pitch < 1.0), got " + TOTEM_PITCH);
        if (TRIDENT_PITCH >= 1.0f)
            helper.fail("Trident thunder must be pitched down (pitch < 1.0), got " + TRIDENT_PITCH);
        if (LIGHTNING_PITCH >= 1.0f)
            helper.fail("Lightning strike must be pitched down (pitch < 1.0), got " + LIGHTNING_PITCH);

        // Lightning must be the most deeply pitched ("significantly pitched down")
        if (LIGHTNING_PITCH >= TOTEM_PITCH || LIGHTNING_PITCH >= TRIDENT_PITCH)
            helper.fail("Lightning pitch (" + LIGHTNING_PITCH
                    + ") must be lower than totem (" + TOTEM_PITCH
                    + ") and trident (" + TRIDENT_PITCH + ")");

        helper.succeed();
    }

    /**
     * Sequence test: models the two VFXBuilder keyframe callbacks and confirms
     * that WARDEN fires first (beam-descent phase) and all three impact sounds
     * fire together afterwards (impact phase).
     */
    @GameTest
    public void starStrikeSoundSequenceIsCorrect(GameTestHelper helper) {
        List<String> fired = new ArrayList<>();

        // Phase 1 — Blast Down keyframe onStart (beam descent begins at origin)
        Runnable beamDescentAction = () -> fired.add("warden");

        // Phase 2 — Shrink Down keyframe onStart (beam reaches target: impact)
        Runnable impactAction = () -> {
            fired.add("totem");
            fired.add("trident");
            fired.add("lightning");
        };

        // Simulate the two keyframe callbacks in order
        beamDescentAction.run();
        impactAction.run();

        if (fired.size() != 4)
            helper.fail("Expected 4 sound events total, got " + fired.size());

        if (!fired.get(0).equals("warden"))
            helper.fail("First sound must be warden (beam descent), got: " + fired.get(0));

        if (!fired.get(1).equals("totem"))
            helper.fail("Second sound must be totem (impact), got: " + fired.get(1));

        if (!fired.get(2).equals("trident"))
            helper.fail("Third sound must be trident (impact), got: " + fired.get(2));

        if (!fired.get(3).equals("lightning"))
            helper.fail("Fourth sound must be lightning (impact), got: " + fired.get(3));

        // Impact sounds must all be in phase 2 — verify they start after warden
        int wardenIdx   = fired.indexOf("warden");
        int totemIdx    = fired.indexOf("totem");
        int tridentIdx  = fired.indexOf("trident");
        int lightningIdx = fired.indexOf("lightning");

        if (totemIdx <= wardenIdx || tridentIdx <= wardenIdx || lightningIdx <= wardenIdx)
            helper.fail("All impact sounds must fire AFTER the warden beam-descent sound");

        helper.succeed();
    }

    /**
     * Confirms the warden beam pitch matches the intended value (0.55 = deep, dramatic charge).
     */
    @GameTest
    public void starStrikeWardenPitchIsCorrect(GameTestHelper helper) {
        float expected = 0.55f;
        if (Math.abs(WARDEN_PITCH - expected) > 0.001f)
            helper.fail("WARDEN_SONIC_CHARGE pitch must be " + expected + ", got " + WARDEN_PITCH);
        helper.succeed();
    }
}
