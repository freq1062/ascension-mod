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
            }

            if (AbilityManager.anyMatch(player, (order) -> order.isNeutralBy(player, mob))) {
                mob.setTarget(null);
            }
        }
    }
}