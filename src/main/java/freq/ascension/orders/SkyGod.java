package freq.ascension.orders;

import freq.ascension.managers.SpellStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

public class SkyGod extends Sky {
    public static final SkyGod INSTANCE = new SkyGod();

    private SkyGod() {
        super();
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dash" -> new SpellStats(225, "Dash forward 12 blocks", 12);
            case "star_strike" -> new SpellStats(675,
                    "Summon a 2x2 beam of light that damages and launches entities",
                    true);
            default -> null;
        };
    }

    @Override
    public void onEntityDamage(ServerPlayer victim, DamageContext context) {
        DamageSource source = context.getSource();
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.STALAGMITE)) {
            context.setCancelled(true);
        }
    }
}
