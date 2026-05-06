package freq.ascension.orders;

import freq.ascension.config.ConfigGroup;
import net.minecraft.server.level.ServerPlayer;

public class EarthGod extends Earth {
    public static final EarthGod INSTANCE = new EarthGod();

    public static final ConfigGroup CONFIG_GROUP = new ConfigGroup("earth_god")
            .add("supermine.cooldown_ticks", 60)
            .add("magma_bubble.cooldown_ticks", 9800)
            .add("magma_bubble.range", 4)
            .add("magma_bubble.damage_percent", 40);

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
                "Permanent Haste 1. Instamine stone with efficiency 5. Ore drops are doubled and automatically smelted without silk touch. Anvils cost "
                        + CONFIG_GROUP.get("anvil_discount_percent") + "% less, do not break, and have no limit.";
            default -> "";
        };
    }
}
