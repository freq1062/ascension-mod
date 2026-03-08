package freq.ascension.orders;

import freq.ascension.managers.ActiveSpell;
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

    /** Record that {@code player} was in contact with fire (call when fire damage is cancelled). */
    public static void recordFireContact(ServerPlayer player) {
        NETHER_FIRE_TIMESTAMP.put(player.getUUID(), player.level().getGameTime());
    }

    /**
     * Returns {@code true} if the player received fire contact within the last 5 seconds (100 ticks).
     * Used in place of {@link net.minecraft.world.entity.Entity#isOnFire()} for the autocrit check.
     */
    public static boolean wasRecentlyOnFire(ServerPlayer player) {
        Long time = NETHER_FIRE_TIMESTAMP.get(player.getUUID());
        if (time == null) return false;
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
        SpellCooldownManager.register(new Spell("soul_drain", this, "combat", (player, stats) -> {
            SpellRegistry.soul_drain(player, stats.getInt(0));
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "ghast_carry" -> new SpellStats(60,
                    "Summons a normal health ghast you can control and fly using. 6 b/s",
                    0);
            case "soul_drain" -> new SpellStats(60,
                    "For 10 seconds you gain saturation equivalent to 1/3 of the damage that you deal.",
                    200); // duration ticks (10s)
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Permanent fire resistance. Nether mobs are neutral. Ability to swim in lava. Autocrit when on fire. ";
            case "utility" -> "Nether util temp";
            case "combat" -> "Nether combat temp";
            default -> "";
        };
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        // Show a persistent FIRE_RESISTANCE icon (ambient so it has beacon styling, not visible
        // particles, but the icon appears in the HUD). Duration 80 ticks — refreshed every 40 ticks.
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

        // Soul Drain healing effect
        ActiveSpell soulDrain = SpellCooldownManager.getActiveSpell(attacker, SpellCooldownManager.get("soul_drain"));
        if (soulDrain != null && soulDrain.isInUse() && hasCapability(attacker, "combat")) {
            float saturation = damage * getSoulDrainRatio();
            attacker.getFoodData().eat(0, saturation); // Restore saturation

            // One SOUL particle per half-saturation bar healed
            int soulPieces = (int) (saturation / 0.5f);
            for (int i = 0; i < soulPieces; i++) {
                attacker.level().addParticle(ParticleTypes.SOUL,
                        victim.getX() + (attacker.level().getRandom().nextFloat() - 0.5f),
                        victim.getY() + 1 + (attacker.level().getRandom().nextFloat() * 0.5f),
                        victim.getZ() + (attacker.level().getRandom().nextFloat() - 0.5f),
                        0.0, 0.1, 0.0);
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

    /** Returns the fraction of damage dealt that becomes saturation during soul drain. */
    protected float getSoulDrainRatio() {
        return 1.0f / 3.0f;
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