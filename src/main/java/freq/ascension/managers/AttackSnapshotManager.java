package freq.ascension.managers;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Captures per-attack snapshots that must survive from {@code Player.attack()}
 * into the later {@code hurtServer()} callbacks where ability damage hooks run.
 */
public final class AttackSnapshotManager {
    private static final ConcurrentHashMap<UUID, Float> CAPTURED_ATTACK_STRENGTH = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> CURRENT_ATTACK_TARGETS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> PENDING_FORCED_CRITS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> CONFIRMED_FORCED_CRITS = new ConcurrentHashMap<>();

    private static volatile boolean cleanupRegistered = false;

    private AttackSnapshotManager() {
    }

    public static void register() {
        if (cleanupRegistered) {
            return;
        }
        cleanupRegistered = true;

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearPlayer(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CAPTURED_ATTACK_STRENGTH.clear();
            CURRENT_ATTACK_TARGETS.clear();
            PENDING_FORCED_CRITS.clear();
            CONFIRMED_FORCED_CRITS.clear();
        });
    }

    public static void captureAttack(ServerPlayer player, Entity target, float attackStrengthScale) {
        UUID playerId = player.getUUID();
        CAPTURED_ATTACK_STRENGTH.put(playerId, attackStrengthScale);
        CURRENT_ATTACK_TARGETS.put(playerId, target.getUUID());
        PENDING_FORCED_CRITS.remove(playerId);
        CONFIRMED_FORCED_CRITS.remove(playerId);
    }

    public static float getCapturedAttackStrength(ServerPlayer player) {
        return CAPTURED_ATTACK_STRENGTH.getOrDefault(player.getUUID(), player.getAttackStrengthScale(0.5f));
    }

    public static boolean isCurrentAttackTarget(ServerPlayer player, Entity target) {
        return target.getUUID().equals(CURRENT_ATTACK_TARGETS.get(player.getUUID()));
    }

    public static void markPendingForcedCrit(ServerPlayer player, Entity target) {
        if (isCurrentAttackTarget(player, target)) {
            PENDING_FORCED_CRITS.put(player.getUUID(), target.getUUID());
        }
    }

    public static void resolveForcedCrit(ServerPlayer player, Entity target, boolean attackSucceeded) {
        UUID playerId = player.getUUID();
        UUID targetId = target.getUUID();
        UUID pendingTargetId = PENDING_FORCED_CRITS.get(playerId);

        if (pendingTargetId != null && pendingTargetId.equals(targetId) && attackSucceeded) {
            CONFIRMED_FORCED_CRITS.put(playerId, targetId);
        }

        if (pendingTargetId != null && pendingTargetId.equals(targetId)) {
            PENDING_FORCED_CRITS.remove(playerId);
        }
    }

    public static boolean consumeForcedCrit(ServerPlayer player, Entity target) {
        UUID playerId = player.getUUID();
        UUID targetId = CONFIRMED_FORCED_CRITS.get(playerId);
        if (targetId != null && targetId.equals(target.getUUID())) {
            CONFIRMED_FORCED_CRITS.remove(playerId);
            return true;
        }
        return false;
    }

    public static void clearAttack(ServerPlayer player) {
        clearPlayer(player.getUUID());
    }

    private static void clearPlayer(UUID playerId) {
        CAPTURED_ATTACK_STRENGTH.remove(playerId);
        CURRENT_ATTACK_TARGETS.remove(playerId);
        PENDING_FORCED_CRITS.remove(playerId);
        CONFIRMED_FORCED_CRITS.remove(playerId);
    }
}
