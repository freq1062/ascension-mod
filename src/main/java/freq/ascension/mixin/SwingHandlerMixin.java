package freq.ascension.mixin;

import freq.ascension.Ascension;
import freq.ascension.managers.AbilityManager;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the weapon shift+left-click callback when the player swings their main hand in air
 * while crouching.
 *
 * <p>Neither {@code AttackEntityCallback} nor {@code AttackBlockCallback} fires for a swing
 * that hits nothing — {@code handleAnimate} is the only server-side handler for that packet.
 * No Fabric API event wraps this specific path, so a Mixin is the only option.
 *
 * <p>Double-fire on actual hits (entity/block) is prevented by the per-player last-toggle-tick
 * deduplication inside {@link freq.ascension.weapons.TempestTrident#onShiftLeftClick}.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class SwingHandlerMixin {

    /** The connection's owning player — public field on {@code ServerGamePacketListenerImpl}. */
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At("HEAD"))
    private void ascension$onHandleAnimate(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (packet.getHand() != InteractionHand.MAIN_HAND) return;
        if (!player.isCrouching()) return;

        // Dispatch to server thread: weapon callbacks (e.g. TempestTrident) access
        // Ascension.getServer().getTickCount() and other server-thread-only state.
        // handleAnimate fires on the Netty I/O thread, so we must re-dispatch.
        MinecraftServer srv = Ascension.getServer();
        if (srv == null) return;
        srv.executeIfPossible(() -> AbilityManager.broadcastWeapon(player, w -> w.onShiftLeftClick(player)));
    }
}
