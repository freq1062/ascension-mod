package freq.ascension.orders;

import freq.ascension.config.ConfigGroup;
import freq.ascension.managers.SpellStats;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public class SkyGod extends Sky {
    public static final SkyGod INSTANCE = new SkyGod();

    public static final ConfigGroup CONFIG_GROUP = new ConfigGroup("sky_god")
            .add("dripstone_dmg_percent", 50)
            .add("projectile_speed_percent", 50)
            .add("double_jump.cooldown_ticks", 160)
            .add("double_jump_height", 6)
            .add("dash_cooldown_ticks", 225)
            .add("dash_distance", 12)
            .add("star_strike_cooldown_ticks", 675)
            .add("star_strike_damage_percent", 40)
            .add("star_strike_range", 64)
            .add("star_strike_glowcol", 0x000000);

    private SkyGod() {
        super();
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            // Double jump is also implemented in SpellRegistry.java
            case "passive" ->
                "Full fall, projectile, and dripstone damage immunity. Breezes are passive. Double jump by jumping twice quickly and deal damage to nearby entities on landing.";
            default -> "";
        };
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dash" -> {
                int cd = CONFIG_GROUP.get("dash.cooldown_ticks");
                int ds = CONFIG_GROUP.get("dash.distance");
                yield new SpellStats(cd, "Dash in the direction you're facing by " + ds + " blocks. ", ds);
            }
            case "star_strike" -> {
                int cd = CONFIG_GROUP.get("star_strike.cooldown_ticks");
                int gl = CONFIG_GROUP.get("star_strike.glowcol");
                int ran = CONFIG_GROUP.get("star_strike.range");
                int dmg = CONFIG_GROUP.get("star_strike.damage_percent");
                yield new SpellStats(cd,
                        "Summon a 2x2 beam of light that deals " + dmg
                                + "% max hp and launches entities into the sky. Range" + ran + " blocks.",
                        gl, ran, dmg);
            }
            default -> null;
        };
    }

    @Override
    public void onEntityDamage(ServerPlayer victim, DamageContext context) {
        DamageSource source = context.getSource();
        // Gods get full immunity to fall damage, dripstone damage, and projectile
        // damage
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.STALAGMITE)
                || source.is(DamageTypeTags.IS_PROJECTILE)) {
            context.setCancelled(true);
        }
    }

    @Override
    public void applyProjectileShield(ServerPlayer player, Projectile projectile) {
        if (nonHarmfulProjectiles(projectile))
            return;
        if (projectile.getTags().contains("sky_slowed") || projectile.getOwner() == player)
            return;

        if (projectile.level() instanceof ServerLevel serverLevel) {
            // Gods: deflect projectiles by reversing horizontal velocity.
            Vec3 velocity = projectile.getDeltaMovement();
            Vec3 reversed = new Vec3(-velocity.x, velocity.y * 0.2, -velocity.z);
            projectile.setDeltaMovement(reversed);
            serverLevel.getChunkSource().sendToTrackingPlayers(projectile,
                    new ClientboundSetEntityMotionPacket(projectile));
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.GLOW,
                    projectile.getX(), projectile.getY(), projectile.getZ(),
                    10,
                    0.2, 0.2, 0.2,
                    0.02);
            projectile.addTag("sky_slowed");
        }
    }
}
