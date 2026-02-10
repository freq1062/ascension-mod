package freq.ascension.orders;

import freq.ascension.animation.PotionFlame;
import freq.ascension.managers.AbilityManager;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Magic implements Order {
    public static final Magic INSTANCE = new Magic();

    @Override
    public Order getVersion(String rank) {
        if (rank == "god")
            return MagicGod.INSTANCE;
        return this;
    }

    public String getOrderName() {
        return "magic";
    }

    public TextColor getOrderColor() {
        // Pink
        return TextColor.fromRgb(0xff00d4);
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "Permanent Speed 1. Enchantments are 50% cheaper.";
            case "utility" ->
                "All potion effects shorter than 5 minutes become 5 minutes, excluding negative effects and resistance.";
            case "combat" -> "Shapeshift";
            default -> "";
        };
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        AbilityManager.skipNextModification();
        if (hasCapability(player, "passive"))
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0));
    }

    @Override
    public int modifyEnchantmentCost(int originalCost) {
        return Math.max(1, (int) Math.floor(originalCost * 0.5));
    }

    @Override
    public MobEffectInstance onPotionEffect(ServerPlayer player, MobEffectInstance effectInstance) {
        if (!hasCapability(player, "passive") || !effectInstance.getEffect().value().isBeneficial()
                || effectInstance.getEffect() == MobEffects.RESISTANCE
                || effectInstance.getDuration() > getPotionEffectTicks()
                || effectInstance.isInfiniteDuration()
                || effectInstance.isAmbient()
                || !effectInstance.isVisible())
            return effectInstance;

        boolean isTippedArrow = effectInstance.getDuration() <= 20 * 20; // Tipped arrows typically have short durations
                                                                         // (≤11 seconds)
        int flameDuration = isTippedArrow ? 20 : 60;
        int targetDuration = isTippedArrow ? 20 * 60 : getPotionEffectTicks(); // 1 minute for arrows, 5 minutes for
                                                                               // potions

        PotionFlame.spawnPotionFlame(player, flameDuration);

        return new MobEffectInstance(
                effectInstance.getEffect(),
                targetDuration,
                effectInstance.getAmplifier(),
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon());
    }

    protected int getPotionEffectTicks() {
        // 5 minutes
        return 5 * 60 * 20;
    }

    @Override
    public String getOrderIcon() {
        return "\uE185";
    }
}