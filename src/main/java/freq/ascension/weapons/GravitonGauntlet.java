package freq.ascension.weapons;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Ascension;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.End;
import freq.ascension.orders.Order;
import freq.ascension.orders.Sky;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Graviton Gauntlet — Sky order mythical weapon.
 *
 * <p>A retextured Diamond Axe with Sharpness V, Efficiency V, and Curse of Vanishing.
 * Its active ability toggles between PULL and PUSH mode (shift+left-click), then
 * launches all nearby living entities in the chosen direction (shift+right-click).
 */
public class GravitonGauntlet implements MythicWeapon {

    public static final GravitonGauntlet INSTANCE = new GravitonGauntlet();

    /** Per-player mode map. {@code true} = PULL (default), {@code false} = PUSH. */
    private static final ConcurrentHashMap<UUID, Boolean> PULL_MODE = new ConcurrentHashMap<>();

    @Override
    public String getWeaponId() {
        return "graviton_gauntlet";
    }

    @Override
    public Item getBaseItem() {
        return Items.DIAMOND_AXE;
    }

    @Override
    public Order getParentOrder() {
        return Sky.INSTANCE;
    }

    @Override
    public ItemStack createItem() {
        ItemStack stack = buildBaseItem();

        var registries = Ascension.getServer().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);

        stack.enchant(registries.getOrThrow(Enchantments.SHARPNESS), 5);
        stack.enchant(registries.getOrThrow(Enchantments.EFFICIENCY), 5);
        stack.enchant(registries.getOrThrow(Enchantments.VANISHING_CURSE), 1);

        return stack;
    }

    // ═══ MODE MANAGEMENT ═══

    /** Returns {@code true} if the given player is in PULL mode (the default). */
    public static boolean getPullMode(UUID playerId) {
        return PULL_MODE.getOrDefault(playerId, true);
    }

    /**
     * Toggles the mode for the given player and returns the new mode
     * ({@code true} = PULL, {@code false} = PUSH).
     */
    public static boolean toggleMode(UUID playerId) {
        boolean newPull = !getPullMode(playerId);
        PULL_MODE.put(playerId, newPull);
        return newPull;
    }

    // ═══ VELOCITY HELPERS (static for testability) ═══

    /**
     * Calculates the pull velocity that moves an entity at {@code entityPos} toward a
     * player at {@code playerPos}. Pull strength scales linearly with distance — stronger
     * at the far end of the 10-block range.
     */
    public static Vec3 calcPullVelocity(Vec3 playerPos, Vec3 entityPos) {
        double d = entityPos.distanceTo(playerPos);
        double strength = (d / 10.0) * 2.5;
        Vec3 dir = playerPos.subtract(entityPos).normalize();
        return dir.scale(strength).add(0, 0.3, 0);
    }

    /**
     * Calculates the push velocity that moves an entity at {@code entityPos} away from a
     * player at {@code playerPos}. Push strength scales inversely with distance — stronger
     * close to the player.
     */
    public static Vec3 calcPushVelocity(Vec3 playerPos, Vec3 entityPos) {
        double d = entityPos.distanceTo(playerPos);
        double strength = ((10.0 - d) / 10.0) * 2.5;
        Vec3 dir = entityPos.subtract(playerPos).normalize();
        return dir.scale(strength).add(0, 0.3, 0);
    }

    // ═══ EVENT HOOKS ═══

    /**
     * Shift+left-click: unbind any spell occupying the active hotbar slot (to avoid conflict),
     * then toggle between PULL and PUSH mode.
     */
    @Override
    public void onShiftLeftClick(ServerPlayer player) {
        AscensionData data = (AscensionData) player;
        int slot = player.getInventory().getSelectedSlot();
        Map<Integer, String> bindings = data.getSpellBindings();
        String spellId = bindings.get(slot);
        if (spellId != null && !spellId.isEmpty()) {
            data.unbind(spellId);
            player.sendSystemMessage(Component.literal(
                    "§eSpell unbound from this slot to activate the Graviton Gauntlet."));
        }

        boolean nowPull = toggleMode(player.getUUID());
        if (nowPull) {
            player.sendSystemMessage(Component.literal("§bGraviton Gauntlet: §aPULL mode"));
        } else {
            player.sendSystemMessage(Component.literal("§cGraviton Gauntlet: §cPUSH mode"));
        }
    }

    /**
     * Shift+right-click: launch all living entities within a 10-block sphere either toward
     * or away from the player depending on the current mode. Creative-mode players and the
     * weapon holder are excluded.
     */
    @Override
    public void onShiftUse(ServerPlayer player) {
        if (End.isAffectedByDesolation(player)) {
            player.sendSystemMessage(Component.literal(
                    "§cYour combat abilities are disabled by Desolation of Time!"));
            return;
        }

        boolean pullMode = getPullMode(player.getUUID());
        Vec3 playerPos = player.position();

        AABB box = AABB.ofSize(playerPos, 20, 20, 20);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class, box,
                e -> !e.equals(player)
                        && e.distanceTo(player) <= 10.0
                        && !(e instanceof Player ePlr && ePlr.getAbilities().instabuild));

        for (LivingEntity entity : targets) {
            Vec3 vel = pullMode
                    ? calcPullVelocity(playerPos, entity.position())
                    : calcPushVelocity(playerPos, entity.position());
            entity.setDeltaMovement(vel);
            entity.hurtMarked = true;
        }

        spawnVfx(player, playerPos, pullMode);
    }

    // ═══ PRIVATE HELPERS ═══

    private static void spawnVfx(ServerPlayer player, Vec3 playerPos, boolean pullMode) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        var rng = player.getRandom();
        for (int i = 0; i < 20; i++) {
            double theta = rng.nextDouble() * 2 * Math.PI;
            double phi   = rng.nextDouble() * Math.PI;
            double px = playerPos.x + 3 * Math.sin(phi) * Math.cos(theta);
            double py = playerPos.y + 1.0 + 3 * Math.cos(phi);
            double pz = playerPos.z + 3 * Math.sin(phi) * Math.sin(theta);
            if (pullMode) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        px, py, pz, 1, 0, 0, 0, 0.05);
            } else {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        px, py, pz, 1, 0, 0, 0, 0.05);
            }
        }
    }
}
