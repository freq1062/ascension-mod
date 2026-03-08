package freq.ascension.orders;

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
                "Haste 2, Ore drops doubled, automatically smelted without silk touch, anvils cost 80% less";
            case "utility" -> "Supermine spell, 3x3";
            case "combat" -> "Magma bubble spell, launches you";
            default -> "";
        };
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "supermine" -> new SpellStats(60,
                    "Activate to toggle 3x3 mining. Consumes normal durability.",
                    3, 1); // diameter, max durability loss
            case "magma_bubble" -> new SpellStats(900,
                    "Scorches enemy with magma spikes in a 4x4 centered area, dealing 4 hearts and launching you into the air. Must be activated on land or in lava.",
                    4, 40, true);
            default -> null;
        };
    }
}
