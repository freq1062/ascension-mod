package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

        DamageContext ctx = new DamageContext(source, amount);
        ascension$currentDamageContext = ctx;

        if (source.getEntity() instanceof ServerPlayer attacker) {
            AbilityManager.broadcast(attacker,
                    (ability) -> ability.onEntityDamageByEntity(attacker, victim, ctx));

            // Route to weapon-specific onAttack handler
            AbilityManager.broadcastWeapon(attacker, w -> w.onAttack(attacker, victim, ctx));

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
                    (ability) -> ability.onEntityDamage(victimPlayer, ctx));
            // Soul Rage: player takes more damage while the buff is active
            if (SpellRegistry.isSoulRageActive(victimPlayer)) {
                freq.ascension.managers.AscensionData data = (freq.ascension.managers.AscensionData) victimPlayer;
                boolean isGod = "god".equals(data.getRank());
                ctx.setAmount(ctx.getAmount() * (isGod ? 1.1f : 1.2f));
            }
        }

        // Restore field so @ModifyVariable can read the (possibly modified) amount.
        // This must come AFTER all broadcasts so any re-entrant hurtServer call's cleanup
        // does not leave our outer context lost in the field.
        ascension$currentDamageContext = ctx;

        // Check if cancelled
        if (ctx.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float ascension$modifyDamageAmount(float amount) {
        // Execution order note: Mixin inserts the @Inject(HEAD) callback as the very
        // first bytecode instruction, so ascension$initDamageContext runs BEFORE this
        // @ModifyVariable handler. The field is therefore already populated with the
        // context (including any 1.5x autocrit scaling applied by the broadcasts), and
        // returning ctx.getAmount() here correctly passes the modified damage into the
        // method body. The tests for oceanAutocritScalesDamageBy150Percent only verify
        // context math; this handler is the bridge that makes it reach hurtServer.
        if (ascension$currentDamageContext != null) {
            float modifiedAmount = ascension$currentDamageContext.getAmount();
            ascension$currentDamageContext = null; // Clean up
            return modifiedAmount;
        }
        return amount;
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void ascension$clearDamageContext(ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        ascension$currentDamageContext = null;
    }
}