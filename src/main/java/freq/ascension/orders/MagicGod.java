package freq.ascension.orders;

import freq.ascension.animation.PotionFlame;
import freq.ascension.config.Config;
import freq.ascension.config.ConfigGroup;
import freq.ascension.managers.SpellStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

public class MagicGod extends Magic {
    public static final MagicGod INSTANCE = new MagicGod();

    public static final ConfigGroup CONFIG_GROUP = new ConfigGroup("magic")
            .add("enchant_reduction_percent", 90)
            .add("potion_extension_duration_ticks", 12000)
            .add("tipped_arrow_extension_duration_ticks", 1800)
            .add("shapeshift.cooldown_ticks", 600)
            .add("shapeshift.max_transformations", 7)
            .add("shapeshift.duration_ticks", 900);

    private MagicGod() {
        super();
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            // Speed 2 (amplifier 1)
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false, true));
    }

    /** Gods: illagers are fully passive (never attack), not just neutral. */
    @Override
    public boolean isIgnoredBy(ServerPlayer player, Mob mob) {
        return mob.getType().is(EntityTypeTags.RAIDERS) && hasCapability(player, "passive");
    }

    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        return false; // Handled by isIgnoredBy for god
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "shapeshift" -> {
                int cd = CONFIG_GROUP.get("shapeshift.cooldown_ticks");
                int ds = CONFIG_GROUP.get("shapeshift.duration_ticks") / 20;
                int his = CONFIG_GROUP.get("shapeshift.max_transformations");
                yield new SpellStats(cd,
                        "Transform into the last mob you killed, including boss mobs and players for " + ds
                                + "s. Up to " + his
                                + " transformations can be stored. If you die as the mob, you die for real. View your form history using /sh.",
                        ds * 20);
            }

            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "Permanent Speed 2. Enchantments are " + CONFIG_GROUP.get("enchant_reduction_percent")
                    + "% cheaper. Illagers are passive.";
            case "utility" -> {
                // Didn't really format it sorry
                int pdm = CONFIG_GROUP.get("potion_extension_duration_ticks") / (60 * 20);
                int tdm = CONFIG_GROUP.get("tipped_arrow_extension_duration_ticks") / (60 * 20);
                yield "Potions under " + pdm
                        + " minutes are extended to " + pdm + " minutes. Tipped arrows are extended to " + tdm
                        + " minute. Excludes resistance and negative effects.";
            }
            default -> "";
        };
    }
}
