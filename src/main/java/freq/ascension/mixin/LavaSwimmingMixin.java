package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AscensionData;
import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.LavaFlightManager;
import freq.ascension.orders.Nether;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Grants Nether-order players elytra-glide pose and swimming-speed movement while
 * sprinting and submerged in lava.
 *
 * <p>Uses entity shared flag 7 (isFallFlying / elytra pose) for the horizontal
 * lying-flat visual. Creative-mode flight abilities are intentionally untouched so
 * that creative players are unaffected.
 *
 * <p>Injected at TAIL so our flag write occurs after vanilla may have cleared it
 * earlier in the same tick. No Fabric event provides an equivalent hook.
 */
@Mixin(Player.class)
public abstract class LavaSwimmingMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void manageLavaGlide(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // Cast to the invoker interface to access protected Entity.setSharedFlag
        EntitySharedFlagInvoker flagSetter = (EntitySharedFlagInvoker) player;

        boolean canSwim = AbilityManager.anyMatch(serverPlayer,
                order -> order.canSwimInlava(serverPlayer));

        boolean wasActive = LavaFlightManager.isActive(serverPlayer.getUUID());

        if (!canSwim) {
            if (wasActive) {
                LavaFlightManager.setActive(serverPlayer.getUUID(), false);
                flagSetter.invokeSetSharedFlag(7, false);
            }
            return;
        }

        boolean shouldBeActive = serverPlayer.isInLava() && serverPlayer.isSprinting();

        if (shouldBeActive && !wasActive) {
            LavaFlightManager.setActive(serverPlayer.getUUID(), true);
            flagSetter.invokeSetSharedFlag(7, true);
        } else if (!shouldBeActive && wasActive) {
            LavaFlightManager.setActive(serverPlayer.getUUID(), false);
            flagSetter.invokeSetSharedFlag(7, false);
        }

        if (LavaFlightManager.isActive(serverPlayer.getUUID())) {
            // Maintain elytra glide pose (lying flat) every tick — written at TAIL to
            // override any vanilla reset that ran earlier in the same tick.
            flagSetter.invokeSetSharedFlag(7, true);

            // Drive movement in the look direction. Gods (nether rank) get Dolphin's
            // Grace 1 speed (~0.20 b/t); Demigods get normal swimming speed (0.12 b/t).
            Vec3 look = serverPlayer.getLookAngle();
            AscensionData data = (AscensionData) serverPlayer;
            boolean isGod = "god".equals(data.getRank())
                    && "nether".equalsIgnoreCase(data.getGodOrder());
            float speed = isGod ? 0.20f : 0.12f;
            serverPlayer.setDeltaMovement(look.x * speed, look.y * speed, look.z * speed);

            Nether.recordFireContact(serverPlayer);
        }
    }
}
