package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.LavaFlightManager;
import freq.ascension.orders.Nether;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Grants Nether-order players elytra-style creative flight while in lava.
 * Flight is enabled on lava entry and disabled on lava exit.
 *
 * <p>Replaces the previous swimming-mode approach (updateSwimming / travel injections)
 * which instantly reset because Minecraft does not support swimming state in lava.
 *
 * <p>No Fabric event exists that intercepts the per-tick player ability update before
 * the server sends ability packets, so a Player tick Mixin is the correct hook here.
 */
@Mixin(Player.class)
public abstract class LavaSwimmingMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void manageLavaFlight(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        boolean canSwimInLava = AbilityManager.anyMatch(serverPlayer,
                order -> order.canSwimInlava(serverPlayer));

        if (!canSwimInLava) {
            if (LavaFlightManager.isActive(serverPlayer.getUUID())) {
                ascension$disableLavaFlight(serverPlayer);
            }
            return;
        }

        boolean inLava = serverPlayer.isInLava();
        boolean wasInLavaFlight = LavaFlightManager.isActive(serverPlayer.getUUID());

        if (inLava && !wasInLavaFlight) {
            ascension$enableLavaFlight(serverPlayer);
        } else if (!inLava && wasInLavaFlight) {
            ascension$disableLavaFlight(serverPlayer);
        }
    }

    @Unique
    private static void ascension$enableLavaFlight(ServerPlayer player) {
        LavaFlightManager.setActive(player.getUUID(), true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        Nether.recordFireContact(player);
    }

    @Unique
    private static void ascension$disableLavaFlight(ServerPlayer player) {
        LavaFlightManager.setActive(player.getUUID(), false);
        boolean hasSkyFlight = AbilityManager.anyMatch(player,
                o -> o.getOrderName().equals("sky") && o.hasCapability(player, "passive"));
        if (!hasSkyFlight) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }
}
