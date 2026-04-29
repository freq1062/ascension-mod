package freq.ascension.orders;

import freq.ascension.Config;
import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class OceanGod extends Ocean {
    public static final OceanGod INSTANCE = new OceanGod();

    private OceanGod() {
        super();
    }

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
            case "dolphins_grace" -> new SpellStats(Config.oceanGodDolphinsGraceCD,
                    "Cycle between no speed boost, Dolphins' Grace 1, and Dolphins' Grace 2.",
                    0);
            case "molecular_flux" -> new SpellStats(Config.oceanGodMolecularFluxCD,
                    "Transforms water-related blocks between states",
                    Config.oceanGodMolecularFluxRange, Config.oceanGodMolecularFluxDuration);
            case "drown" -> new SpellStats(Config.oceanGodDrownCD,
                    "Drowns players within 12 blocks for 10s. Grants Haste 1 to caster.",
                    Config.oceanGodDrownDuration, Config.oceanGodDrownRadius);
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
