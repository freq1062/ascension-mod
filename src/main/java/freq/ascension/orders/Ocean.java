package freq.ascension.orders;

import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public class Ocean implements Order {
    public static final Ocean INSTANCE = new Ocean();

    @Override
    public Order getVersion(String rank) {
        if (rank.equals("god")) {
            return OceanGod.INSTANCE;
        }
        return this;
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("dolphins_grace", this, "passive", (player, stats) -> {
            SpellRegistry.dolphinsGrace(player);
        }));

        SpellCooldownManager.register(new Spell("molecular_flux", this, "utility", (player, stats) -> {
            SpellRegistry.molecularFlux(player,
                    stats.getInt(0), // range
                    stats.getInt(1) // duration
            );
        }));

        SpellCooldownManager.register(new Spell("drown", this, "combat", (player, stats) -> {
            SpellRegistry.drown(player, stats.getInt(0), 8);
            // duration, radius
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dolphins_grace" -> new SpellStats(60,
                    "Toggle between normal swimming speed and Dolphin's Grace 1.",
                    0);
            case "molecular_flux" ->
                new SpellStats(300, "Transforms water related blocks between states", 20, 5); // range(blocks),
                                                                                              // duration(seconds)
            case "drown" -> new SpellStats(10,
                    "Drowns players within 8 blocks and activates passives on land for 7s",
                    7); // duration 600
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "Permanent Conduit Power. Autocrit in water. DOLPHIN'S GRACE:"
                    + getSpellStats("dolphins_grace").getDescription();
            case "utility" -> "MOLECULAR FLUX: " + getSpellStats("molecular_flux").getDescription();
            case "combat" -> "DROWN: " + getSpellStats("drown").getDescription();
            default -> "";
        };
    }

    @Override
    public boolean canWalkOnPowderSnow(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public void onEntityDamageByEntity(ServerPlayer attacker, LivingEntity victim, DamageContext context) {
        float damage = context.getAmount();
        // Ignore very low-damage (sweep) attacks
        if (damage < 0.1)
            return;
        ActiveSpell as = SpellCooldownManager.getActiveSpell(attacker, SpellCooldownManager.get("drown"));
        if ((attacker.isInWaterOrRain() && hasCapability(attacker, "passive"))
                || (as != null && as.isInUse())) {
            context.setAmount((float) (context.getAmount() * 1.5));
            attacker.level().playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        10, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            // ambient=true → no dot-particles; showParticles=false → fully hidden;
            // showIcon=true → HUD icon still visible so the player knows it's active.
            // 80 ticks > 40-tick refresh interval, ensuring the effect never expires
            // between applyEffect calls (gives a 40-tick safety buffer).
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, true, false, true));
    }

    public String getOrderName() {
        return "ocean";
    }

    public TextColor getOrderColor() {
        // Dark blue
        return TextColor.fromRgb(0x001eff);
    }

    @Override
    public String getOrderIcon() {
        return "\uE184";
    }
}