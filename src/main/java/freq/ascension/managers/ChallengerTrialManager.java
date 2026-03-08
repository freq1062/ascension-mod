package freq.ascension.managers;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.api.DelayedTask;
import freq.ascension.api.TaskScheduler;
import freq.ascension.items.ChallengerSigil;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Runtime-only (not persisted) manager for Order god-challenge trials.
 *
 * <p>Tracks the trial lifecycle for each order. Full trial logic (F2-F12) is implemented here.
 *
 * <p>Lifecycle phases:
 * <pre>
 *   IDLE → (challenger right-clicks POI with Sigil) → PENDING_GOD
 *   PENDING_GOD → (god approaches shrine) → ACTIVE
 *   ACTIVE → (cube destroyed / death / logout / timeout) → COOLDOWN
 *   COOLDOWN → (timer expires) → IDLE
 * </pre>
 */
public class ChallengerTrialManager {

    private static ChallengerTrialManager INSTANCE;

    public static ChallengerTrialManager get() {
        if (INSTANCE == null) INSTANCE = new ChallengerTrialManager();
        return INSTANCE;
    }

    // ─── Phase ───────────────────────────────────────────────────────────────

    public enum Phase { IDLE, PENDING_GOD, ACTIVE, COOLDOWN }

    // ─── TrialResult ─────────────────────────────────────────────────────────

    public enum TrialResult { CUBE_DESTROYED, GOD_DEATH, CHALLENGER_DEATH, FORFEIT, TIMEOUT, GOD_LOGOUT }

    // ─── TrialState ──────────────────────────────────────────────────────────

    public static class TrialState {
        public final String orderName;
        public UUID challengerUUID;
        /** UUID of the god at trial start — used to identify god-death events after demotion. */
        public UUID godUUID;
        public Phase phase = Phase.IDLE;
        public int cubeHealth = 500;
        public long pendingStartMs;
        public long activeStartMs;
        /** -1 means challenger is currently in range; any other value is the ms when they left. */
        public long lastChallengerOutOfRangeMs = -1L;
        public long lastOfflineLossMs = 0L;
        public ServerBossEvent bossBar;
        public ContinuousTask healTask;
        public ContinuousTask proximityTask;
        public ContinuousTask bossBarTask;
        public ContinuousTask leashTask;
        public ContinuousTask cooldownTask;
        /** Reference to the POI cube ItemDisplay entity for applying glow / glow removal. */
        public Display.ItemDisplay cubeDisplay;
        /** Reference to the Interaction entity for damage detection. */
        public Interaction cubeInteraction;
        public long cooldownEndsMs = 0L;
        /** UUID of the cooldown countdown TextDisplay entity. */
        public UUID cooldownDisplayUUID;

        public TrialState(String orderName) {
            this.orderName = orderName;
        }

        public boolean isOnCooldown() {
            return System.currentTimeMillis() < cooldownEndsMs;
        }

        /** Stops all running tasks for this trial (safe to call even if tasks are null). */
        public void stopAllTasks() {
            if (healTask != null) { healTask.stop(); healTask = null; }
            if (proximityTask != null) { proximityTask.stop(); proximityTask = null; }
            if (bossBarTask != null) { bossBarTask.stop(); bossBarTask = null; }
            if (leashTask != null) { leashTask.stop(); leashTask = null; }
            if (cooldownTask != null) { cooldownTask.stop(); cooldownTask = null; }
        }
    }

    // ─── State storage ───────────────────────────────────────────────────────

    private final Map<String, TrialState> trials = new HashMap<>();

    public TrialState getOrCreate(String orderName) {
        return trials.computeIfAbsent(orderName.toLowerCase(), TrialState::new);
    }

    public TrialState get(String orderName) {
        return trials.get(orderName.toLowerCase());
    }

    // ─── Proximity check ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given player is a god with an active trial whose POI is
     * within the POI radius. Used for god-protection mechanics.
     */
    public boolean isActiveTrialInRadius(ServerPlayer player, MinecraftServer server) {
        GodManager gm = GodManager.get(server);
        String godOrder = gm.getGodOrderName(player);
        if (godOrder == null) return false;
        TrialState state = get(godOrder);
        if (state == null || state.phase != Phase.ACTIVE) return false;
        PoiManager poi = PoiManager.get(server);
        BlockPos poiPos = poi.getPoiPosition(godOrder);
        if (poiPos == null) return false;
        int radius = poi.getPoiRadius(godOrder);
        return player.blockPosition().distSqr(poiPos) <= (double) (radius * radius);
    }

    // ─── Event registration ───────────────────────────────────────────────────

    /**
     * Registers all static Fabric event listeners. Must be called once from
     * {@code Ascension.onInitialize()}.
     *
     * <p>Separate from {@link #registerEvents} so that events can be registered before the
     * server starts, which is required by the Fabric event system.
     */
    public void registerEventListeners() {

        // F5: Cube melee attack → damage the cube
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(world instanceof ServerLevel)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!(entity instanceof Interaction)) return InteractionResult.PASS;
            Component name = entity.getCustomName();
            if (name == null) return InteractionResult.PASS;
            String nameStr = name.getString();
            if (!nameStr.startsWith("ascension_poi_")) return InteractionResult.PASS;
            String orderName = nameStr.substring("ascension_poi_".length());

            TrialState state = get(orderName);
            if (state == null || state.phase != Phase.ACTIVE) return InteractionResult.PASS;

            if (!(sp.level() instanceof ServerLevel spLevel)) return InteractionResult.PASS;
            MinecraftServer srv = spLevel.getServer();

            float damage = (float) sp.getAttributeValue(Attributes.ATTACK_DAMAGE);
            damageCube(orderName, sp, damage, srv);
            applyCubeGlow(state);
            return InteractionResult.SUCCESS;
        });

        // F12: Death detection — god death and challenger death
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer dead)) return;
            MinecraftServer srv = Ascension.getServer();
            if (srv == null) return;

            // Check if the dead player is a god who killed during active trial
            // (heal the cube on behalf of whoever killed someone during the trial)
            net.minecraft.world.entity.Entity killer = damageSource.getEntity();
            if (killer instanceof ServerPlayer killerPlayer) {
                GodManager gm = GodManager.get(srv);
                String killerOrder = gm.getGodOrderName(killerPlayer.getUUID());
                if (killerOrder != null) {
                    TrialState st = get(killerOrder);
                    if (st != null && st.phase == Phase.ACTIVE) {
                        healCube(killerOrder, 80, srv);
                    }
                }
            }

            // Check if dead player was the god of an active trial.
            // AbilityManager.AFTER_DEATH may have already demoted the god, clearing godsByOrder,
            // so we use the stored godUUID in TrialState rather than GodManager.
            for (TrialState state : trials.values()) {
                if (state.phase == Phase.ACTIVE
                        && state.godUUID != null
                        && state.godUUID.equals(dead.getUUID())) {
                    endTrial(state.orderName, TrialResult.GOD_DEATH, srv);
                    return;
                }
            }

            // Check if dead player is the challenger
            for (TrialState state : trials.values()) {
                if (state.phase == Phase.ACTIVE
                        && dead.getUUID().equals(state.challengerUUID)) {
                    endTrial(state.orderName, TrialResult.CHALLENGER_DEATH, srv);
                    return;
                }
            }
        });

        // F12: God/challenger disconnect handling
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            ServerPlayer player = handler.player;
            GodManager gm = GodManager.get(srv);

            String godOrder = gm.getGodOrderName(player.getUUID());
            if (godOrder != null) {
                TrialState state = get(godOrder);
                if (state != null && state.phase == Phase.ACTIVE) {
                    endTrial(godOrder, TrialResult.GOD_LOGOUT, srv);
                    return;
                } else if (state != null && state.phase == Phase.PENDING_GOD) {
                    godForfeits(godOrder, srv);
                    return;
                }
            }

            // Check if challenger disconnected
            for (TrialState state : trials.values()) {
                if (state.phase == Phase.ACTIVE
                        && player.getUUID().equals(state.challengerUUID)) {
                    endTrial(state.orderName, TrialResult.CHALLENGER_DEATH, srv);
                    return;
                }
            }
        });

        // F12: Server shutdown cleanup
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            for (TrialState state : trials.values()) {
                state.stopAllTasks();
                if (state.bossBar != null) state.bossBar.removeAllPlayers();
            }
        });
    }

    /**
     * Stores server and scheduler references for use in helper methods.
     * Called from {@code Ascension.SERVER_STARTED}.
     */
    public void registerEvents(MinecraftServer server, TaskScheduler scheduler) {
        // References stored implicitly via Ascension.getServer() / Ascension.scheduler
        // This method exists for the lifecycle hook in Ascension.java.
    }

    // ─── F2: Trial Initiation ─────────────────────────────────────────────────

    /**
     * Called when a challenger right-clicks the POI cube while holding a Challenger's Sigil.
     * Validates preconditions, consumes the sigil, and either:
     * <ul>
     *   <li>Records an offline absence if the god is not online, or</li>
     *   <li>Transitions to {@code PENDING_GOD} and starts the proximity-check task.</li>
     * </ul>
     */
    public void initiateTrial(ServerPlayer challenger, String orderName, ServerLevel level) {
        orderName = orderName.toLowerCase();
        TrialState state = getOrCreate(orderName);

        if (state.phase != Phase.IDLE) {
            challenger.sendSystemMessage(Component.literal(
                    "§cA challenge is already in progress for " + capitalize(orderName) + "."));
            return;
        }

        if (state.isOnCooldown()) {
            long remaining = state.cooldownEndsMs - System.currentTimeMillis();
            long minutes = (remaining / 60_000L) + 1;
            challenger.sendSystemMessage(Component.literal(
                    "§cChallenges for " + capitalize(orderName)
                    + " are on cooldown for " + minutes + " more minutes."));
            return;
        }

        MinecraftServer server = level.getServer();
        GodManager gm = GodManager.get(server);

        UUID godUUID = gm.getGodUUID(orderName);
        if (godUUID == null) {
            challenger.sendSystemMessage(Component.literal("§cThere is no god to challenge."));
            return;
        }

        // Consume the sigil
        consumeSigil(challenger);

        ServerPlayer god = gm.getGodPlayer(orderName, server);
        if (god == null) {
            // God is offline — try to count the daily absence
            boolean incremented = gm.tryIncrementDailyLoss(orderName);
            if (incremented) {
                int lossCount = gm.getLossCounter(orderName);
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§7The god of " + capitalize(orderName)
                                + " was challenged but was not online. Their loss counter is now "
                                + lossCount + "."),
                        false);
                if (lossCount >= 2) {
                    gm.clearGod(orderName);
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal(
                                    "§c⚔ The God of " + capitalize(orderName)
                                    + " has been demoted for abandonment!"),
                            false);
                    state.cooldownEndsMs = System.currentTimeMillis() + 3_600_000L;
                }
            } else {
                challenger.sendSystemMessage(Component.literal(
                        "§cThe god of " + capitalize(orderName)
                        + " has already been counted absent today."));
            }
            return;
        }

        // God is online — start PENDING_GOD phase
        String finalOrderName = orderName;
        state.challengerUUID = challenger.getUUID();
        state.phase = Phase.PENDING_GOD;
        state.pendingStartMs = System.currentTimeMillis();

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§6⚔ " + challenger.getDisplayName().getString()
                        + " §6has challenged the God of " + capitalize(orderName)
                        + "! The God has 5 minutes to appear at the shrine to accept,"
                        + " or they forfeit."),
                false);

        BlockPos poiPos = PoiManager.get(server).getPoiPosition(orderName);

        state.proximityTask = new ContinuousTask(20, () -> {
            ServerPlayer godPlayer = gm.getGodPlayer(finalOrderName, server);
            if (godPlayer == null) {
                godForfeits(finalOrderName, server);
                return;
            }
            long elapsed = System.currentTimeMillis() - state.pendingStartMs;
            if (elapsed > 300_000L) {
                godForfeits(finalOrderName, server);
                return;
            }
            if (poiPos != null && godPlayer.blockPosition().distSqr(poiPos) <= 900) {
                startActiveTrial(finalOrderName, server);
            }
        });
        Ascension.scheduler.schedule(state.proximityTask);
    }

    // ─── F3: Start Active Trial ───────────────────────────────────────────────

    /**
     * Transitions from {@code PENDING_GOD} to {@code ACTIVE}, resets terrain, spawns the boss
     * bar, and starts the heal / leash / boss-bar update tasks.
     */
    private void startActiveTrial(String orderName, MinecraftServer server) {
        TrialState state = get(orderName);
        if (state == null) return;

        if (state.proximityTask != null) {
            state.proximityTask.stop();
            state.proximityTask = null;
        }

        state.phase = Phase.ACTIVE;
        state.cubeHealth = 500;
        state.activeStartMs = System.currentTimeMillis();
        state.lastChallengerOutOfRangeMs = -1L;

        // Record the god UUID now (before any potential demotion on death)
        state.godUUID = GodManager.get(server).getGodUUID(orderName);

        PoiManager poi = PoiManager.get(server);
        BlockPos poiPos = poi.getPoiPosition(orderName);
        ServerLevel level = getPoiLevel(orderName, server);

        if (level != null && poiPos != null) {
            poi.resetTerrain(orderName, level);
        }

        // Grab the block display reference for glow effects
        state.cubeDisplay = poi.getDisplayEntity(orderName, server);

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§6⚔ The challenger trial for " + capitalize(orderName)
                        + " has begun! Defeat the cube to claim godhood!"),
                false);

        // ── Boss bar ──────────────────────────────────────────────────────────
        BossEvent.BossBarColor barColor = switch (orderName) {
            case "sky"    -> BossEvent.BossBarColor.BLUE;
            case "earth"  -> BossEvent.BossBarColor.GREEN;
            case "ocean"  -> BossEvent.BossBarColor.BLUE;
            case "magic"  -> BossEvent.BossBarColor.PURPLE;
            case "flora"  -> BossEvent.BossBarColor.GREEN;
            case "nether" -> BossEvent.BossBarColor.RED;
            case "end"    -> BossEvent.BossBarColor.PURPLE;
            default       -> BossEvent.BossBarColor.WHITE;
        };
        state.bossBar = new ServerBossEvent(
                Component.literal("Challenger Trial — " + capitalize(orderName)),
                barColor,
                BossEvent.BossBarOverlay.PROGRESS);
        state.bossBar.setProgress(1.0f);

        int bossBarRadius = poi.getPoiRadius(orderName);
        state.bossBarTask = new ContinuousTask(20, () -> {
            if (state.phase != Phase.ACTIVE) {
                state.bossBarTask.stop();
                return;
            }
            BlockPos pPos = poi.getPoiPosition(orderName);
            if (pPos == null) return;

            Set<ServerPlayer> viewers = new HashSet<>(state.bossBar.getPlayers());
            double visRadiusSq = (double)(bossBarRadius * 2) * (bossBarRadius * 2);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean inRange = p.blockPosition().distSqr(pPos) <= visRadiusSq;
                if (inRange && !viewers.contains(p)) {
                    state.bossBar.addPlayer(p);
                } else if (!inRange && viewers.contains(p)) {
                    state.bossBar.removePlayer(p);
                }
            }
            state.bossBar.setProgress(Math.max(0f, (float) state.cubeHealth / 500f));
        });
        Ascension.scheduler.schedule(state.bossBarTask);

        // ── Passive heal task ─────────────────────────────────────────────────
        state.healTask = new ContinuousTask(20, () -> {
            if (state.phase != Phase.ACTIVE) {
                state.healTask.stop();
                return;
            }
            ServerPlayer godPlayer = GodManager.get(server).getGodPlayer(orderName, server);
            if (godPlayer == null) {
                endTrial(orderName, TrialResult.GOD_LOGOUT, server);
                return;
            }
            BlockPos pPos = poi.getPoiPosition(orderName);
            if (pPos == null) return;
            double dist = Math.sqrt(godPlayer.blockPosition().distSqr(pPos));
            int healAmount;
            if (dist <= 10)      healAmount = 3;
            else if (dist <= 20) healAmount = 2;
            else if (dist <= 30) healAmount = 1;
            else                 healAmount = 0;
            if (healAmount > 0) healCube(orderName, healAmount, server);
        });
        Ascension.scheduler.schedule(state.healTask);

        // ── Challenger leash task ─────────────────────────────────────────────
        int leashRadius = poi.getPoiRadius(orderName);
        state.leashTask = new ContinuousTask(20, () -> {
            if (state.phase != Phase.ACTIVE) {
                state.leashTask.stop();
                return;
            }
            ServerPlayer challenger = server.getPlayerList().getPlayer(state.challengerUUID);
            if (challenger == null) {
                endTrial(orderName, TrialResult.CHALLENGER_DEATH, server);
                return;
            }
            BlockPos pPos = poi.getPoiPosition(orderName);
            if (pPos == null) return;
            double distSq = challenger.blockPosition().distSqr(pPos);
            double leashRadiusSq = (double)(leashRadius * 2) * (leashRadius * 2);
            if (distSq > leashRadiusSq) {
                long now = System.currentTimeMillis();
                if (state.lastChallengerOutOfRangeMs == -1L) {
                    state.lastChallengerOutOfRangeMs = now;
                } else {
                    long elapsed = now - state.lastChallengerOutOfRangeMs;
                    if (elapsed > 10_000L) {
                        endTrial(orderName, TrialResult.FORFEIT, server);
                    } else {
                        long remaining = (10_000L - elapsed) / 1000L;
                        challenger.sendSystemMessage(Component.literal(
                                "§cReturn to the shrine within " + remaining
                                + " seconds or you forfeit!"));
                    }
                }
            } else {
                state.lastChallengerOutOfRangeMs = -1L;
            }
        });
        Ascension.scheduler.schedule(state.leashTask);

        // ── 10-minute hard timeout ────────────────────────────────────────────
        Ascension.scheduler.schedule(new DelayedTask(12000, () -> {
            if (state.phase == Phase.ACTIVE) {
                endTrial(orderName, TrialResult.TIMEOUT, server);
            }
        }));
    }

    /** Delegates to {@link #startActiveTrial} — retained for external callers. */
    public void godAccepted(String orderName, MinecraftServer server) {
        startActiveTrial(orderName, server);
    }

    // ─── F5b: Cube Damage ────────────────────────────────────────────────────

    /**
     * Decrements cube health by {@code damage}. Ends the trial if health reaches zero.
     */
    public void damageCube(String orderName, ServerPlayer attacker, float damage, MinecraftServer server) {
        TrialState state = get(orderName);
        if (state == null || state.phase != Phase.ACTIVE) return;
        state.cubeHealth = Math.max(0, state.cubeHealth - (int) damage);
        if (state.cubeHealth <= 0) {
            endTrial(orderName, TrialResult.CUBE_DESTROYED, server);
        }
    }

    // ─── F6: Cube Healing ────────────────────────────────────────────────────

    /**
     * Increments cube health by {@code amount}, clamped to 500.
     */
    public void healCube(String orderName, int amount, MinecraftServer server) {
        TrialState state = get(orderName);
        if (state == null || state.phase != Phase.ACTIVE) return;
        state.cubeHealth = Math.min(500, state.cubeHealth + amount);
    }

    // ─── F9: Cube Glow ───────────────────────────────────────────────────────

    private void applyCubeGlow(TrialState state) {
        if (state.cubeDisplay == null || state.cubeDisplay.isRemoved()) return;
        state.cubeDisplay.setGlowingTag(true);
        state.cubeDisplay.setGlowColorOverride(0xFFFFFF);
        Ascension.scheduler.schedule(new DelayedTask(5, () -> {
            if (state.cubeDisplay != null && !state.cubeDisplay.isRemoved()) {
                state.cubeDisplay.setGlowingTag(false);
                state.cubeDisplay.setGlowColorOverride(-1);
            }
        }));
    }

    // ─── F11: End Trial ───────────────────────────────────────────────────────

    /**
     * Ends the trial with the given result, applies consequences, and starts the cooldown phase.
     */
    public void endTrial(String orderName, TrialResult result, MinecraftServer server) {
        orderName = orderName.toLowerCase();
        TrialState state = get(orderName);
        if (state == null || state.phase == Phase.IDLE || state.phase == Phase.COOLDOWN) return;

        // Cancel all tasks
        state.stopAllTasks();

        // Remove boss bar
        if (state.bossBar != null) {
            state.bossBar.removeAllPlayers();
            state.bossBar = null;
        }

        // Remove glow from cube display
        if (state.cubeDisplay != null && !state.cubeDisplay.isRemoved()) {
            state.cubeDisplay.setGlowingTag(false);
            state.cubeDisplay.setGlowColorOverride(-1);
        }

        String orderDisplay = capitalize(orderName);
        GodManager gm = GodManager.get(server);

        switch (result) {
            case CUBE_DESTROYED, GOD_DEATH, GOD_LOGOUT -> {
                // Challenger wins
                ServerPlayer challenger = (state.challengerUUID != null)
                        ? server.getPlayerList().getPlayer(state.challengerUUID)
                        : null;
                if (challenger != null) {
                    Order order = OrderRegistry.get(orderName);
                    if (order != null) {
                        gm.promoteToGod(challenger, order, server);
                    }
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("§6⚔ " + challenger.getDisplayName().getString()
                                    + " §6has claimed the title of God of " + orderDisplay + "!"),
                            false);
                } else {
                    // Challenger offline at resolution — just clear the god record
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("§6⚔ The trial for " + orderDisplay
                                    + " has ended — the cube was defeated!"),
                            false);
                }
            }
            case CHALLENGER_DEATH -> {
                gm.decrementLossCounter(orderName);
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§6⚔ The God of " + orderDisplay
                                + " has defended their title! The challenger has been defeated!"),
                        false);
            }
            case FORFEIT -> {
                gm.decrementLossCounter(orderName);
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§6⚔ The God of " + orderDisplay
                                + " has defended their title! The challenger has forfeited!"),
                        false);
            }
            case TIMEOUT -> {
                gm.decrementLossCounter(orderName);
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§6⚔ The God of " + orderDisplay
                                + " has defended their title against the 10-minute challenge!"),
                        false);
            }
        }

        // Enter cooldown
        state.cooldownEndsMs = System.currentTimeMillis() + 3_600_000L;
        state.phase = Phase.COOLDOWN;
        state.challengerUUID = null;
        state.godUUID = null;

        spawnCooldownDisplay(state, orderName, server);

        Ascension.scheduler.schedule(new DelayedTask(72000, () -> {
            if (state.phase == Phase.COOLDOWN) {
                state.phase = Phase.IDLE;
                removeCooldownDisplay(state, server);
            }
        }));
    }

    // ─── God Forfeits ─────────────────────────────────────────────────────────

    private void godForfeits(String orderName, MinecraftServer server) {
        TrialState state = getOrCreate(orderName);

        // Cancel proximity task if still running
        if (state.proximityTask != null) {
            state.proximityTask.stop();
            state.proximityTask = null;
        }

        GodManager gm = GodManager.get(server);
        gm.incrementLossCounter(orderName);
        int lossCount = gm.getLossCounter(orderName);

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§c⚔ The God of " + capitalize(orderName)
                        + " has forfeited the challenge! (Loss counter: " + lossCount + ")"),
                false);

        if (lossCount >= 2) {
            ServerPlayer god = gm.getGodPlayer(orderName, server);
            if (god != null) {
                gm.demoteFromGod(god, server);
                god.sendSystemMessage(Component.literal(
                        "§cYou have been demoted as God of " + capitalize(orderName)
                        + " for repeated forfeits!"));
            } else {
                gm.clearGod(orderName);
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§c⚔ The God of " + capitalize(orderName)
                                + " has been demoted for abandonment!"),
                        false);
            }
        }

        state.phase = Phase.COOLDOWN;
        state.cooldownEndsMs = System.currentTimeMillis() + 3_600_000L;
        state.challengerUUID = null;
        state.godUUID = null;

        spawnCooldownDisplay(state, orderName, server);

        Ascension.scheduler.schedule(new DelayedTask(72000, () -> {
            if (state.phase == Phase.COOLDOWN) {
                state.phase = Phase.IDLE;
                removeCooldownDisplay(state, server);
            }
        }));
    }

    // ─── Cooldown Display ────────────────────────────────────────────────────

    private void spawnCooldownDisplay(TrialState state, String orderName, MinecraftServer server) {
        ServerLevel level = getPoiLevel(orderName, server);
        BlockPos poiPos = PoiManager.get(server).getPoiPosition(orderName);
        if (level == null || poiPos == null) return;

        // Discard any existing cooldown display
        if (state.cooldownDisplayUUID != null) {
            net.minecraft.world.entity.Entity old = level.getEntity(state.cooldownDisplayUUID);
            if (old != null) old.discard();
            state.cooldownDisplayUUID = null;
        }

        Display.TextDisplay td = EntityType.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (td == null) return;
        td.setPos(poiPos.getX() + 0.5, poiPos.getY() + 1.5, poiPos.getZ() + 0.5);
        td.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        td.setText(Component.literal(formatCountdown(state.cooldownEndsMs))
                .withStyle(ChatFormatting.YELLOW));
        level.addFreshEntity(td);
        state.cooldownDisplayUUID = td.getUUID();

        final ServerLevel finalLevel = level;
        state.cooldownTask = new ContinuousTask(20, () -> {
            long remaining = state.cooldownEndsMs - System.currentTimeMillis();
            if (remaining <= 0 || state.phase != Phase.COOLDOWN) {
                state.cooldownTask.stop();
                return;
            }
            net.minecraft.world.entity.Entity ent = finalLevel.getEntity(state.cooldownDisplayUUID);
            if (ent instanceof Display.TextDisplay textDisplay) {
                textDisplay.setText(Component.literal(formatCountdown(state.cooldownEndsMs))
                        .withStyle(ChatFormatting.YELLOW));
            }
        });
        Ascension.scheduler.schedule(state.cooldownTask);
    }

    private void removeCooldownDisplay(TrialState state, MinecraftServer server) {
        if (state.cooldownTask != null) {
            state.cooldownTask.stop();
            state.cooldownTask = null;
        }
        if (state.cooldownDisplayUUID == null) return;
        // Search across all loaded levels
        for (ServerLevel level : server.getAllLevels()) {
            net.minecraft.world.entity.Entity e = level.getEntity(state.cooldownDisplayUUID);
            if (e != null) {
                e.discard();
                break;
            }
        }
        state.cooldownDisplayUUID = null;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ServerLevel getPoiLevel(String orderName, MinecraftServer server) {
        String dimKey = PoiManager.get(server).getPoiDimension(orderName);
        return server.getLevel(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimKey)));
    }

    /** Removes one Challenger's Sigil from whichever hand holds it. */
    private static void consumeSigil(ServerPlayer player) {
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            if (ChallengerSigil.isSigil(stack)) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static String formatCountdown(long cooldownEndsMs) {
        long remaining = Math.max(0, cooldownEndsMs - System.currentTimeMillis());
        long minutes = remaining / 60_000L;
        long seconds = (remaining % 60_000L) / 1_000L;
        return String.format("Next challenge in: %d:%02d", minutes, seconds);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Stops all running tasks across all active trials. Called on server shutdown. */
    public void stopAllTasks() {
        for (TrialState state : trials.values()) {
            state.stopAllTasks();
        }
    }
}
