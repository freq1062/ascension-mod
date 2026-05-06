package freq.ascension.orders;

import freq.ascension.config.Config;
import freq.ascension.config.ConfigGroup;
import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class OceanGod extends Ocean {
    public static final OceanGod INSTANCE = new OceanGod();

    public static final ConfigGroup CONFIG_GROUP = new ConfigGroup("ocean_god")
            .add("dolphins_grace.cooldown_ticks", 60)
            .add("molecular_flux.cooldown_ticks", 300)
            .add("molecular_flux.range", 40)
            .add("molecular_flux.duration_seconds", 15)
            .add("drown.cooldown_ticks", 600)
            .add("drown.duration_seconds", 10)
            .add("drown.radius", 12);

    private OceanGod() {
        super();
    }

    /*
     * Main body
     */

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 80, 0, true, false, true));

        // Haste 2 only when actually submerged (not just in rain or at water surface)
        if (hasCapability(player, "passive") && player.isUnderWater())
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 80, 1, true, false, true));

        // Haste 1 for the caster while drown spell is active
        ActiveSpell drownAs = SpellCooldownManager.getActiveSpell(player, SpellCooldownManager.get("drown"));
        if (drownAs != null && drownAs.isInUse() && hasCapability(player, "combat"))
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 80, 0, true, false, true));

        // Refresh Dolphin's Grace by re-applying the existing effect's amplifier
        // (DG1 or DG2) so it never expires between applyEffect ticks.
        if (hasCapability(player, "passive")) {
            MobEffectInstance dg = player.getEffect(MobEffects.DOLPHINS_GRACE);
            if (dg != null) {
                player.addEffect(
                        new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 80, dg.getAmplifier(), true, false, true));
            }
        }
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dolphins_grace" -> new SpellStats(CONFIG_GROUP.get("dolphins_grace.cooldown_ticks"),
                    "Cycle between no speed boost, Dolphins' Grace 1, and Dolphins' Grace 2.",
                    0);
            case "molecular_flux" ->
                new SpellStats(CONFIG_GROUP.get("molecular_flux.cooldown_ticks"),
                        "Transforms water related blocks between states.",
                        CONFIG_GROUP.get("molecular_flux.range"), CONFIG_GROUP.get("molecular_flux.duration_seconds"));
            case "drown" -> new SpellStats(CONFIG_GROUP.get("drown.cooldown_ticks"),
                    "Drowns players within " + CONFIG_GROUP.get("drown.radius") + " and activates passives on land for "
                            + CONFIG_GROUP.get("drown.duration_seconds") + "s.",
                    CONFIG_GROUP.get("drown.duration_seconds"), CONFIG_GROUP.get("drown.radius"));
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Permanent Conduit Power. Haste 2 while submerged. Autocrit in water. Walk over powdered snow with any boots. DOLPHIN'S GRACE: "
                        + getSpellStats("dolphins_grace").getDescription();
            case "utility" -> {
                SpellStats s = getSpellStats("molecular_flux");
                yield "MOLECULAR FLUX: Transform water-related blocks in a " + s.getInt(0)
                        + "-block range for " + s.getInt(1) + "s. " + s.getCooldownSecs() + "s cooldown.";
            }
            case "combat" -> {
                SpellStats s = getSpellStats("drown");
                yield "DROWN: Drowns players within " + s.getInt(1) + " blocks for " + s.getInt(0)
                        + "s. Grants Haste 1 to caster. " + s.getCooldownSecs() + "s cooldown.";
            }
            default -> "";
        };
    }
}
