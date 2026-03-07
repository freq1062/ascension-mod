package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import freq.ascension.managers.AbilityManager;

@Mixin(Mob.class)
public abstract class MobTargetMixin {

    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void onSetTarget(LivingEntity target, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (target instanceof ServerPlayer player) {
            Mob mob = (Mob) (Object) this;
            if (AbilityManager.anyMatch(player, (order) -> order.isIgnoredBy(player, mob))) {
                ci.cancel();
                return;
            }

            // Cancel this setTarget call and redirect to null so the mob becomes neutral.
            // Exception: if the mob is retaliating (player hit it first), allow targeting.
            if (AbilityManager.anyMatch(player, (order) -> order.isNeutralBy(player, mob))) {
                LivingEntity lastHurt = mob.getLastHurtByMob();
                if (lastHurt == player) {
                    return; // mob is retaliating — allow it
                }
                ci.cancel();
                mob.setTarget(null);
            }
        }
    }
}