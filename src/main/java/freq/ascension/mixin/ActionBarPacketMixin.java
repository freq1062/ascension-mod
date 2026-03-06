package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

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
        // Only check action bar packets
        if (packet instanceof ClientboundSetActionBarTextPacket actionBarPacket) {
            Component message = actionBarPacket.text();
            // Get plain text without formatting codes and convert to lowercase for
            // comparison
            String text = message.getString().toLowerCase();

            // Cancel DisguiseLib's "You are disguised as..." message
            // Check for various forms of the message
            if (text.contains("disguised as") ||
                    text.contains("you are disguised") ||
                    (text.contains("disguised") && text.length() < 100)) { // Short messages containing "disguised"
                ci.cancel();
            }
        }
    }
}
