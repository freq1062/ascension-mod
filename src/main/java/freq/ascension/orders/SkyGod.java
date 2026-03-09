package freq.ascension.orders;

import freq.ascension.Config;
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
            case "double_jump" -> new SpellStats(Config.skyGodDoubleJumpCD, "Jump twice mid-air. Upon landing, nearby entities take damage.", Config.skyDoubleJumpRange, true);
            // Jump height, slam=true for landing AoE
            case "dash" -> new SpellStats(Config.skyGodDashCD, "Dash forward 12 blocks", Config.skyGodDashDistance);
            case "star_strike" -> new SpellStats(Config.skyGodStarStrikeCD,
                    "Summon a 2x2 beam of light that damages and launches entities",
                    true);
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Full fall, dripstone, and projectile immunity. Projectiles are deflected. Double jump slams nearby entities on landing. Breezes are passive.";
            case "utility" -> {
                SpellStats s = getSpellStats("dash");
                yield "DASH: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            case "combat" -> {
                SpellStats s = getSpellStats("star_strike");
                yield "STAR STRIKE: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            default -> "";
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
        if (nonHarmfulProjectiles(projectile)) return;
        if (projectile.getTags().contains("sky_slowed") || projectile.getOwner() == player) return;

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
