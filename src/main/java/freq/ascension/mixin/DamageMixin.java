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

import freq.ascension.managers.AbilityManager;

@Mixin(ServerPlayer.class)
public abstract class DamageMixin {
    public class DamageContext {
        private final DamageSource source;
        private float amount;
        private boolean cancelled = false;

        public DamageContext(DamageSource source, float amount) {
            this.source = source;
            this.amount = amount;
        }

        public DamageSource getSource() {
            return source;
        }

        public float getAmount() {
            return amount;
        }

        public void setAmount(float amount) {
            this.amount = amount;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

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

// @Mixin(ServerPlayer.class)
// public abstract class DamageMixin {

// @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
// private void onPlayerTakeDamage(ServerLevel level, DamageSource source, float
// amount,
// CallbackInfoReturnable<Boolean> cir) {
// ServerPlayer victim = (ServerPlayer) (Object) this;

// AbilityManager.broadcast(victim, (order) -> order.onEntityDamage(victim,
// source, amount, cir));

// if (source.getEntity() instanceof ServerPlayer attacker) {
// AbilityManager.broadcast(attacker,
// (order) -> order.onEntityDamageByEntity(attacker, victim, source, amount,
// cir));
// }
// }
// }