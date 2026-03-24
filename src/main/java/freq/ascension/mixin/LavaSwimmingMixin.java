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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Grants Nether-order players a swimming pose and full input-integrated movement
 * while submerged in lava.
 *
 * <p>
 * Uses entity shared flag 4 (isSwimming) for the swimming pose instead of
 * flag 7 (elytra). No sprint requirement — any lava contact activates glide.
 *
 * <p>
 * Velocity is set at TAIL to override vanilla lava drag applied earlier in
 * the same tick. Horizontal/vertical speeds are pre-compensated for the lava
 * drag (0.5× horizontal, 0.8× vertical) that will be applied on the NEXT tick.
 *
 * <p>
 * Injected at TAIL so our flag write occurs after vanilla may have cleared it
 * earlier in the same tick. No Fabric event provides an equivalent hook.
 */
@Mixin(Player.class)
public abstract class LavaSwimmingMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void manageLavaGlide(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // Cast to the invoker interface to access protected Entity.setSharedFlag
        EntitySharedFlagInvoker flagSetter = (EntitySharedFlagInvoker) player;

        boolean canSwim = AbilityManager.anyMatch(serverPlayer,
                order -> order.canSwimInlava(serverPlayer));

        boolean wasActive = LavaFlightManager.isActive(serverPlayer.getUUID());

        if (!canSwim) {
            if (wasActive) {
                LavaFlightManager.setActive(serverPlayer.getUUID(), false);
                flagSetter.invokeSetSharedFlag(4, false);
            }
            return;
        }

        boolean shouldBeActive = serverPlayer.isInLava();

        if (shouldBeActive && !wasActive) {
            LavaFlightManager.setActive(serverPlayer.getUUID(), true);
            flagSetter.invokeSetSharedFlag(4, true);
        } else if (!shouldBeActive && wasActive) {
            LavaFlightManager.setActive(serverPlayer.getUUID(), false);
            flagSetter.invokeSetSharedFlag(4, false);
        }

        if (LavaFlightManager.isActive(serverPlayer.getUUID())) {
            // Maintain swimming pose every tick — written at TAIL to override any
            // vanilla reset that ran earlier in the same tick.
            flagSetter.invokeSetSharedFlag(4, true);

            AscensionData data = (AscensionData) serverPlayer;
            boolean isNetherGod = "nether".equalsIgnoreCase(data.getGodOrder());

            // Pre-compensate for lava drag applied next tick:
            //   horizontal drag = 0.5×, vertical drag = 0.8×
            float speedH = isNetherGod ? 0.55f : 0.38f;
            float speedV = isNetherGod ? 0.40f : 0.28f;

            Vec3 look = serverPlayer.getLookAngle(); // normalized, includes pitch

            // Right vector: perpendicular to look, horizontal only
            float yawRad = serverPlayer.getYRot() * (float) (Math.PI / 180.0);
            Vec3 right = new Vec3(
                    -Mth.sin(yawRad + (float) (Math.PI / 2)),
                    0,
                    Mth.cos(yawRad + (float) (Math.PI / 2)));

            float fwd = serverPlayer.zza;  // forward/backward input
            float stf = serverPlayer.xxa;  // strafe input
            float vrt = serverPlayer.getLastClientInput().jump() ? 1.0f
                    : (serverPlayer.isShiftKeyDown() ? -1.0f : 0.0f);

            double vx = look.x * fwd * speedH + right.x * stf * speedH;
            double vy = look.y * fwd * speedH + vrt * speedV;
            double vz = look.z * fwd * speedH + right.z * stf * speedH;

            // Clamp diagonal movement to the same max speed as cardinal movement
            double mag = Math.sqrt(vx * vx + vz * vz + vy * vy);
            if (mag > speedH) {
                double scale = speedH / mag;
                vx *= scale;
                vy *= scale;
                vz *= scale;
            }

            serverPlayer.setDeltaMovement(vx, vy, vz);

            Nether.recordFireContact(serverPlayer);
        }
    }
}
