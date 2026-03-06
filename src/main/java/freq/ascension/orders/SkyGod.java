package freq.ascension.orders;

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
        // Gods get full immunity to fall damage, dripstone damage, and projectile
        // damage
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.STALAGMITE)
                || source.is(DamageTypeTags.IS_PROJECTILE)) {
            context.setCancelled(true);
        }
    }

    @Override
    public void applyProjectileShield(ServerPlayer player, Projectile projectile) {
        if (nonHarmfulProjectiles(projectile)) {
            return;
        }
        if (projectile.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().sendToTrackingPlayers(projectile,
                    new ClientboundSetEntityMotionPacket(projectile));
            Vec3 velocity = projectile.getDeltaMovement();
            if (Math.abs(velocity.x) > 0.01 && Math.abs(velocity.z) > 0.01) {
                projectile.setDeltaMovement(velocity.scale(0.3));
            }
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.GLOW,
                    projectile.getX(), projectile.getY(), projectile.getZ(),
                    2, // count
                    0.1, 0.1, 0.1, // offset
                    0.01 // speed
            );
        }
    }
}
