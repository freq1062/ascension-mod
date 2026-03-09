package freq.ascension.weapons;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Ascension;
import freq.ascension.animation.HellfireBeam;
import freq.ascension.orders.Nether;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Hellfire Crossbow — mythical weapon for the Nether order.
 *
 * <h2>Active ability — Hellfire Beam</h2>
 * <p>Every 3rd firework the god fires from this crossbow triggers the Hellfire Beam instead of
 * launching the firework projectile. The beam travels along the player's look vector, stops at
 * the first solid block (max 60 blocks), and deals scaled spell damage to all living entities
 * within 1 block of the ray path.
 *
 * <p>Firework detection is handled by {@link freq.ascension.mixin.CrossbowMixin}, which intercepts
 * {@code CrossbowItem.performShooting} and delegates to {@link #onFireworkShot(ServerPlayer)}.
 */
public class HellfireCrossbow implements MythicWeapon {

    public static final HellfireCrossbow INSTANCE = new HellfireCrossbow();

    /** Tracks how many fireworks each player has fired with this crossbow. */
    public static final ConcurrentHashMap<UUID, Integer> FIREWORK_COUNTER = new ConcurrentHashMap<>();

    private static volatile boolean cleanupRegistered = false;

    // ─── MythicWeapon identity ────────────────────────────────────────────────

    @Override
    public String getWeaponId() {
        return "hellfire_crossbow";
    }

    @Override
    public Item getBaseItem() {
        return Items.CROSSBOW;
    }

    @Override
    public Order getParentOrder() {
        return Nether.INSTANCE;
    }

    // ─── Item creation ────────────────────────────────────────────────────────

    @Override
    public ItemStack createItem() {
        ItemStack stack = buildBaseItem();
        if (Ascension.getServer() != null) {
            applyEnchantments(stack);
        }
        return stack;
    }

    /** Applies all Hellfire Crossbow enchantments using the live server registry. */
    static void applyEnchantments(ItemStack stack) {
        var enchReg = Ascension.getServer().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        stack.enchant(enchReg.getOrThrow(Enchantments.QUICK_CHARGE),     2);
        stack.enchant(enchReg.getOrThrow(Enchantments.MULTISHOT),        1);
        stack.enchant(enchReg.getOrThrow(Enchantments.PIERCING),         4);
        stack.enchant(enchReg.getOrThrow(Enchantments.VANISHING_CURSE),  1);
    }

    // ─── Lifecycle / cleanup registration ────────────────────────────────────

    /**
     * Registers server-lifecycle cleanup hooks. Safe to call multiple times; only the first
     * call has any effect. Must be called from {@code Ascension.onInitialize()}.
     */
    public static void register() {
        if (cleanupRegistered) return;
        cleanupRegistered = true;

        // Per-player cleanup on disconnect.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                FIREWORK_COUNTER.remove(handler.getPlayer().getUUID()));

        // Full wipe on server stop.
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> FIREWORK_COUNTER.clear());
    }

    // ─── Firework shot handling ───────────────────────────────────────────────

    /**
     * Called by {@link freq.ascension.mixin.CrossbowMixin} when a player fires a firework from
     * this crossbow. Increments the per-player counter and triggers the Hellfire Beam as a bonus
     * on every 3rd shot (the firework still fires normally). On shots 1 and 2, plays a charge
     * sound at rising pitch to signal progress toward the beam.
     *
     * @return {@code true} if the Hellfire Beam was activated (informational only — caller must
     *         NOT cancel the vanilla firework launch).
     */
    public boolean onFireworkShot(ServerPlayer player) {
        boolean triggered = incrementAndCheck(player.getUUID());
        if (triggered) {
            Vec3 dir = player.getLookAngle();
            HellfireBeam.fire(player, dir, 60.0);
            // Reset counter so the next 3 shots start a fresh charge cycle.
            FIREWORK_COUNTER.put(player.getUUID(), 0);
        } else {
            // Charging sound — pitch rises on the 2nd press to signal progress toward the beam.
            int count = FIREWORK_COUNTER.getOrDefault(player.getUUID(), 0);
            float pitch = (count % 3 == 1) ? 0.8f : 1.0f;
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, pitch);
        }
        return triggered;
    }

    /**
     * Increments the firework counter for the given player UUID and returns {@code true} if the
     * new count is a multiple of 3 (i.e., the beam should activate).
     *
     * <p>Exposed as a {@code static} method so GameTests can verify counter logic without a
     * live {@link ServerPlayer}.
     */
    public static boolean incrementAndCheck(UUID playerId) {
        int count = FIREWORK_COUNTER.merge(playerId, 1, Integer::sum);
        return count % 3 == 0;
    }

    // ─── Cleanup on god death / weapon released ───────────────────────────────

    @Override
    public void onDeath(ServerPlayer player) {
        FIREWORK_COUNTER.remove(player.getUUID());
    }

    @Override
    public void onRelease(ServerPlayer player) {
        FIREWORK_COUNTER.remove(player.getUUID());
    }
}
