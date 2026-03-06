package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PowderSnowBlock.class)
public class PowderSnowMixin {
    /**
     * Hook into the static method that determines if an entity can walk on powder
     * snow.
     * This is the primary check used by collision detection.
     */
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void ocean$allowCustomSnowWalking(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, (order) -> order.canWalkOnPowderSnow(player))) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Prevent Ocean players from being stuck/slowed by powder snow blocks.
     * This ensures they can move through powder snow as if it's not there when
     * walking on top.
     */
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void ocean$preventPowderSnowSlow(BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, Entity entity,
            net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean hasPowderSnowBoots,
            CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, (order) -> order.canWalkOnPowderSnow(player))) {
                // Cancel the entityInside logic which makes entities sink/freeze
                ci.cancel();
            }
        }
    }
}