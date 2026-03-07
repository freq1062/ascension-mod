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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class Nether implements Order {
    public static final Nether INSTANCE = new Nether();

    @Override
    public Order getVersion(String rank) {
        if (rank == "god") {
            return NetherGod.INSTANCE;
        }
        return this;
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("ghast_carry", this, "utility", (player, stats) -> {
            SpellRegistry.ghast_carry(player);
        }));
        SpellCooldownManager.register(new Spell("soul_drain", this, "combat", (player, stats) -> {
            SpellRegistry.soul_drain(player);
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
                    0);
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
        // Fire immunity is implemented via DamageContext cancellation in onEntityDamage().
        // We intentionally do NOT apply MobEffects.FIRE_RESISTANCE here because vanilla
        // LivingEntity.aiStep() calls clearFire() whenever FIRE_RESISTANCE is present,
        // which would extinguish fire ticks early and break the autocrit-on-fire mechanic.
    }

    @Override
    public void onEntityDamage(ServerPlayer player, DamageContext context) {
        // Cancel all fire-type damage when the Nether passive is equipped.
        // The player remains visually on fire (fire ticks drain normally) so the
        // autocrit condition in onEntityDamageByEntity() can still trigger.
        if (hasCapability(player, "passive") && context.getSource().is(DamageTypeTags.IS_FIRE)) {
            context.setCancelled(true);
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
            float saturation = damage / 3.0f;
            attacker.getFoodData().eat(0, saturation); // Restore saturation

            // One SOUL particle per half-saturation bar healed (spec: damage/3/0.5)
            int soulPieces = (int) (saturation / 0.5f);
            for (int i = 0; i < soulPieces; i++) {
                attacker.level().addParticle(ParticleTypes.SOUL,
                        victim.getX() + (attacker.level().getRandom().nextFloat() - 0.5f),
                        victim.getY() + 1 + (attacker.level().getRandom().nextFloat() * 0.5f),
                        victim.getZ() + (attacker.level().getRandom().nextFloat() - 0.5f),
                        0.0, 0.1, 0.0);
            }
        }

        // Autocrit when on fire
        if ((attacker.isOnFire() && hasCapability(attacker, "passive"))) {
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