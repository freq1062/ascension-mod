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
import net.minecraft.world.entity.LivingEntity;
import freq.ascension.managers.AbilityManager;
import freq.ascension.orders.Order.DamageContext;
import freq.ascension.registry.SpellRegistry;

@Mixin(LivingEntity.class)
public abstract class DamageMixin {

    @Unique
    private DamageContext ascension$currentDamageContext;

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void ascension$initDamageContext(ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        LivingEntity victim = (LivingEntity) (Object) this;

        ascension$currentDamageContext = new DamageContext(source, amount);

        if (source.getEntity() instanceof ServerPlayer attacker) {
            AbilityManager.broadcast(attacker,
                    (ability) -> ability.onEntityDamageByEntity(attacker, victim, ascension$currentDamageContext));

            // Route to weapon-specific onAttack handler
            AbilityManager.broadcastWeapon(attacker, w -> w.onAttack(attacker, victim, ascension$currentDamageContext));

            // Check if attacker has thorns active and trigger on victim
            // Shield blocks should not trigger the Thorns spell
            if (SpellRegistry.isThornsActive(attacker)) {
                boolean victimBlocking = victim instanceof ServerPlayer vp && vp.isBlocking();
                if (!victimBlocking) {
                    SpellRegistry.executeThorns(attacker, victim);
                }
            }
        }

        if (victim instanceof ServerPlayer victimPlayer) {
            AbilityManager.broadcast(victimPlayer,
                    (ability) -> ability.onEntityDamage(victimPlayer, ascension$currentDamageContext));
        }

        // Check if cancelled
        if (ascension$currentDamageContext.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float ascension$modifyDamageAmount(float amount) {
        if (ascension$currentDamageContext != null) {
            float modifiedAmount = ascension$currentDamageContext.getAmount();
            ascension$currentDamageContext = null; // Clean up
            return modifiedAmount;
        }
        return amount;
    }
}