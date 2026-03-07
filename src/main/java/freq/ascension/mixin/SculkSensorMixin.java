package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.PlantProximityManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

@Mixin(VibrationSystem.Listener.class)
public abstract class SculkSensorMixin {

    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void onHandleGameEvent(ServerLevel level, Holder<GameEvent> gameEvent, GameEvent.Context context,
            net.minecraft.world.phys.Vec3 pos, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = context.sourceEntity();

        if (entity instanceof ServerPlayer player) {
            // Use synchronous check to avoid staleness from the 5-tick cache update interval
            if (PlantProximityManager.isNearPlantSync(player)) {
                boolean hasEffect = AbilityManager.anyMatch(player,
                        (order) -> order.hasPlantProximityEffect(player));

                if (hasEffect) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
