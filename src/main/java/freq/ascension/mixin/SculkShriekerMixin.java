package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.PlantProximityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerMixin {

    @Inject(method = "tryToWarn", at = @At("HEAD"), cancellable = true)
    private void onTryToWarn(ServerLevel level, net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            // Check if player has plant proximity effect active and is near a plant
            if (PlantProximityManager.isNearPlant(serverPlayer)) {
                boolean hasEffect = AbilityManager.anyMatch(serverPlayer, 
                    (order) -> order.hasPlantProximityEffect(serverPlayer));
                
                if (hasEffect) {
                    ci.cancel();
                }
            }
        }
    }
}
