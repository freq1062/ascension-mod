package freq.ascension.orders;

import freq.ascension.Config;
import freq.ascension.managers.SpellStats;
import net.minecraft.server.level.ServerPlayer;

public class EarthGod extends Earth {
    public static final EarthGod INSTANCE = new EarthGod();

    private EarthGod() {
        super();
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.HASTE, 60, 1));
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Haste 2. Ore drops doubled and auto-smelted without silk touch. Anvils cost 80% less, never break, and have no level limit.";
            case "utility" -> {
                SpellStats s = getSpellStats("supermine");
                yield "SUPERMINE: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            case "combat" -> {
                SpellStats s = getSpellStats("magma_bubble");
                yield "MAGMA BUBBLE: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            default -> "";
        };
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "supermine" -> new SpellStats(Config.earthSupermineCD,
                    "Activate to toggle 3x3 mining. Consumes normal durability.",
                    3, 1); // diameter, max durability loss
            case "magma_bubble" -> new SpellStats(Config.earthGodMagmaBubbleCD,
                    "Scorches enemy with magma spikes in a 4x4 centered area, dealing 4 hearts and launching you into the air. Must be activated on land or in lava.",
                    Config.earthGodMagmaBubbleRange, Config.earthGodMagmaBubbleDmg, true);
            default -> null;
        };
    }
}
