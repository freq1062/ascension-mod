package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;

/**
 * Mixin to intercept and cancel DisguiseLib action bar messages
 * that show "You are disguised as..." text.
 * 
 * Rationale: No Fabric API event exists for action bar packets,
 * and DisguiseLib sends these messages directly without a way
 * to disable them via its API. This is the minimal approach to
 * intercept and filter unwanted action bar messages from DisguiseLib.
 */
@Mixin(Connection.class)
public class ActionBarPacketMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(net.minecraft.network.protocol.Packet<?> packet, CallbackInfo ci) {
        if (cancelIfDisguiseMessage(packet)) {
            ci.cancel();
        }
    }

    /**
     * DisguiseLib may also route through the 2-arg send(Packet,
     * ChannelFutureListener)
     * overload. Intercept that path too so the "You are disguised as..." action bar
     * message is filtered regardless of which send overload is used.
     *
     * <p>
     * Rationale: No Fabric API event exists for this overload, and DisguiseLib
     * does not provide a way to disable its action bar messages via its own API.
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacketWithListener(net.minecraft.network.protocol.Packet<?> packet,
            io.netty.channel.ChannelFutureListener listener, CallbackInfo ci) {
        if (cancelIfDisguiseMessage(packet)) {
            ci.cancel();
        }
    }

    private static boolean cancelIfDisguiseMessage(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet instanceof ClientboundSetActionBarTextPacket actionBarPacket) {
            Component message = actionBarPacket.text();
            String text = message.getString().toLowerCase();
            // Only filter DisguiseLib's specific messages - be precise to avoid blocking
            // spell cooldowns
            return text.startsWith("you are disguised as") ||
                    text.startsWith("you are now disguised as") ||
                    text.startsWith("disguised as");
        }
        return false;
    }
}
