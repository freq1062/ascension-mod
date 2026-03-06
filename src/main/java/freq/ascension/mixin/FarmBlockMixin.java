package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FarmBlock.class)
public class FarmBlockMixin {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void flora$preventCropTrampling(Level level, BlockState state, BlockPos pos, Entity entity,
            double fallDistance, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, (order) -> !order.canTrampleCrops(player))) {
                ci.cancel();
            }
        }
    }
}
