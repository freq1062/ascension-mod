package freq.ascension.mixin;

import freq.ascension.managers.AbilityManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Projectile.class)
public abstract class ProjectileShieldMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void applyVelocityShield(CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;

        Level level = projectile.level();
        AABB shieldArea = projectile.getBoundingBox().inflate(3.0);
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, shieldArea);

        for (ServerPlayer player : nearbyPlayers) {
            AbilityManager.broadcast(player, (ability) -> ability.applyProjectileShield(player, projectile));
        }
    }
}