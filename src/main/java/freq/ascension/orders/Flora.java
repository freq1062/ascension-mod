package freq.ascension.orders;

import freq.ascension.Ascension;
import freq.ascension.api.DelayedTask;
import freq.ascension.managers.PlantProximityManager;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.Creeper;

public class Flora implements Order {
    public static final Flora INSTANCE = new Flora();

    @Override
    public Order getVersion(String rank) {
        if ("god".equals(rank)) {
            return FloraGod.INSTANCE;
        }
        return this;
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("thorns", this, "combat", (player, stats) -> {
            SpellRegistry.thorns(player);
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "thorns" -> new SpellStats(60,
                    "Impale your opponents from the ground, freezing them for 3 seconds and giving them poison 1 for 10 seconds. Deals 35% max health.",
                    0);
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Regeneration I every 3s. Immune to negative potion effects. Double saturation from food. Within 5 blocks of a plant: Sculk sensors and shriekers ignore you, mob aggro distance reduced by 50%.";
            case "utility" -> "flora util temp";
            case "combat" -> "flora combat temp";
            default -> "";
        };
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive")) {
            // ambient=true keeps HUD icon without particle spam; showIcon=true ensures it shows
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, true, false, true));
            // Spawn cherry leaf particles when near a plant block — 6 staggered spawns per 40-tick window (~3/s)
            if (PlantProximityManager.isNearPlant(player) && player.level() instanceof ServerLevel sl) {
                for (int i = 0; i < 6; i++) {
                    final int tickDelay = i * 7;
                    Ascension.scheduler.schedule(new DelayedTask(tickDelay, () -> {
                        if (!player.isAlive() || !PlantProximityManager.isNearPlant(player)) return;
                        sl.sendParticles(ParticleTypes.CHERRY_LEAVES,
                                player.getX() + (sl.getRandom().nextFloat() - 0.5f) * 1.5,
                                player.getY() + 1.5,
                                player.getZ() + (sl.getRandom().nextFloat() - 0.5f) * 1.5,
                                1, 0.3, 0.2, 0.3, 0.05);
                    }));
                }
            }
        }
    }

    public String getOrderName() {
        return "flora";
    }

    @Override
    public MobEffectInstance onPotionEffect(ServerPlayer player, MobEffectInstance effectInstance) {
        if (!hasCapability(player, "passive") || effectInstance.getEffect().value().isBeneficial()
                || effectInstance.isInfiniteDuration()
                || effectInstance.isAmbient()
                || !effectInstance.isVisible())
            return effectInstance;

        player.level().sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                10,
                0.5,
                0.5,
                0.5,
                0.1);

        return new MobEffectInstance(
                effectInstance.getEffect(),
                0,
                effectInstance.getAmplifier(),
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon());
    }

    @Override
    public boolean isIgnoredBy(ServerPlayer player, Mob mob) {
        if (mob instanceof Bee && hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        if (mob instanceof Creeper && hasCapability(player, "utility")
                && PlantProximityManager.isNearPlant(player))
            return true;
        return false;
    }

    @Override
    public boolean canTrampleCrops(ServerPlayer player) {
        return !hasCapability(player, "passive");
    }

    @Override
    public boolean hasPlantProximityEffect(ServerPlayer player) {
        return hasCapability(player, "passive");
    }

    @Override
    public float modifySaturation(ServerPlayer player, float saturation) {
        if (hasCapability(player, "passive")) {
            player.level().sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    player.getX(),
                    player.getY() + 1.0,
                    player.getZ(),
                    10,
                    0.5,
                    0.5,
                    0.5,
                    0.1);
            return saturation * 2.0f;
        }
        return saturation;
    }

    public TextColor getOrderColor() {
        // Green
        return TextColor.fromRgb(0x22bd0d);
    }

    @Override
    public String getOrderIcon() {
        return "\uE186";
    }
}