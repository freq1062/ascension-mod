package freq.ascension.orders;

import freq.ascension.Config;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;

public class Nether implements Order {
    public static final Nether INSTANCE = new Nether();

    /**
     * Tracks the last game-tick at which a Nether player received fire damage.
     * Used to keep the autocrit window alive even after FIRE_RESISTANCE suppresses
     * actual fire ticks (vanilla clears fire when the effect is present).
     */
    private static final Map<UUID, Long> NETHER_FIRE_TIMESTAMP = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Record that {@code player} was in contact with fire (call when fire damage is
     * cancelled).
     */
    public static void recordFireContact(ServerPlayer player) {
        NETHER_FIRE_TIMESTAMP.put(player.getUUID(), player.level().getGameTime());
    }

    /**
     * Returns {@code true} if the player received fire contact within the last 5
     * seconds (100 ticks).
     * Used in place of {@link net.minecraft.world.entity.Entity#isOnFire()} for the
     * autocrit check.
     */
    public static boolean wasRecentlyOnFire(ServerPlayer player) {
        Long time = NETHER_FIRE_TIMESTAMP.get(player.getUUID());
        if (time == null)
            return false;
        return player.level().getGameTime() - time < 100;
    }

    /** Remove stale fire-tracking entry when the player disconnects. */
    public static void clearFireTracking(UUID uuid) {
        NETHER_FIRE_TIMESTAMP.remove(uuid);
    }

    @Override
    public Order getVersion(String rank) {
        if ("god".equals(rank)) {
            return NetherGod.INSTANCE;
        }
        return this;
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("ghast_carry", this, "utility", (player, stats) -> {
            SpellRegistry.ghast_carry(player, false, 1.0);
        }));
        SpellCooldownManager.register(new Spell("soul_rage", this, "combat", (player, stats) -> {
            boolean isGod = "god".equals(((freq.ascension.managers.AscensionData) player).getRank());
            int durationTicks = (isGod ? Config.netherSoulRageDurationGod : Config.netherSoulRageDuration) * 20;
            SpellRegistry.soul_rage(player, durationTicks);
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "ghast_carry" -> new SpellStats(Config.netherGhastCarryCD,
                    "Summons a normal health ghast you can control and fly using. 6 b/s",
                    0);
            case "soul_rage" -> new SpellStats(Config.netherSoulRageCD * 20,
                    "Activate a fury that enhances damage when low on health for " + Config.netherSoulRageDuration
                            + "s. " +
                            "≤8h: +1 | ≤6h: +1.5 | ≤4h: +2 | ≤2h: +3. You take 20% more damage while active.",
                    0);
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Permanent fire resistance.\nMobs in the nether are neutral.\nAbility to swim in lava.\nAutocrit when on fire. ";
            case "utility" -> {
                SpellStats s = getSpellStats("ghast_carry");
                yield "GHAST CARRY: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            case "combat" -> {
                SpellStats s = getSpellStats("soul_rage");
                yield "SOUL RAGE: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            default -> "";
        };
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        // Show a persistent FIRE_RESISTANCE icon (ambient so it has beacon styling, not
        // visible
        // particles, but the icon appears in the HUD). Duration 80 ticks — refreshed
        // every 40 ticks.
        if (hasCapability(player, "passive")) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 80, 0, true, false, true));
        }
    }

    @Override
    public void onUnequip(ServerPlayer player, String slotType) {
        // When the player removes Nether from their passive slot, immediately clear the
        // ambient fire resistance so the immunity does not linger for the remaining
        // effect duration (up to 80 ticks after the last applyEffect call).
        if ("passive".equals(slotType)) {
            player.removeEffect(MobEffects.FIRE_RESISTANCE);
        }
    }

    @Override
    public void onEntityDamage(ServerPlayer player, DamageContext context) {
        // Cancel all fire-type damage when the Nether passive is equipped.
        // Record the contact time so wasRecentlyOnFire() can keep the autocrit
        // window alive even though FIRE_RESISTANCE now suppresses the fire visuals.
        if (hasCapability(player, "passive") && context.getSource().is(DamageTypeTags.IS_FIRE)) {
            context.setCancelled(true);
            recordFireContact(player);
        }
    }

    @Override
    public void onEntityDamageByEntity(ServerPlayer attacker, LivingEntity victim, DamageContext context) {
        float damage = context.getAmount();
        // Ignore very low-damage (sweep) attacks
        if (damage < 0.1)
            return;

        // Soul Rage: health-based damage bonus when the buff is active
        if (SpellRegistry.isSoulRageActive(attacker) && hasCapability(attacker, "combat")) {
            float health = attacker.getHealth();
            float bonus = 0;
            if (health <= 4f)
                bonus = 3f;
            else if (health <= 8f)
                bonus = 2f;
            else if (health <= 12f)
                bonus = 1.5f;
            else if (health <= 16f)
                bonus = 1f;
            if (bonus > 0) {
                context.setAmount(context.getAmount() + bonus);
            }
        }

        // Autocrit when recently on fire (tracks via timestamp because FIRE_RESISTANCE
        // prevents vanilla isOnFire() from returning true after the effect is applied)
        if (wasRecentlyOnFire(attacker) && hasCapability(attacker, "passive")) {
            context.setAmount((float) (context.getAmount() * 1.5));
            attacker.level().playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            attacker.level().addParticle(ParticleTypes.CRIT, victim.getX(), victim.getY(), victim.getZ(), 0.0, 0.0,
                    0.0);
        }
    }

    public String getOrderName() {
        return "nether";
    }

    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        return mob.level().dimension() == Level.NETHER;
    }

    @Override
    public boolean canSwimInlava(ServerPlayer player) {
        return hasCapability(player, "passive");
    }

    public TextColor getOrderColor() {
        // Red
        return TextColor.fromRgb(0x9c0e0e);
    }

    @Override
    public String getOrderIcon() {
        return "\uE187";
    }
}