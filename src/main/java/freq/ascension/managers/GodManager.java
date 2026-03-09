package freq.ascension.managers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.MythicWeapon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.item.ItemStack;

/**
 * Server-wide persistent store that tracks which player is currently the god of each order.
 *
 * <p>Saved to {@code world/data/ascension_gods.dat} via Fabric's {@link PersistentState} API.
 *
 * <p><b>Single entry point:</b> all promotion and demotion must go through this class.
 * The {@code /setrank} command and the future ascension menu both delegate here.
 *
 * <p><b>Invariants enforced:</b>
 * <ul>
 *   <li>At most one god per order at any time.</li>
 *   <li>A player can be god of at most one order at any time.</li>
 *   <li>A player cannot hold a mythical weapon while a demigod.</li>
 *   <li>God status is removed on death (wired via {@code ServerLivingEntityEvents.AFTER_DEATH}
 *       in {@code AbilityManager.init()}).</li>
 *   <li>Demotion carries a 24-hour cooldown tracked by UUID.</li>
 * </ul>
 */
public class GodManager extends SavedData {

    // 24 hours in milliseconds
    public static final long DEMOTION_COOLDOWN_MS = 24L * 60 * 60 * 1000;

    private static final String KEY = "ascension_gods";

    // orderName (lowercase) → player UUID as string
    private final Map<String, String> godsByOrder = new HashMap<>();

    // player UUID as string → System.currentTimeMillis() when cooldown expires
    private final Map<String, Long> demotionCooldowns = new HashMap<>();

    // orderName (lowercase) → number of challenger wins against this god
    private final Map<String, Integer> lossCounters = new HashMap<>();

    // orderName (lowercase) → epoch ms of last offline-loss increment
    private final Map<String, Long> lastDailyLossMs = new HashMap<>();

    // orderName (lowercase) → display name of the current god
    private final Map<String, String> godNamesByOrder = new HashMap<>();

    // ─── Constructors ─────────────────────────────────────────────────────────

    private GodManager() {}

    private static GodManager fromMaps(Map<String, String> gods, Map<String, Long> cooldowns,
            Map<String, Integer> losses, Map<String, Long> dailyLoss, Map<String, String> godNames) {
        GodManager m = new GodManager();
        m.godsByOrder.putAll(gods);
        m.demotionCooldowns.putAll(cooldowns);
        m.lossCounters.putAll(losses);
        m.lastDailyLossMs.putAll(dailyLoss);
        m.godNamesByOrder.putAll(godNames);
        return m;
    }

    // ─── Serialisation (Codec) ────────────────────────────────────────────────

    private static final Codec<GodManager> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING, Codec.STRING)
                            .optionalFieldOf("gods", Map.of())
                            .forGetter(m -> Map.copyOf(m.godsByOrder)),
                    Codec.unboundedMap(Codec.STRING, Codec.LONG)
                            .optionalFieldOf("cooldowns", Map.of())
                            .forGetter(m -> Map.copyOf(m.demotionCooldowns)),
                    Codec.unboundedMap(Codec.STRING, Codec.INT)
                            .optionalFieldOf("loss_counters", Map.of())
                            .forGetter(m -> Map.copyOf(m.lossCounters)),
                    Codec.unboundedMap(Codec.STRING, Codec.LONG)
                            .optionalFieldOf("daily_loss_timestamps", Map.of())
                            .forGetter(m -> Map.copyOf(m.lastDailyLossMs)),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING)
                            .optionalFieldOf("god_names", Map.of())
                            .forGetter(m -> Map.copyOf(m.godNamesByOrder))
            ).apply(instance, GodManager::fromMaps)
    );

    public static final SavedDataType<GodManager> TYPE = new SavedDataType<>(
            KEY,
            GodManager::new,
            CODEC,
            null
    );

    /**
     * Retrieves (or creates) the {@link GodManager} from the server's overworld data storage.
     * Must be called on the server thread.
     */
    public static GodManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ─── Query ────────────────────────────────────────────────────────────────

    /**
     * Returns the UUID of the current god of the given order, or {@code null} if there is none.
     */
    public UUID getGodUUID(String orderName) {
        if (orderName == null) return null;
        String uuidStr = godsByOrder.get(orderName.toLowerCase());
        if (uuidStr == null) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the online {@link ServerPlayer} who is currently god of the given order, or
     * {@code null} if there is no god or the god is offline.
     */
    public ServerPlayer getGodPlayer(String orderName, MinecraftServer server) {
        UUID uuid = getGodUUID(orderName);
        if (uuid == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }

    /** Returns {@code true} if the given player is currently a god of any order. */
    public boolean isGod(ServerPlayer player) {
        return godsByOrder.containsValue(player.getStringUUID());
    }

    /** Returns {@code true} if the given UUID is currently a god of any order. */
    public boolean isGod(UUID uuid) {
        return godsByOrder.containsValue(uuid.toString());
    }

    /**
     * Returns the order name that the given player is god of, or {@code null} if they are not a
     * god (or not recorded in the persistent state).
     */
    public String getGodOrderName(ServerPlayer player) {
        String uuidStr = player.getStringUUID();
        for (Map.Entry<String, String> e : godsByOrder.entrySet()) {
            if (e.getValue().equals(uuidStr)) return e.getKey();
        }
        return null;
    }

    /**
     * Returns the order name that the given UUID is god of, or {@code null} if not found.
     */
    public String getGodOrderName(UUID uuid) {
        String uuidStr = uuid.toString();
        for (Map.Entry<String, String> e : godsByOrder.entrySet()) {
            if (e.getValue().equals(uuidStr)) return e.getKey();
        }
        return null;
    }

    /**
     * Returns {@code true} if the player is on a 24-hour post-demotion cooldown that prevents
     * them from being promoted to god again.
     */
    public boolean isOnDemotionCooldown(ServerPlayer player) {
        Long until = demotionCooldowns.get(player.getStringUUID());
        return until != null && System.currentTimeMillis() < until;
    }

    /** UUID-based overload for use in tests or non-player contexts. */
    public boolean isOnDemotionCooldown(UUID uuid) {
        Long until = demotionCooldowns.get(uuid.toString());
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * Returns the number of milliseconds remaining on the player's demotion cooldown,
     * or {@code 0} if not on cooldown.
     */
    public long getDemotionCooldownRemainingMs(ServerPlayer player) {
        Long until = demotionCooldowns.get(player.getStringUUID());
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    /** UUID-based overload for use in tests or non-player contexts. */
    public long getDemotionCooldownRemainingMs(UUID uuid) {
        Long until = demotionCooldowns.get(uuid.toString());
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    /** Returns an unmodifiable view of the order → UUID string map (for inspection/testing). */
    public Map<String, String> getGodsByOrder() {
        return Collections.unmodifiableMap(godsByOrder);
    }

    // ─── Loss Counter ─────────────────────────────────────────────────────────

    /** Returns the current loss counter for the god of the given order. */
    public int getLossCounter(String orderName) {
        if (orderName == null) return 0;
        return lossCounters.getOrDefault(orderName.toLowerCase(), 0);
    }

    /** Sets the loss counter for the given order and marks dirty. */
    public void setLossCounter(String orderName, int count) {
        if (orderName == null) return;
        lossCounters.put(orderName.toLowerCase(), count);
        setDirty();
    }

    /** Increments the loss counter for the given order by 1 and marks dirty. */
    public void incrementLossCounter(String orderName) {
        if (orderName == null) return;
        lossCounters.merge(orderName.toLowerCase(), 1, Integer::sum);
        setDirty();
    }

    /** Decrements the loss counter for the given order by 1 (minimum 0) and marks dirty. */
    public void decrementLossCounter(String orderName) {
        if (orderName == null) return;
        String key = orderName.toLowerCase();
        lossCounters.put(key, Math.max(0, lossCounters.getOrDefault(key, 0) - 1));
        setDirty();
    }

    /**
     * Increments the loss counter only if the last daily-loss increment was more than 24 real
     * hours ago. Returns true if the increment occurred, false if on cooldown.
     */
    public boolean tryIncrementDailyLoss(String orderName) {
        if (orderName == null) return false;
        String key = orderName.toLowerCase();
        long now = System.currentTimeMillis();
        long last = lastDailyLossMs.getOrDefault(key, 0L);
        if (now - last > 86_400_000L) {
            lastDailyLossMs.put(key, now);
            lossCounters.merge(key, 1, Integer::sum);
            setDirty();
            return true;
        }
        return false;
    }

    // ─── Promotion ───────────────────────────────────────────────────────────

    /**
     * Promotes a player to god of the given order.
     *
     * <p>Steps:
     * <ol>
     *   <li>Demote any existing god of this order (if online; if offline, just clear the entry).</li>
     *   <li>If the player is already a god of another order, demote them from it first.</li>
     *   <li>Update the player's {@link AscensionData}: rank → "god", godOrder → orderName,
     *       all three ability slots → orderName.</li>
     *   <li>Remove any active demotion cooldown (promotion overrides it).</li>
     *   <li>Record the new god in the persistent map and mark dirty.</li>
     *   <li>Give the player the mythical weapon if one is registered for this order.</li>
     * </ol>
     *
     * @param player    the player being promoted
     * @param order     the order they will be god of
     * @param server    the running server instance
     */
    public void promoteToGod(ServerPlayer player, Order order, MinecraftServer server) {
        String orderName = order.getOrderName().toLowerCase();

        // Step 1: demote existing god of this order
        String existingUuidStr = godsByOrder.get(orderName);
        if (existingUuidStr != null) {
            try {
                UUID existingUuid = UUID.fromString(existingUuidStr);
                ServerPlayer existingGod = server.getPlayerList().getPlayer(existingUuid);
                if (existingGod != null) {
                    demoteFromGod(existingGod, server);
                    existingGod.sendSystemMessage(Component.literal(
                            "§cYou have been dethroned as the God of " +
                            capitalize(order.getOrderName()) + "!"));
                } else {
                    // Offline — clear the entry only (cannot remove weapon or send message)
                    godsByOrder.remove(orderName);
                    setDirty();
                }
            } catch (IllegalArgumentException ignored) {
                godsByOrder.remove(orderName);
                setDirty();
            }
        }

        // Step 2: if this player is already god of another order, demote them first
        if (isGod(player)) {
            String oldOrder = getGodOrderName(player);
            demoteFromGod(player, server);
            player.sendSystemMessage(Component.literal(
                    "§eYou relinquished your title as God of " +
                    capitalize(oldOrder != null ? oldOrder : "?") + "."));
        }

        // Before step 3: save current slots so they can be restored on demotion
        savePreviousSlots(player);

        // Step 3: update AscensionData
        AscensionData data = (AscensionData) player;
        data.setRank("god");
        data.setGodOrder(orderName);
        data.setPassive(orderName);
        data.setUtility(orderName);
        data.setCombat(orderName);

        // Step 4: clear any active demotion cooldown
        demotionCooldowns.remove(player.getStringUUID());

        // Step 5: record in persistent map
        godsByOrder.put(orderName, player.getStringUUID());
        godNamesByOrder.put(orderName, player.getName().getString());

        // Reset loss counter now that a new god has risen
        lossCounters.remove(orderName);
        lastDailyLossMs.remove(orderName);

        setDirty();

        // Step 6: give mythical weapon
        MythicWeapon weapon = WeaponRegistry.getForOrder(orderName);
        if (weapon != null) {
            ItemStack weaponStack = weapon.createItem();
            if (!player.getInventory().add(weaponStack)) {
                // Inventory full — drop at player's feet
                player.drop(weaponStack, false);
            }
            player.sendSystemMessage(Component.literal(
                    "§6⚔ You received your mythical weapon: " +
                    MythicWeapon.formatWeaponName(weapon.getWeaponId()) + "!"));
        }

        // Step 7: spawn promotion animation
        playPromotionAnimation(player);
    }

    // ─── Promotion Animation ─────────────────────────────────────────────────

    void playPromotionAnimation(ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();

        double px = player.getX(), py = player.getY(), pz = player.getZ();

        // Play beacon activate sound at the start of the promotion
        level.playSound(null, px, py, pz, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.9f);

        // Beam dimensions: 1×1 block wide, 8 blocks tall, centered on player
        float beamH = 8.0f;
        org.joml.Vector3f beamPos = new org.joml.Vector3f((float) px, (float) py, (float) pz);
        // Translation to center a 1×1 block horizontally on the entity origin
        org.joml.Vector3f centerXZ = new org.joml.Vector3f(-0.5f, 0f, -0.5f);

        // Spawn the VFXBuilder beam using OCHRE_FROGLIGHT (golden/yellow column)
        freq.ascension.animation.VFXBuilder beam = new freq.ascension.animation.VFXBuilder(
                level, beamPos,
                net.minecraft.world.level.block.Blocks.OCHRE_FROGLIGHT.defaultBlockState(),
                freq.ascension.animation.VFXBuilder.instant(
                        centerXZ, new org.joml.Quaternionf(), new org.joml.Vector3f(1f, 0f, 1f)));

        // Keyframe 1: grow beam to full height in 5 ticks, play thunder when it appears
        beam.addKeyframeS(centerXZ, null, new org.joml.Vector3f(1f, beamH, 1f), 5)
                .withAction(() -> level.playSound(null, px, py, pz,
                        net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.7f));

        // Keyframe 2: hold at full beam for ~2.5 seconds (50 ticks)
        beam.addKeyframeS(null, null, null, 50);

        // Keyframe 3: shrink width to zero over 15 ticks (fade out)
        beam.addKeyframeS(new org.joml.Vector3f(0f, 0f, 0f), null,
                new org.joml.Vector3f(0f, beamH, 0f), 15);

        // Total animation = 5 + 50 + 15 = 70 ticks
        int[] particleTick = {0};
        freq.ascension.api.ContinuousTask particleTask = new freq.ascension.api.ContinuousTask(1, () -> {
            particleTick[0]++;
            if (particleTick[0] % 3 == 0) {
                // Totem particles in a ring around the player
                int ringCount = 12;
                double radius = 2.0;
                for (int j = 0; j < ringCount; j++) {
                    double angle = 2 * Math.PI * j / ringCount;
                    double tx = px + radius * Math.cos(angle);
                    double tz = pz + radius * Math.sin(angle);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                            tx, py + 1.0, tz, 2, 0.0, 0.4, 0.0, 0.05);
                }
            }
        }) {
            @Override
            public boolean isFinished() {
                return particleTick[0] >= 70;
            }
        };

        freq.ascension.Ascension.scheduler.schedule(particleTask);
    }

    // ─── Demotion ────────────────────────────────────────────────────────────

    /**
     * Demotes the given player from god status.
     *
     * <p>Steps:
     * <ol>
     *   <li>Verify the player is actually a god; no-op otherwise.</li>
     *   <li>Remove all mythical weapons from their inventory.</li>
     *   <li>Reset AscensionData: rank → "demigod", godOrder → null, all slots → null.</li>
     *   <li>Record a 24-hour demotion cooldown.</li>
     *   <li>Remove from persistent map and mark dirty.</li>
     * </ol>
     *
     * @param player the player being demoted
     * @param server the running server instance
     */
    public void demoteFromGod(ServerPlayer player, MinecraftServer server) {
        if (!isGod(player)) return;

        String orderName = getGodOrderName(player);

        // Step 2: remove all mythical weapons
        removeAllMythicalWeapons(player);

        // Step 3: reset AscensionData — restore pre-god slots
        AscensionData data = (AscensionData) player;
        data.setRank("demigod");
        data.setGodOrder(null);
        restorePreviousSlots(player);

        // Step 4: record demotion cooldown
        demotionCooldowns.put(player.getStringUUID(),
                System.currentTimeMillis() + DEMOTION_COOLDOWN_MS);

        // Step 5: update persistent map
        if (orderName != null) {
            godsByOrder.remove(orderName);
            godNamesByOrder.remove(orderName);
        }
        setDirty();
    }

    /**
     * Removes the god entry for the given order without triggering a full demotion sequence
     * (no cooldown recorded, no weapon removal). Use when cleaning up stale/offline god entries.
     */
    public void clearGod(String orderName) {
        if (orderName == null) return;
        godsByOrder.remove(orderName.toLowerCase());
        godNamesByOrder.remove(orderName.toLowerCase());
        setDirty();
    }

    /** Returns the stored display name of the current god of the given order, or {@code "Unknown"}. */
    public String getGodName(String orderName) {
        return godNamesByOrder.getOrDefault(orderName.toLowerCase(), "Unknown");
    }

    // ─── Testing support ─────────────────────────────────────────────────────

    /**
     * Creates an empty {@link GodManager} instance for use in game tests.
     * Not backed by the file system; not registered with any server.
     */
    public static GodManager createForTesting() {
        return new GodManager();
    }

    /**
     * Directly records a god entry without running the full promotion sequence (no AscensionData
     * changes, no weapon granting). Use only in game tests to set up preconditions.
     */
    public void setGodEntryForTesting(String orderName, UUID uuid) {
        godsByOrder.put(orderName.toLowerCase(), uuid.toString());
        setDirty();
    }

    /** Overload that also stores the player name — for testing getGodName(). */
    public void setGodEntryForTesting(String orderName, UUID uuid, String playerName) {
        godsByOrder.put(orderName.toLowerCase(), uuid.toString());
        godNamesByOrder.put(orderName.toLowerCase(), playerName);
        setDirty();
    }

    /**
     * Directly removes a god entry without running the demotion sequence. Use in game tests.
     */
    public void clearGodEntryForTesting(String orderName) {
        godsByOrder.remove(orderName.toLowerCase());
        setDirty();
    }

    /**
     * Directly records a demotion cooldown without triggering a demotion. Use in game tests.
     */
    public void setDemotionCooldownForTesting(UUID uuid, long expiresAtMillis) {
        demotionCooldowns.put(uuid.toString(), expiresAtMillis);
        setDirty();
    }

    /**
     * Directly sets the last-daily-loss timestamp for the given order. Use only in game tests
     * to simulate that a daily-loss increment occurred at a specific time.
     */
    public void setLastDailyLossTimestampForTesting(String orderName, long epochMs) {
        if (orderName == null) return;
        lastDailyLossMs.put(orderName.toLowerCase(), epochMs);
        setDirty();
    }

    // ─── Slot Save/Restore Helpers ────────────────────────────────────────────

    /**
     * Saves the player's current passive/utility/combat order names into the
     * previousPassive/Utility/Combat fields so they can be restored on demotion.
     */
    private void savePreviousSlots(ServerPlayer player) {
        AscensionData data = (AscensionData) player;
        Order passive = data.getPassive();
        Order utility = data.getUtility();
        Order combat = data.getCombat();
        data.setPreviousPassive(passive != null ? passive.getOrderName() : "");
        data.setPreviousUtility(utility != null ? utility.getOrderName() : "");
        data.setPreviousCombat(combat != null ? combat.getOrderName() : "");
    }

    /**
     * Restores the player's passive/utility/combat slots from the previously-saved
     * values, then clears those saved values.
     */
    private void restorePreviousSlots(ServerPlayer player) {
        AscensionData data = (AscensionData) player;
        String pp = data.getPreviousPassive();
        String pu = data.getPreviousUtility();
        String pc = data.getPreviousCombat();
        data.setPassive(!pp.isEmpty() ? pp : null);
        data.setUtility(!pu.isEmpty() ? pu : null);
        data.setCombat(!pc.isEmpty() ? pc : null);
        data.setPreviousPassive("");
        data.setPreviousUtility("");
        data.setPreviousCombat("");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static void removeAllMythicalWeapons(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (WeaponRegistry.isMythicalWeapon(stack)) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
