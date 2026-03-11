package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.registry.SpellRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Suppresses movement, jumping, and knockback for stunned players.
// Uses a flag in SpellRegistry rather than attribute manipulation to avoid
// the Zero-G levitation bug on high-latency connections.
@Mixin(LivingEntity.class)
public abstract class StunMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void ascension$cancelMovementIfStunned(Vec3 movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer sp && SpellRegistry.isStunned(sp)) {
            // Lock XZ velocity; allow gravity to pull Y down naturally
            Vec3 current = sp.getDeltaMovement();
            sp.setDeltaMovement(0, Math.min(current.y, 0), 0);
            ci.cancel();
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void ascension$cancelJumpIfStunned(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer sp && SpellRegistry.isStunned(sp)) {
            ci.cancel();
        }
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void ascension$cancelKnockbackIfStunned(double strength, double x, double z, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer sp && SpellRegistry.isStunned(sp)) {
            ci.cancel();
        }
    }
}
