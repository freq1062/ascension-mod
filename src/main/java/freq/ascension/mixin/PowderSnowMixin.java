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

    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void ocean$allowPowderSnowWalk(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer player && hasAbility(player)) {
            if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty()) {
                cir.setReturnValue(!player.isShiftKeyDown());
            }
        }
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void ocean$insidePowderSnow(BlockState state, Level level, BlockPos pos,
            Entity entity, InsideBlockEffectApplier effectApplier, boolean hasPowderSnowBoots, CallbackInfo ci) {

        if (!(entity instanceof ServerPlayer player) || !hasAbility(player))
            return;

        if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty())

            if (player.isShiftKeyDown()) {
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
