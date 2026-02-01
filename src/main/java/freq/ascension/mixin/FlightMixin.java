package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import freq.ascension.Ascension;
import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Sky;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FlightMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void onToggleFlight(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {

        AscensionData data = (AscensionData) player;
        boolean isSkyPassive = data.getPassive() != null
                && data.getPassive().getOrderName().equals(Sky.INSTANCE.getOrderName());
        boolean isNowFlying = packet.isFlying();
        boolean wasFlying = player.getAbilities().flying;
        if (!player.isCreative() && !player.isSpectator()) {
            if (isSkyPassive && isNowFlying && !wasFlying) {
                Ascension.LOGGER.info("Flight enabled");
                AbilityManager.broadcast(player, (order) -> order.onToggleFlight(player));
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                ci.cancel();
            } else if (!isSkyPassive) {
                ci.cancel();
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }

    }
}