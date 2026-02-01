package freq.ascension.mixin;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
        AABB shieldArea = projectile.getBoundingBox().inflate(2.0);
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, shieldArea);

        for (ServerPlayer player : nearbyPlayers) {
            AbilityManager.broadcast(player, (ability) -> ability.applyProjectileShield(player, projectile));
        }
    }
}