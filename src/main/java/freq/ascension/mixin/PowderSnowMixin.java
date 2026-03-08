package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PowderSnowBlock.class)
public class PowderSnowMixin {
    /**
     * Short-circuit canEntityWalkOnPowderSnow for Ocean passive players so they
     * walk on top of powder snow without sinking. An @Inject at HEAD is simpler
     * and more resilient across mappings changes than the previous @Redirect on
     * getItemBySlot, which was fragile against method-rename in 1.21.x.
     */
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void ocean$allowPowderSnowWalk(Entity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, order -> order.canWalkOnPowderSnow(player))) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Prevent Ocean players from being frozen/slowed by powder snow blocks.
     * Also zeroes out any accumulated freeze ticks so the frost overlay clears
     * immediately rather than fading out over the next few ticks.
     */
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void ocean$preventPowderSnowSlow(BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, Entity entity,
            net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean hasPowderSnowBoots,
            CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, (order) -> order.canWalkOnPowderSnow(player))) {
                entity.setTicksFrozen(0);
                ci.cancel();
            }
        }
    }
}