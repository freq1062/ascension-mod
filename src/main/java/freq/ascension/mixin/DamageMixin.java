package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import freq.ascension.Ascension;
import freq.ascension.managers.AbilityManager;
import freq.ascension.orders.Order.DamageContext;

@Mixin(ServerPlayer.class)
public abstract class DamageMixin {

    // Unique to each player instance during the tick
    @Unique
    private DamageContext currentDamageContext;

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float hookDamageAmount(float amount, ServerLevel level, DamageSource source) {
        ServerPlayer victim = (ServerPlayer) (Object) this;

        // 1. Create the mutable context
        currentDamageContext = new DamageContext(source, amount);

        // 2. Broadcast to all listeners (Bukkit style)
        AbilityManager.broadcast(victim, (ability) -> ability.onEntityDamage(victim, currentDamageContext));

        if (source.getEntity() instanceof ServerPlayer attacker) {
            Ascension.LOGGER.info(String.valueOf("FROM DAMAGEMIXIN " + attacker.isInWaterOrRain()));
            AbilityManager.broadcast(attacker,
                    (ability) -> ability.onEntityDamageByEntity(attacker, victim, currentDamageContext));
        }

        // 3. Return the potentially modified amount back to the method
        return currentDamageContext.getAmount();
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void checkCancellation(ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        // 4. Check if a listener cancelled the event
        if (currentDamageContext != null && currentDamageContext.isCancelled()) {
            currentDamageContext = null; // Clean up
            cir.setReturnValue(false); // Return false (damage failed)
        }
    }
}