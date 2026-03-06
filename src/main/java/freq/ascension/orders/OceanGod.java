package freq.ascension.orders;

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
            // ambient=true, showParticles=false, showIcon=true — matches Ocean base pattern.
            // 80 ticks matches Ocean base class buffer (> 40-tick refresh interval).
            player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 80, 0, true, false, true));
        ActiveSpell as = SpellCooldownManager.getActiveSpell(player, SpellCooldownManager.get("drown"));
        // Guard against null ActiveSpell when no drown spell has been cast yet.
        if (player.isInWaterOrRain() || (as != null && as.isInUse()))
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 80, 0, true, false, true));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dolphins_grace" -> new SpellStats(60,
                    "Toggle between normal swimming speed and Dolphin's Grace 1.",
                    0);
            case "molecular_flux" ->
                new SpellStats(300, "Transforms water related blocks between states", 40, 7); // range(blocks),
                                                                                              // duration(seconds)
            case "drown" -> new SpellStats(600,
                    "Drowns players within 8 blocks and activates passives on land for 10s",
                    10); // duration
            default -> null;
        };
    }
}
