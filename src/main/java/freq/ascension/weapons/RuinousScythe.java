package freq.ascension.weapons;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import freq.ascension.Ascension;
import freq.ascension.Config;
import freq.ascension.Utils;
import freq.ascension.api.DelayedTask;
import freq.ascension.orders.End;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Ruinous Scythe — mythical weapon for the End order.
 *
 * <p>Active ability: a per-target combo counter. Every 4th consecutive hit (within 3 s) on the
 * same target triggers a burst: 40% max-HP spell damage, 15–20 armour durability split randomly
 * across worn pieces, CRIT particle X-pattern, and two impact sounds.
 */
public class RuinousScythe implements MythicWeapon {

    public static final RuinousScythe INSTANCE = new RuinousScythe();

    // outer key = attacker UUID, inner key = target UUID, value = hit count
    public static final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Integer>> COMBO_COUNTERS =
            new ConcurrentHashMap<>();

    // Generation counter: incremented every time a reset task is scheduled for (attacker, target).
    // The reset Runnable only fires if the generation it captured is still current, i.e. no newer
    // hit landed in the meantime (which would have incremented the generation again).
    public static final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Integer>> RESET_GENERATIONS =
            new ConcurrentHashMap<>();

    private static volatile boolean cleanupRegistered = false;

    // ─── MythicWeapon identity ────────────────────────────────────────────────

    @Override
    public String getWeaponId() {
        return "ruinous_scythe";
    }

    @Override
    public Item getBaseItem() {
        return Items.DIAMOND_SWORD;
    }

    @Override
    public Order getParentOrder() {
        return End.INSTANCE;
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

    /** Applies all Ruinous Scythe enchantments using the live server registry. */
    static void applyEnchantments(ItemStack stack) {
        var enchReg = Ascension.getServer().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        stack.enchant(enchReg.getOrThrow(Enchantments.SHARPNESS), 5);
        stack.enchant(enchReg.getOrThrow(Enchantments.FIRE_ASPECT), 2);
        stack.enchant(enchReg.getOrThrow(Enchantments.SMITE), 5);
        stack.enchant(enchReg.getOrThrow(Enchantments.BANE_OF_ARTHROPODS), 5);
        // Sweeping Edge was removed from Java Edition in 1.21; omitted intentionally.
        stack.enchant(enchReg.getOrThrow(Enchantments.VANISHING_CURSE), 1);
    }

    // ─── Lifecycle / cleanup registration ────────────────────────────────────

    /**
     * Registers server-lifecycle cleanup hooks. Safe to call multiple times; only the first
     * call has any effect. Should be called from {@code Ascension.onInitialize()}.
     */
    public static void register() {
        if (cleanupRegistered) return;
        cleanupRegistered = true;

        // Per-player cleanup on disconnect — remove both as attacker and as target.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            COMBO_COUNTERS.remove(id);
            RESET_GENERATIONS.remove(id);
            COMBO_COUNTERS.values().forEach(m -> m.remove(id));
            RESET_GENERATIONS.values().forEach(m -> m.remove(id));
        });

        // Full map wipe on server stop.
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            COMBO_COUNTERS.clear();
            RESET_GENERATIONS.clear();
        });
    }

    // ─── Active ability: combo counter ───────────────────────────────────────

    @Override
    public void onAttack(ServerPlayer attacker, LivingEntity victim, Order.DamageContext ctx) {
        // Don't count hits that were blocked by a shield
        if (victim instanceof ServerPlayer victimPlayer && victimPlayer.isBlocking()) return;

        // Only count fully charged swings (attack strength ≥ 0.9).
        // Spam clicks and any residual sweep-damage calls at low charge are ignored.
        if (attacker.getAttackStrengthScale(0.5f) < 0.9f) return;

        UUID attackerId = attacker.getUUID();
        UUID targetId = victim.getUUID();

        ConcurrentHashMap<UUID, Integer> attackerCombo =
                COMBO_COUNTERS.computeIfAbsent(attackerId, k -> new ConcurrentHashMap<>());
        int newCount = attackerCombo.merge(targetId, 1, Integer::sum);

        // Cancel any prior reset for this (attacker, target) pair by incrementing the generation.
        ConcurrentHashMap<UUID, Integer> attackerGen =
                RESET_GENERATIONS.computeIfAbsent(attackerId, k -> new ConcurrentHashMap<>());
        int myGen = attackerGen.merge(targetId, 1, Integer::sum);

        // Schedule a 3-second (60-tick) reset if no further hit lands.
        Ascension.scheduler.schedule(new DelayedTask(60, () -> {
            Integer currentGen = RESET_GENERATIONS
                    .getOrDefault(attackerId, new ConcurrentHashMap<>())
                    .get(targetId);
            if (currentGen != null && currentGen == myGen) {
                COMBO_COUNTERS.getOrDefault(attackerId, new ConcurrentHashMap<>()).remove(targetId);
                RESET_GENERATIONS.getOrDefault(attackerId, new ConcurrentHashMap<>()).remove(targetId);
                // Play combo-broken sound at attacker's last known position
                if (Ascension.getServer() != null) {
                    net.minecraft.server.level.ServerPlayer atk =
                            Ascension.getServer().getPlayerList().getPlayer(attackerId);
                    if (atk != null && atk.level() instanceof ServerLevel sl) {
                        sl.playSound(null, atk.blockPosition(),
                                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.5f);
                    }
                }
            }
        }));

        // Play charge-up sound for each hit in the combo (not when triggering)
        if (newCount < Config.ruinousScytheHitsNeeded && victim.level() instanceof ServerLevel sl) {
            float pitch = newCount == 1 ? 0.8f : (newCount == 2 ? 1.0f : 1.2f);
            sl.playSound(null, victim.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, pitch);
        }

        if (newCount >= Config.ruinousScytheHitsNeeded) {
            // Reset the counter first to avoid double-trigger on rapid hits.
            attackerCombo.put(targetId, 0);

            // 40 % max-HP spell damage bypassing armour.
            Utils.spellDmg(victim, attacker, 40);

            // 15–20 total armour durability damage split across worn pieces.
            applyArmorDurabilityDamage(victim);

            // CRIT particle X-pattern at eye level.
            if (victim.level() instanceof ServerLevel sl) {
                double eyeY = victim.getEyeY();
                double x = victim.getX();
                double z = victim.getZ();
                sl.sendParticles(ParticleTypes.CRIT, x + 0.5, eyeY, z + 0.5, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.CRIT, x - 0.5, eyeY, z + 0.5, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.CRIT, x + 0.5, eyeY, z - 0.5, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.CRIT, x - 0.5, eyeY, z - 0.5, 1, 0, 0, 0, 0);

                sl.playSound(null, victim.blockPosition(),
                        SoundEvents.WOLF_ARMOR_CRACK, SoundSource.PLAYERS, 1.0f, 1.0f);
                sl.playSound(null, victim.blockPosition(),
                        SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    }

    // ─── Armour durability distribution ──────────────────────────────────────

    private static void applyArmorDurabilityDamage(LivingEntity victim) {
        List<EquipmentSlot> worn = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!victim.getItemBySlot(slot).isEmpty()) worn.add(slot);
        }
        if (worn.isEmpty()) return;

        int[] perPiece = distributeArmor(worn.size(), ThreadLocalRandom.current());
        for (int i = 0; i < worn.size(); i++) {
            if (perPiece[i] <= 0) continue;
            ItemStack armor = victim.getItemBySlot(worn.get(i));
            if (armor.isEmpty()) continue;
            // Clamp so we never fully break the item (durability stays at max-1 minimum).
            int newDamage = Math.min(armor.getDamageValue() + perPiece[i], armor.getMaxDamage() - 1);
            armor.setDamageValue(newDamage);
        }
    }

    /**
     * Generates a random durability-damage allocation across {@code numPieces} armour slots.
     *
     * <p>Total is chosen uniformly in [15, 20]. Each piece except the last receives a random
     * share of the remaining total; the last piece receives whatever is left.
     *
     * @param numPieces number of worn armour pieces (≥ 1)
     * @param rng       source of randomness
     * @return int[] of length {@code numPieces}, values ≥ 0, summing to a value in [15, 20]
     */
    public static int[] distributeArmor(int numPieces, java.util.Random rng) {
        int total = 15 + rng.nextInt(6); // [15, 20] inclusive
        int[] result = new int[numPieces];
        int remaining = total;
        for (int i = 0; i < numPieces - 1; i++) {
            int share = remaining > 0 ? rng.nextInt(remaining + 1) : 0;
            result[i] = share;
            remaining -= share;
        }
        result[numPieces - 1] = remaining;
        return result;
    }
}
