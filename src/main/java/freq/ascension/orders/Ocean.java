package freq.ascension.orders;

import freq.ascension.config.ConfigGroup;
import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.AttackSnapshotManager;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public class Ocean implements Order {

    public static final Ocean INSTANCE = new Ocean();

    /*
     * Default configs
     */
    public static final ConfigGroup CONFIG_GROUP = new ConfigGroup("ocean")
            .add("dolphins_grace.cooldown_ticks", 60)
            .add("molecular_flux.cooldown_ticks", 300)
            .add("molecular_flux.range", 20)
            .add("molecular_flux.duration_seconds", 5)
            .add("drown.cooldown_ticks", 600)
            .add("drown.duration_seconds", 7)
            .add("drown.radius", 8);

    /*
     * Metadata
     */

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

    /*
     * Stats, spells, descriptions
     */

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
                    stats.getInt(0), // range, blocks
                    stats.getInt(1) // duration, secs
            );
        }));

        SpellCooldownManager.register(new Spell("drown", this, "combat", (player, stats) -> {
            SpellRegistry.drown(player,
                    stats.getInt(0), // duration, secs
                    stats.getInt(1) // radius, blocks
            );
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dolphins_grace" -> new SpellStats(CONFIG_GROUP.get("dolphins_grace.cooldown_ticks"),
                    "Toggle between normal swimming speed and Dolphin's Grace 1.",
                    0);
            case "molecular_flux" -> {
                int cd = CONFIG_GROUP.get("molecular_flux.cooldown_ticks");
                int ran = CONFIG_GROUP.get("molecular_flux.range");
                int ds = CONFIG_GROUP.get("molecular_flux.duration_seconds");
                yield new SpellStats(cd,
                        "Transform water-related blocks in a " + ran
                                + "-block range for " + ds
                                + "s.\nWater source -> frosted ice\nfrosted/normal/packed ice -> water source\nCobweb -> air\nWet sponge -> dry sponge\nWater cauldron <-> powdered snow cauldron",
                        ran, ds);
            }
            case "drown" -> {
                int cd = CONFIG_GROUP.get("drown.cooldown_ticks");
                int rad = CONFIG_GROUP.get("drown.radius");
                int ds = CONFIG_GROUP.get("drown.duration_seconds");
                yield new SpellStats(cd,
                        "Drowns players within " + rad + " and activates passives on land for " + ds + "s.",
                        ds, rad);
            }
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Permanent Water Breathing. Autocrit in water. Walk over powdered snow with any boots.";
            default -> "";
        };
    }

    /*
     * Main body
     */

    @Override
    public boolean canWalkOnPowderSnow(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public void onEntityDamageByEntity(ServerPlayer attacker, LivingEntity victim, DamageContext context) {
        float damage = context.getAmount();
        // Ignore very low-damage attacks
        if (damage < 0.1)
            return;

        float attackStrengthScale = AttackSnapshotManager.getCapturedAttackStrength(attacker);
        ActiveSpell as = SpellCooldownManager.getActiveSpell(attacker, SpellCooldownManager.get("drown"));
        boolean oceanPassiveActiveInWater = attacker.isInWater() && hasCapability(attacker, "passive");
        boolean drownAutocritActive = as != null && as.isInUse();

        // Autocrit in water (passive) or while drown is active — full-charge only, no
        // double-crit.
        if (attackStrengthScale >= 0.9f && (oceanPassiveActiveInWater || drownAutocritActive)) {
            // Skip our 1.5× when vanilla already applied a crit (player falling, not
            // sprinting, not in liquid)
            boolean isVanillaCrit = !attacker.onGround() && !attacker.isSprinting()
                    && !attacker.isInWater() && !attacker.isInLava();
            if (!isVanillaCrit) {
                context.setAmount((float) (context.getAmount() * 1.5));
                AttackSnapshotManager.markPendingForcedCrit(attacker, victim);
            }
        }
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive")) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, true, false, true));
            // Refresh Dolphin's Grace if it is currently active (toggled on).
            // applyEffect runs every 40 ticks; giving 60 ticks means the effect
            // is always refreshed before it expires (never drops below 20 ticks).
            if (player.getEffect(MobEffects.DOLPHINS_GRACE) != null) {
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 80, 0, true, false, true));
            }
        }
    }

    @Override
    public void onUnequip(ServerPlayer player, String slotType) {
        if ("passive".equals(slotType)) {
            player.removeEffect(MobEffects.WATER_BREATHING);
            player.removeEffect(MobEffects.DOLPHINS_GRACE);
        }
    }
}
