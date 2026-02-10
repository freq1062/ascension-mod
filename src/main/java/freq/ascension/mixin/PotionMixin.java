package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public abstract class PotionMixin {

    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance modifyPotionDuration(MobEffectInstance effectInstance) {
        if (AbilityManager.shouldSkipModification()) {
            return effectInstance;
        }

        LivingEntity entity = (LivingEntity) (Object) this;

        if (!(entity instanceof ServerPlayer serverPlayer)) {
            return effectInstance;
        }

        MobEffectInstance[] newEffectInstance = { effectInstance };

        AbilityManager.broadcast(serverPlayer, (order) -> {
            newEffectInstance[0] = order.onPotionEffect(serverPlayer, newEffectInstance[0]);
        });

        return newEffectInstance[0];
    }
}