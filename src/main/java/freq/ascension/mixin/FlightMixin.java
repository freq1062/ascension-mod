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

// Changed target from ServerPlayer to ServerGamePacketListenerImpl
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FlightMixin {

    @Shadow
    public ServerPlayer player;

    // Inject into the packet handler instead of a non-existent setter
    @Inject(method = "handlePlayerAbilities", at = @At("TAIL"))
    private void onToggleFlight(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        // Triggers whenever the client sends an ability update (e.g. double tap space)
        // We run at TAIL so the server player's abilities are likely already updated
        boolean flying = packet.isFlying();
        AbilityManager.broadcast(this.player, (order) -> order.onToggleFlight(this.player, flying));
    }
}