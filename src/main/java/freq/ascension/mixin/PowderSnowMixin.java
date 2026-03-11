package freq.ascension.mixin;

import freq.ascension.managers.AbilityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PowderSnowBlock.class)
public class PowderSnowMixin {

    /**
     * Returns {@code true} (walk on top) when Ocean passive is active and the
     * player is NOT crouching. Returns {@code false} when crouching so the
     * player sinks through the block. Vanilla {@code getCollisionShape} reads
     * this value to decide whether to give the entity a solid surface.
     *
     * <p>Note: the unmodded client predicts sinking (no leather boots). The
     * server corrects the position each tick — minor rubber-banding is the
     * inherent limit of a server-side-only implementation.
     */
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void ocean$allowPowderSnowWalk(Entity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer player && hasAbility(player)) {
            cir.setReturnValue(!player.isShiftKeyDown());
        }
    }

    /**
     * Cancels all powder-snow-inside effects (freeze, slowdown, fire-extinguish)
     * for Ocean passive players. Freeze ticks are cleared each server tick by the
     * END_SERVER_TICK loop in {@code Ascension.java}. When crouching, a gentle
     * downward velocity is applied here to produce a smooth sink feel; ascending
     * (jump while inside) is handled by the same tick loop.
     *
     * <p>Reason for Mixin: no Fabric API hook intercepts per-block inside-effects
     * with enough granularity to selectively suppress FREEZE without also losing
     * makeStuckInBlock, which we want cancelled when not crouching.
     */
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void ocean$insidePowderSnow(BlockState state, Level level, BlockPos pos,
            Entity entity, InsideBlockEffectApplier effectApplier,
            boolean hasPowderSnowBoots, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player) || !hasAbility(player)) return;

        ci.cancel(); // Suppress freeze, makeStuckInBlock, and fire-extinguish

        if (player.isShiftKeyDown()) {
            // Crouching — apply a gentle downward velocity to simulate sinking
            player.setDeltaMovement(
                    player.getDeltaMovement().x,
                    -0.1,
                    player.getDeltaMovement().z);
        }
    }

    private static boolean hasAbility(ServerPlayer player) {
        return AbilityManager.anyMatch(player, order -> order.canWalkOnPowderSnow(player));
    }
}
