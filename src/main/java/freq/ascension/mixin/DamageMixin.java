package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import freq.ascension.managers.AbilityManager;

@Mixin(ServerPlayer.class)
public abstract class DamageMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void onPlayerTakeDamage(ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer victim = (ServerPlayer) (Object) this;

        AbilityManager.broadcast(victim, (order) -> order.onEntityDamage(victim, source, amount));

        if (source.getEntity() instanceof ServerPlayer attacker) {
            AbilityManager.broadcast(attacker,
                    (order) -> order.onEntityDamageByEntity(attacker, victim, source, amount));
        }
    }
}