package freq.ascension.orders;

import freq.ascension.animation.PotionFlame;
import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;

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
        if (hasCapability(player, "passive"))
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0));
    }

    @Override
    public int modifyEnchantmentCost(int originalCost) {
        return Math.max(1, (int) Math.floor(originalCost * 0.5));
    }

    @Override
    public MobEffectInstance onPotionEffect(ServerPlayer player, MobEffectInstance effectInstance) {
        if (!hasCapability(player, "utility")
                || !effectInstance.getEffect().value().isBeneficial()
                || effectInstance.getEffect() == MobEffects.RESISTANCE
                || effectInstance.getEffect() == MobEffects.SLOWNESS // Turtle Master
                || effectInstance.getDuration() > getPotionEffectTicks()
                || effectInstance.isInfiniteDuration()
                || effectInstance.isAmbient()
                || !effectInstance.isVisible())
            return effectInstance;

        boolean isTippedArrow = effectInstance.getDuration() <= 20 * 20; // Tipped arrows: short duration
        int flameDuration = isTippedArrow ? 20 : 60;
        int targetDuration = isTippedArrow ? 20 * 60 : getPotionEffectTicks(); // 1 min arrows, 5 min potions
        int durationGranted = targetDuration - effectInstance.getDuration();

        PotionFlame.spawnPotionFlame(player, flameDuration, Math.max(0, durationGranted));

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

    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        if (mob.getType().is(EntityTypeTags.ILLAGER) && hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("shapeshift", this, "combat", (player, stats) -> {
            SpellRegistry.shapeshift(player, stats.getInt(0));
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "shapeshift" -> new SpellStats(600,
                    "Transform into the last mob you killed for 30s. Up to 5 forms in history. Die as the mob = die for real.",
                    600); // duration ticks (30s)
            default -> null;
        };
    }

    @Override
    public void onPlayerKill(ServerPlayer killer, LivingEntity victim) {
        if (!hasCapability(killer, "combat"))
            return;
        AscensionData data = (AscensionData) killer;
        if (data.getShapeshiftHistory().size() >= 5)
            return;
        EntityType<?> type = victim.getType();
        boolean isGod = "god".equals(data.getRank());
        if (!isGod && (victim instanceof net.minecraft.server.level.ServerPlayer || isBossType(type)))
            return;
        data.pushShapeshiftKill(type);
    }

    private static boolean isBossType(EntityType<?> type) {
        return type == EntityType.ELDER_GUARDIAN || type == EntityType.WITHER
                || type == EntityType.ENDER_DRAGON || type == EntityType.WARDEN;
    }
}