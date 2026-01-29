package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;

import freq.ascension.managers.AbilityManager;
import freq.ascension.orders.Order;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FlightMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void onToggleFlight(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        boolean isNowFlying = packet.isFlying();
        boolean wasFlying = player.getAbilities().flying;
        if (isNowFlying && !wasFlying) {
            if (AbilityManager.anyMatch(player, Order::isDoubleJumpEnabled)) {
                AbilityManager.broadcast(player, (order) -> order.onToggleFlight(player));
                ci.cancel();
            }
        }
    }
}