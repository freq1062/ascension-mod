package freq.ascension.orders;

import freq.ascension.animation.PotionFlame;
import freq.ascension.config.ConfigGroup;
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

    /*
     * Default configs
     */

    public static final ConfigGroup CONFIG_GROUP = new ConfigGroup("magic")
            .add("enchant_reduction_percent", 50)
            .add("potion_extension_duration_ticks", 6000)
            .add("tipped_arrow_extension_duration_ticks", 1200)
            .add("shapeshift.cooldown_ticks", 600)
            .add("shapeshift.max_transformations", 5)
            .add("shapeshift.duration_ticks", 600);

    /*
     * Metadata
     */
    public String getOrderName() {
        return "magic";
    }

    public TextColor getOrderColor() {
        // Pink
        return TextColor.fromRgb(0xff00d4);
    }

    @Override
    public String getOrderIcon() {
        return "\uE185";
    }

    /*
     * Stats, spells, descriptions
     */

    @Override
    public Order getVersion(String rank) {
        if ("god".equals(rank))
            return MagicGod.INSTANCE;
        return this;
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "Permanent Speed 1. Enchantments are " + CONFIG_GROUP.get("enchant_reduction_percent")
                    + "% cheaper. Illagers are neutral.";
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

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("shapeshift", this, "combat", (player, stats) -> {
            SpellRegistry.shapeshift(player, stats.getInt(0));
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "shapeshift" -> {
                int cd = CONFIG_GROUP.get("shapeshift.cooldown_ticks");
                int ds = CONFIG_GROUP.get("shapeshift.duration_ticks") / 20;
                int his = CONFIG_GROUP.get("shapeshift.max_transformations");
                yield new SpellStats(cd,
                        "Transform into the last mob you killed, excluding boss mobs and players for " + ds
                                + "s. Up to " + his
                                + " transformations can be stored. If you die as the mob, you die for real. View your form history using /sh.",
                        ds * 20);
            }

            default -> null;
        };
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, true));
    }

    @Override
    public int modifyEnchantmentCost(int originalCost) {
        return Math.max(1, (int) Math.floor(originalCost *
                (1 - CONFIG_GROUP.get("enchant_reduction_percent")) / 100));
    }

    @Override
    public MobEffectInstance onPotionEffect(ServerPlayer player, MobEffectInstance effectInstance) {
        int pt = CONFIG_GROUP.get("potion_extension_duration_ticks");
        int tt = CONFIG_GROUP.get("tipped_arrow_extension_duration_ticks");
        if (!hasCapability(player, "utility")
                || !effectInstance.getEffect().value().isBeneficial()
                || effectInstance.getEffect() == MobEffects.RESISTANCE
                || effectInstance.getDuration() > pt
                || effectInstance.isInfiniteDuration()
                || effectInstance.isAmbient()
                || !effectInstance.isVisible())
            return effectInstance;

        boolean isTippedArrow = isTippedArrow(effectInstance.getDuration() / 20);
        int flameDuration = isTippedArrow ? 20 : 60;
        int targetDuration = isTippedArrow ? tt : pt; // 1 min arrows, 5 min potions
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

    private boolean isTippedArrow(int durTicks) {
        // All possible tipped arrow durations excluding turtle master
        return durTicks == 5 || durTicks == 22 || durTicks == 11;
    }

    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        if (mob.getType().is(EntityTypeTags.ILLAGER) && hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public void onPlayerKill(ServerPlayer killer, LivingEntity victim) {
        if (!hasCapability(killer, "combat"))
            return;
        AscensionData data = (AscensionData) killer;
        if (data.getShapeshiftHistory().size() >= CONFIG_GROUP.get("shapeshift.max_transformations"))
            return;
        EntityType<?> type = victim.getType();
        boolean isGod = "god".equals(data.getRank());
        if (!isGod && (isStrongType(type)))
            return;

        // Add to transformation list
        data.pushShapeshiftKill(type);

        // Spawn potion flame effect at death location
        PotionFlame.spawnPotionFlame(killer, 14); // 0.7 seconds = 14 ticks

        // Play amethyst sound
        killer.level().playSound(null, victim.blockPosition(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);

        // Send chat message
        String mobName = type.getDescription().getString();
        killer.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                        "§d" + mobName + "§7 has been added to your transformations!"));
    }

    private static boolean isStrongType(EntityType<?> type) {
        return type == EntityType.ELDER_GUARDIAN || type == EntityType.WITHER
                || type == EntityType.ENDER_DRAGON || type == EntityType.WARDEN || type == EntityType.PLAYER;
    }
}