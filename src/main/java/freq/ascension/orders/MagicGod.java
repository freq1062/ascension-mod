package freq.ascension.orders;

import freq.ascension.Config;
import freq.ascension.animation.PotionFlame;
import freq.ascension.managers.SpellStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

public class MagicGod extends Magic {
    public static final MagicGod INSTANCE = new MagicGod();

    private MagicGod() {
        super();
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            // Speed 2 (amplifier 1)
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false, true));
    }

    @Override
    public int modifyEnchantmentCost(int originalCost) {
        return Math.max(1, (int) Math.floor(originalCost * 0.1));
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
    protected int getMaxShapeshiftHistory() {
        return 8;
    }

    @Override
    protected int getPotionEffectTicks() {
        // 10 minutes
        return 10 * 60 * 20;
    }

    @Override
    public MobEffectInstance onPotionEffect(ServerPlayer player, MobEffectInstance effectInstance) {
        if (!hasCapability(player, "utility"))
            return effectInstance;

        // Skip negative effects
        if (!effectInstance.getEffect().value().isBeneficial())
            return effectInstance;

        // Skip turtle master effects (RESISTANCE, SLOWNESS)
        if (effectInstance.getEffect() == MobEffects.RESISTANCE
                || effectInstance.getEffect() == MobEffects.SLOWNESS)
            return effectInstance;

        if (effectInstance.isInfiniteDuration() || effectInstance.isAmbient())
            return effectInstance;

        int dur = effectInstance.getDuration();

        // Identify effect source via heuristics:
        // - !ambient && visible → from potion/arrow/splash
        // - dur <= 220 ticks (~11s max for tipped arrows) → tipped arrow
        // - dur > 220 ticks and <= 10 min → regular potion or splash
        // - Otherwise → "other source" → cap to 1m30s
        boolean fromPotionOrArrow = effectInstance.isVisible();

        if (fromPotionOrArrow && dur <= getPotionEffectTicks()) {
            // Extend to 10 minutes
            int targetDuration = getPotionEffectTicks();
            int durationGranted = Math.max(0, targetDuration - dur);
            int flameDuration = (dur <= 220) ? 20 : 60;
            PotionFlame.spawnPotionFlame(player, flameDuration, durationGranted);

            return new MobEffectInstance(
                    effectInstance.getEffect(),
                    targetDuration,
                    effectInstance.getAmplifier(),
                    effectInstance.isAmbient(),
                    effectInstance.isVisible(),
                    effectInstance.showIcon());
        } else if (!fromPotionOrArrow || dur > getPotionEffectTicks()) {
            // Other sources: cap to 1 minute 30 seconds (1800 ticks)
            if (!effectInstance.isAmbient() && dur != 1800) {
                return new MobEffectInstance(
                        effectInstance.getEffect(),
                        1800,
                        effectInstance.getAmplifier(),
                        effectInstance.isAmbient(),
                        effectInstance.isVisible(),
                        effectInstance.showIcon());
            }
        }

        return effectInstance;
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "shapeshift" -> new SpellStats(Config.magicGodShapeshiftCD,
                    "Transform into the last mob you killed for 45s. Up to 8 forms in history. Boss mobs and other players allowed.",
                    Config.magicGodShapeshiftDuration);
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "Permanent Speed 2.\nEnchantments are 90% cheaper.\nIllagers are passive.";
            case "utility" ->
                "Potions are extended to 10 minutes. Tipped arrows are extended to 1 minute 30 seconds. Excludes resistance and negative effects.";
            case "combat" -> {
                SpellStats s = getSpellStats("shapeshift");
                yield "SHAPESHIFT: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            default -> "";
        };
    }
}
