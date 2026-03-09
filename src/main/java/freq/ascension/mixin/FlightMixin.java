package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.MinecraftServer;
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
        if (player.isCreative() || player.isSpectator()) return;

        // Always cancel vanilla: non-creative players must not be able to enable flight.
        ci.cancel();

        // Capture packet data on the network thread (safe reads).
        boolean isNowFlying = packet.isFlying();
        boolean wasFlying   = player.getAbilities().flying;

        AscensionData data = (AscensionData) player;
        boolean isSkyPassive = data.getPassive() != null
                && data.getPassive().getOrderName().equals(Sky.INSTANCE.getOrderName());

        // All further work (VFXBuilder entity spawning, level access, ability dispatch)
        // MUST run on the server thread. Injecting at HEAD means we're on the Netty I/O
        // thread, before PacketUtils.ensureRunningOnSameThread has had a chance to
        // re-dispatch — calling addFreshEntity or player state setters from this thread
        // causes the NullPointerException seen during double-jump.
        MinecraftServer srv = Ascension.getServer();
        if (srv == null) return;
        srv.executeIfPossible(() -> {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            if (isSkyPassive && isNowFlying && !wasFlying) {
                AbilityManager.broadcast(player, order -> order.onToggleFlight(player));
            }
        });
    }
}