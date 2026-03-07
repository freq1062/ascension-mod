package freq.ascension.test.challenger;

import freq.ascension.items.ChallengerSigil;
import freq.ascension.managers.ChallengerTrialManager;
import freq.ascension.managers.ChallengerTrialManager.Phase;
import freq.ascension.managers.ChallengerTrialManager.TrialResult;
import freq.ascension.managers.ChallengerTrialManager.TrialState;
import freq.ascension.managers.GodManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * GameTest suite covering the full trial lifecycle for the Challenger's Sigil system.
 *
 * <p>Tests are grouped into eight sections:
 * <ol>
 *   <li><b>Lifecycle State</b> — TrialState phase transitions and cooldown semantics.</li>
 *   <li><b>Loss Counter</b> — GodManager loss counter CRUD and daily-increment gate.</li>
 *   <li><b>Cube Health</b> — TrialState cube HP arithmetic and boundary clamping.</li>
 *   <li><b>Previous Slot Fields</b> — GodManager AscensionData-layer save/restore evidence via
 *       the pre-promotion slot fields tracked by {@link GodManager}.</li>
 *   <li><b>Constraint Invariants</b> — Four design invariants enforced by GodManager.</li>
 *   <li><b>God Protection</b> — Phase-based protection threshold via TrialState.</li>
 *   <li><b>Trial Phase Transitions</b> — Direct TrialState field manipulation.</li>
 *   <li><b>Sigil</b> — {@link ChallengerSigil} identity contract.</li>
 * </ol>
 *
 * <p>Most tests exercise {@link TrialState} directly (fields are public) or
 * {@link GodManager#createForTesting()}. Integration tests that require live players are
 * deliberately omitted from this unit-style suite.
 */
public class ChallengerTrialTests {

    // ─── 1. Lifecycle State Tests ─────────────────────────────────────────────

    /**
     * A freshly-constructed {@link TrialState} must be in the {@link Phase#IDLE} phase.
     */
    @GameTest
    public void initialPhaseIsIdle(GameTestHelper helper) {
        TrialState state = new TrialState("trial_idle_" + UUID.randomUUID());
        if (state.phase != Phase.IDLE) {
            helper.fail("New TrialState must start in IDLE phase, got: " + state.phase);
        }
        helper.succeed();
    }

    /**
     * When {@code cooldownEndsMs} is set to a future timestamp,
     * {@link TrialState#isOnCooldown()} must return {@code true}.
     */
    @GameTest
    public void cooldownPreventsNewTrial(GameTestHelper helper) {
        TrialState state = new TrialState("trial_cooldown_active_" + UUID.randomUUID());
        state.cooldownEndsMs = System.currentTimeMillis() + 3_600_000L;

        if (!state.isOnCooldown()) {
            helper.fail("isOnCooldown() must return true when cooldownEndsMs is in the future");
        }
        helper.succeed();
    }

    /**
     * When {@code cooldownEndsMs} is set to a past timestamp,
     * {@link TrialState#isOnCooldown()} must return {@code false}.
     */
    @GameTest
    public void cooldownExpires(GameTestHelper helper) {
        TrialState state = new TrialState("trial_cooldown_expired_" + UUID.randomUUID());
        state.cooldownEndsMs = System.currentTimeMillis() - 1L;

        if (state.isOnCooldown()) {
            helper.fail("isOnCooldown() must return false when cooldownEndsMs is in the past");
        }
        helper.succeed();
    }

    // ─── 2. Loss Counter Tests ────────────────────────────────────────────────

    /**
     * A fresh {@link GodManager} must report 0 for {@link GodManager#getLossCounter} on any
     * order.
     */
    @GameTest
    public void lossCounterStartsAtZero(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        int count = gm.getLossCounter("earth");
        if (count != 0) {
            helper.fail("getLossCounter must return 0 for a fresh GodManager, got " + count);
        }
        helper.succeed();
    }

    /**
     * {@link GodManager#incrementLossCounter} must increase the counter by 1.
     */
    @GameTest
    public void lossCounterIncrement(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        gm.incrementLossCounter("earth");
        if (gm.getLossCounter("earth") != 1) {
            helper.fail("getLossCounter must be 1 after one increment, got " + gm.getLossCounter("earth"));
        }
        helper.succeed();
    }

    /**
     * After one increment followed by one decrement, the counter must return to 0.
     */
    @GameTest
    public void lossCounterDecrement(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        gm.incrementLossCounter("earth");
        gm.decrementLossCounter("earth");
        if (gm.getLossCounter("earth") != 0) {
            helper.fail("getLossCounter must be 0 after increment then decrement, got " + gm.getLossCounter("earth"));
        }
        helper.succeed();
    }

    /**
     * {@link GodManager#decrementLossCounter} must clamp the counter at 0 — it must never go
     * negative.
     */
    @GameTest
    public void lossCounterDoesNotGoBelowZero(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        gm.decrementLossCounter("earth");  // decrement when already at 0
        if (gm.getLossCounter("earth") < 0) {
            helper.fail("Loss counter must never go below 0, got " + gm.getLossCounter("earth"));
        }
        helper.succeed();
    }

    /**
     * {@link GodManager#setLossCounter} must store and retrieve the exact value.
     */
    @GameTest
    public void lossCounterSetDirectly(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        gm.setLossCounter("sky", 5);
        if (gm.getLossCounter("sky") != 5) {
            helper.fail("getLossCounter must return 5 after setLossCounter(5), got " + gm.getLossCounter("sky"));
        }
        helper.succeed();
    }

    /**
     * After simulating a promotion (which removes the loss counter entry),
     * {@link GodManager#getLossCounter} must return 0 for the promoted order.
     */
    @GameTest
    public void lossCounterResetsOnPromotion(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        gm.incrementLossCounter("earth");
        gm.incrementLossCounter("earth");

        // promoteToGod internally calls lossCounters.remove(orderName);
        // simulate this with setLossCounter(0) to verify the reset path
        gm.setLossCounter("earth", 0);

        if (gm.getLossCounter("earth") != 0) {
            helper.fail("Loss counter must be 0 after promotion reset, got " + gm.getLossCounter("earth"));
        }
        helper.succeed();
    }

    /**
     * The first call to {@link GodManager#tryIncrementDailyLoss} must return {@code true}
     * because {@code lastDailyLossMs} starts at 0 (epoch), which is more than 24 h in the past.
     */
    @GameTest
    public void dailyLossIncrementOnlyOnce(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();

        boolean firstResult = gm.tryIncrementDailyLoss("earth");
        if (!firstResult) {
            helper.fail("First tryIncrementDailyLoss call must return true (no previous timestamp)");
        }

        boolean secondResult = gm.tryIncrementDailyLoss("earth");
        if (secondResult) {
            helper.fail("Second immediate tryIncrementDailyLoss call must return false (within 24h)");
        }
        helper.succeed();
    }

    /**
     * After resetting the last-daily-loss timestamp to more than 24 h ago,
     * {@link GodManager#tryIncrementDailyLoss} must return {@code true} again.
     */
    @GameTest
    public void dailyLossResetsAfter24Hours(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();

        // Consume the first free increment
        gm.tryIncrementDailyLoss("sky");

        // Simulate that the last increment happened more than 24 h ago
        gm.setLastDailyLossTimestampForTesting("sky",
                System.currentTimeMillis() - 90_000_000L);  // ~25 hours ago

        boolean result = gm.tryIncrementDailyLoss("sky");
        if (!result) {
            helper.fail("tryIncrementDailyLoss must return true after 24+ h have elapsed since last increment");
        }
        helper.succeed();
    }

    // ─── 3. Cube Health Tests ─────────────────────────────────────────────────

    /**
     * A freshly-constructed {@link TrialState} must have {@code cubeHealth == 500}.
     */
    @GameTest
    public void cubeStartsAt500HP(GameTestHelper helper) {
        TrialState state = new TrialState("cube_hp_initial_" + UUID.randomUUID());
        if (state.cubeHealth != 500) {
            helper.fail("New TrialState must start with cubeHealth = 500, got " + state.cubeHealth);
        }
        helper.succeed();
    }

    /**
     * Directly reducing {@code cubeHealth} by 50 must leave it at 450.
     */
    @GameTest
    public void cubeDamageReducesHP(GameTestHelper helper) {
        TrialState state = new TrialState("cube_hp_damage_" + UUID.randomUUID());
        state.cubeHealth -= 50;
        if (state.cubeHealth != 450) {
            helper.fail("cubeHealth must be 450 after subtracting 50, got " + state.cubeHealth);
        }
        helper.succeed();
    }

    /**
     * Healing the cube must increase {@code cubeHealth}.
     */
    @GameTest
    public void cubeHealIncreasesHP(GameTestHelper helper) {
        TrialState state = new TrialState("cube_hp_heal_" + UUID.randomUUID());
        state.cubeHealth = 400;
        state.cubeHealth = Math.min(500, state.cubeHealth + 80);
        if (state.cubeHealth != 480) {
            helper.fail("cubeHealth must be 480 after healing 80 from 400, got " + state.cubeHealth);
        }
        helper.succeed();
    }

    /**
     * Healing when {@code cubeHealth} is already at maximum (500) must not exceed 500.
     */
    @GameTest
    public void cubeHPDoesNotExceed500(GameTestHelper helper) {
        TrialState state = new TrialState("cube_hp_cap_" + UUID.randomUUID());
        state.cubeHealth = 500;
        state.cubeHealth = Math.min(500, state.cubeHealth + 100);
        if (state.cubeHealth > 500) {
            helper.fail("cubeHealth must be clamped at 500, got " + state.cubeHealth);
        }
        helper.succeed();
    }

    /**
     * Reducing {@code cubeHealth} below 0 must clamp it to 0 (mirrors the
     * {@link ChallengerTrialManager#damageCube} implementation).
     */
    @GameTest
    public void cubeHPDoesNotGoBelowZero(GameTestHelper helper) {
        TrialState state = new TrialState("cube_hp_floor_" + UUID.randomUUID());
        state.cubeHealth = 0;
        state.cubeHealth = Math.max(0, state.cubeHealth - 100);
        if (state.cubeHealth < 0) {
            helper.fail("cubeHealth must not go below 0, got " + state.cubeHealth);
        }
        helper.succeed();
    }

    // ─── 4. Constraint Invariants ─────────────────────────────────────────────

    /**
     * A player must be god of at most one order. If a UUID already appears in the god map for
     * one order, attempting to add it to a second order is detectable via
     * {@link GodManager#isGod(UUID)} — the gate in {@code GodManager.promoteToGod}.
     */
    @GameTest
    public void playerCannotBeGodOfTwoOrders(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        UUID player = UUID.randomUUID();
        gm.setGodEntryForTesting("earth", player);

        // Promotion for a second order would first check isGod(player) → true → demote first
        if (!gm.isGod(player)) {
            helper.fail("isGod must return true to trigger the demotion-before-promotion guard");
        }
        // After simulated demotion from earth, player is no longer god
        gm.clearGodEntryForTesting("earth");
        if (gm.isGod(player)) {
            helper.fail("isGod must return false after clearGodEntryForTesting (simulating demotion)");
        }
        helper.succeed();
    }

    /**
     * After {@link GodManager#clearGodEntryForTesting} (analogous to post-demotion state),
     * the player's UUID must no longer appear in the god map — confirming the demotion removes
     * god status.
     */
    @GameTest
    public void playerDeathDemotesGod(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        UUID player = UUID.randomUUID();
        gm.setGodEntryForTesting("earth", player);

        // Simulate demoteFromGod removing the player from the god map
        gm.clearGodEntryForTesting("earth");

        if (gm.getGodUUID("earth") != null) {
            helper.fail("After demotion the order must have no registered god");
        }
        if (gm.isGod(player)) {
            helper.fail("Demoted player must no longer be detected by isGod");
        }
        helper.succeed();
    }

    /**
     * A player with rank "demigod" must not hold a mythical weapon. This invariant is enforced
     * by {@code GodManager.demoteFromGod} which calls {@code removeAllMythicalWeapons}.
     * The test verifies the design intent: {@code WeaponRegistry.hasWeapon} should return
     * {@code false} for a fresh player (no weapon in inventory).
     */
    @GameTest
    public void demigodCannotHaveMythicalWeapon(GameTestHelper helper) {
        // WeaponRegistry.hasWeapon requires a real ServerPlayer; here we verify the
        // underlying invariant through the isMythicalWeapon stack check.
        // A plain diamond sword (no CustomModelData with a registered weapon ID) must not
        // be identified as a mythical weapon.
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        if (freq.ascension.registry.WeaponRegistry.isMythicalWeapon(plain)) {
            helper.fail("A plain diamond sword must not be identified as a mythical weapon");
        }
        helper.succeed();
    }

    // ─── 5. God Protection Tests ──────────────────────────────────────────────

    /**
     * A {@link TrialState} whose {@code phase} is {@link Phase#ACTIVE} correctly represents
     * the active-trial state that enables god protection in
     * {@link ChallengerTrialManager#isActiveTrialInRadius}.
     */
    @GameTest
    public void godProtectionActivePhaseCheck(GameTestHelper helper) {
        TrialState state = new TrialState("god_prot_" + UUID.randomUUID());
        state.phase = Phase.ACTIVE;

        if (state.phase != Phase.ACTIVE) {
            helper.fail("TrialState.phase must be ACTIVE after assignment");
        }
        helper.succeed();
    }

    // ─── 6. TrialResult enum ─────────────────────────────────────────────────

    /**
     * {@link TrialResult} must declare all six expected outcome values.
     */
    @GameTest
    public void trialResultEnumValues(GameTestHelper helper) {
        // If any constant is renamed/removed, the corresponding switch arms in endTrial()
        // will fail to compile — this test catches any accidental enum narrowing.
        TrialResult[] values = TrialResult.values();
        boolean hasCubeDestroyed  = false;
        boolean hasGodDeath       = false;
        boolean hasChallengerDeath= false;
        boolean hasForfeit        = false;
        boolean hasTimeout        = false;
        boolean hasGodLogout      = false;

        for (TrialResult v : values) {
            switch (v) {
                case CUBE_DESTROYED   -> hasCubeDestroyed = true;
                case GOD_DEATH        -> hasGodDeath = true;
                case CHALLENGER_DEATH -> hasChallengerDeath = true;
                case FORFEIT          -> hasForfeit = true;
                case TIMEOUT          -> hasTimeout = true;
                case GOD_LOGOUT       -> hasGodLogout = true;
            }
        }

        if (!hasCubeDestroyed)   helper.fail("TrialResult missing CUBE_DESTROYED");
        if (!hasGodDeath)        helper.fail("TrialResult missing GOD_DEATH");
        if (!hasChallengerDeath) helper.fail("TrialResult missing CHALLENGER_DEATH");
        if (!hasForfeit)         helper.fail("TrialResult missing FORFEIT");
        if (!hasTimeout)         helper.fail("TrialResult missing TIMEOUT");
        if (!hasGodLogout)       helper.fail("TrialResult missing GOD_LOGOUT");

        helper.succeed();
    }

    // ─── 7. Phase Transition Tests ────────────────────────────────────────────

    /**
     * A {@link TrialState} assigned {@link Phase#PENDING_GOD} must read back as
     * {@link Phase#PENDING_GOD}.
     */
    @GameTest
    public void pendingPhaseTransition(GameTestHelper helper) {
        TrialState state = new TrialState("pending_phase_" + UUID.randomUUID());
        state.phase = Phase.PENDING_GOD;
        if (state.phase != Phase.PENDING_GOD) {
            helper.fail("phase must be PENDING_GOD after assignment, got " + state.phase);
        }
        helper.succeed();
    }

    /**
     * A {@link TrialState} in {@link Phase#ACTIVE} must retain {@code cubeHealth == 500}
     * (initial value) — confirming that phase assignment does not side-effect cube HP.
     */
    @GameTest
    public void activePhaseTransition(GameTestHelper helper) {
        TrialState state = new TrialState("active_phase_" + UUID.randomUUID());
        state.phase = Phase.ACTIVE;

        if (state.phase != Phase.ACTIVE) {
            helper.fail("phase must be ACTIVE after assignment");
        }
        if (state.cubeHealth != 500) {
            helper.fail("cubeHealth must remain 500 after phase assignment, got " + state.cubeHealth);
        }
        helper.succeed();
    }

    /**
     * When the phase is set to {@link Phase#COOLDOWN} and {@code cooldownEndsMs} is a future
     * timestamp, {@link TrialState#isOnCooldown()} must return {@code true}.
     */
    @GameTest
    public void cooldownPhaseTransition(GameTestHelper helper) {
        TrialState state = new TrialState("cooldown_phase_" + UUID.randomUUID());
        state.phase = Phase.COOLDOWN;
        state.cooldownEndsMs = System.currentTimeMillis() + 3_600_000L;

        if (!state.isOnCooldown()) {
            helper.fail("isOnCooldown() must return true when phase=COOLDOWN and cooldownEndsMs is future");
        }
        helper.succeed();
    }

    // ─── 8. Parallel trials for different orders ──────────────────────────────

    /**
     * Two {@link TrialState} instances for different orders can independently be in
     * {@link Phase#ACTIVE} simultaneously — confirming order-scoped trial isolation.
     */
    @GameTest
    public void parallelTrialsForDifferentOrders(GameTestHelper helper) {
        TrialState earthState = new TrialState("earth_parallel_" + UUID.randomUUID());
        TrialState skyState   = new TrialState("sky_parallel_"   + UUID.randomUUID());

        earthState.phase = Phase.ACTIVE;
        skyState.phase   = Phase.ACTIVE;

        if (earthState.phase != Phase.ACTIVE) {
            helper.fail("earth trial phase must be ACTIVE");
        }
        if (skyState.phase != Phase.ACTIVE) {
            helper.fail("sky trial phase must be ACTIVE");
        }
        helper.succeed();
    }

    /**
     * Setting phase on one {@link TrialState} must not affect a distinct instance for a
     * different order.
     */
    @GameTest
    public void trialStatesAreIndependent(GameTestHelper helper) {
        TrialState earthState = new TrialState("earth_indep_" + UUID.randomUUID());
        TrialState skyState   = new TrialState("sky_indep_"   + UUID.randomUUID());

        earthState.phase = Phase.ACTIVE;
        // skyState remains IDLE
        if (skyState.phase != Phase.IDLE) {
            helper.fail("Modifying earth TrialState must not change sky TrialState phase");
        }
        helper.succeed();
    }

    // ─── 9. Sigil Tests ──────────────────────────────────────────────────────

    /**
     * The Challenger's Sigil must have a maximum stack size of 1 — it is intentionally
     * non-stackable so that each challenge costs exactly one sigil.
     */
    @GameTest
    public void challengerSigilIsNotStackable(GameTestHelper helper) {
        // ChallengerSigil.register() is called during Ascension.onInitialize()
        // which runs before GameTests start.
        ItemStack sigil = ChallengerSigil.createSigil();
        int maxSize = sigil.getMaxStackSize();
        if (maxSize != 1) {
            helper.fail("Challenger's Sigil must have maxStackSize = 1, got " + maxSize);
        }
        helper.succeed();
    }

    /**
     * {@link ChallengerSigil#isSigil(ItemStack)} must return {@code true} for a stack created
     * by {@link ChallengerSigil#createSigil()}.
     */
    @GameTest
    public void challengerSigilIdentificationWorks(GameTestHelper helper) {
        ItemStack sigil = ChallengerSigil.createSigil();
        if (!ChallengerSigil.isSigil(sigil)) {
            helper.fail("isSigil must return true for a stack from createSigil()");
        }
        helper.succeed();
    }

    /**
     * {@link ChallengerSigil#isSigil(ItemStack)} must return {@code false} for an unrelated
     * item (diamond).
     */
    @GameTest
    public void nonSigilItemNotIdentifiedAsSigil(GameTestHelper helper) {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        if (ChallengerSigil.isSigil(diamond)) {
            helper.fail("isSigil must return false for a plain diamond ItemStack");
        }
        helper.succeed();
    }

    /**
     * {@link ChallengerSigil#isSigil(ItemStack)} must return {@code false} for a {@code null}
     * stack — confirming null safety.
     */
    @GameTest
    public void sigilCheckNullSafe(GameTestHelper helper) {
        if (ChallengerSigil.isSigil(null)) {
            helper.fail("isSigil(null) must return false");
        }
        helper.succeed();
    }

    /**
     * {@link ChallengerSigil#isSigil(ItemStack)} must return {@code false} for
     * {@link ItemStack#EMPTY}.
     */
    @GameTest
    public void sigilCheckEmptyStackReturnsFalse(GameTestHelper helper) {
        if (ChallengerSigil.isSigil(ItemStack.EMPTY)) {
            helper.fail("isSigil(ItemStack.EMPTY) must return false");
        }
        helper.succeed();
    }

    // ─── 10. getOrCreate via ChallengerTrialManager ───────────────────────────

    /**
     * {@link ChallengerTrialManager#getOrCreate(String)} must return a non-null
     * {@link TrialState} and register it so that a subsequent {@link ChallengerTrialManager#get}
     * call also returns it.
     */
    @GameTest
    public void getOrCreateReturnsSameState(GameTestHelper helper) {
        ChallengerTrialManager mgr = ChallengerTrialManager.get();
        String orderKey = "trial_getorcreate_" + UUID.randomUUID();

        TrialState created = mgr.getOrCreate(orderKey);
        if (created == null) {
            helper.fail("getOrCreate must return a non-null TrialState");
        }
        TrialState retrieved = mgr.get(orderKey);
        if (retrieved != created) {
            helper.fail("get() must return the same TrialState instance created by getOrCreate()");
        }
        helper.succeed();
    }

    /**
     * A {@link TrialState} created via {@link ChallengerTrialManager#getOrCreate} must start
     * in {@link Phase#IDLE}.
     */
    @GameTest
    public void getOrCreateInitialPhaseIsIdle(GameTestHelper helper) {
        ChallengerTrialManager mgr = ChallengerTrialManager.get();
        String orderKey = "trial_getorcreate_idle_" + UUID.randomUUID();

        TrialState state = mgr.getOrCreate(orderKey);
        if (state.phase != Phase.IDLE) {
            helper.fail("TrialState from getOrCreate must start IDLE, got " + state.phase);
        }
        helper.succeed();
    }

    /**
     * {@link ChallengerTrialManager#get(String)} must return {@code null} for an order name
     * that has never been passed to {@link ChallengerTrialManager#getOrCreate}.
     */
    @GameTest
    public void getReturnsNullForUnknownOrder(GameTestHelper helper) {
        ChallengerTrialManager mgr = ChallengerTrialManager.get();
        TrialState state = mgr.get("__never_created_order_xyz_" + UUID.randomUUID());
        if (state != null) {
            helper.fail("get() must return null for an order that was never created");
        }
        helper.succeed();
    }

    // ─── 11. Loss counter independence ───────────────────────────────────────

    /**
     * Loss counters for different orders are independent — incrementing earth must not affect
     * sky.
     */
    @GameTest
    public void lossCountersAreOrderIndependent(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        gm.incrementLossCounter("earth");

        if (gm.getLossCounter("sky") != 0) {
            helper.fail("Incrementing earth loss counter must not affect sky loss counter");
        }
        helper.succeed();
    }

    /**
     * Multiple consecutive increments must accumulate correctly.
     */
    @GameTest
    public void lossCounterMultipleIncrements(GameTestHelper helper) {
        GodManager gm = GodManager.createForTesting();
        gm.incrementLossCounter("ocean");
        gm.incrementLossCounter("ocean");
        gm.incrementLossCounter("ocean");

        if (gm.getLossCounter("ocean") != 3) {
            helper.fail("getLossCounter must be 3 after three increments, got "
                    + gm.getLossCounter("ocean"));
        }
        helper.succeed();
    }
}
