package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.PlantProximityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

@Mixin(LivingEntity.class)
public abstract class MobAggroDistanceMixin {

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void onCanAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Mob))
            return;
        if (target instanceof ServerPlayer player) {
            // Check if player has plant proximity effect active and is near a plant
            if (PlantProximityManager.isNearPlant(player)) {
                boolean hasEffect = AbilityManager.anyMatch(player,
                        (order) -> order.hasPlantProximityEffect(player));

                if (hasEffect) {
                    Mob mob = (Mob) (Object) this;
                    double distance = mob.distanceToSqr(target);
                    // Get the normal follow range
                    double followRange = mob
                            .getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
                    // Reduce by 50%
                    double reducedRange = followRange * 0.5;

                    // If the player is beyond the reduced range, cancel the attack
                    if (distance > reducedRange * reducedRange) {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }
}
