package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;

import freq.ascension.managers.AbilityManager;

@Mixin(ServerPlayer.class)
public abstract class FlightMixin {

    @Inject(method = "setFlying", at = @At("HEAD"))
    private void onToggleFlight(boolean flying, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // Triggers whenever p.getAbilities().flying is changed
        AbilityManager.broadcast(player, (order) -> order.onToggleFlight(player, flying));
    }
}