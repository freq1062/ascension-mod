package freq.ascension.mixin;

import freq.ascension.Ascension;
import freq.ascension.weapons.TempestTrident;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces tracked Tempest Trident entities to remain invisible every tick.
 *
 * <p>Rationale for Mixin: {@link ThrownTrident#tick()} (and the entity-tracking packet
 * pipeline) can reset the shared-flags byte that controls visibility. Calling
 * {@code setInvisible(true)} from outside the tick loop is insufficient — the flag is
 * overwritten before the next packet flush. Injecting at TAIL of {@code tick} guarantees
 * the flag is set last, after all vanilla updates, so the client always receives the
 * correct invisible state.
 *
 * <p>No Fabric API lifecycle event covers per-tick entity state enforcement on a specific
 * entity subtype, making this Mixin the minimal-footprint alternative.
 */
@Mixin(ThrownTrident.class)
public abstract class TridentVisibilityMixin {

    static {
        try {
            Ascension.LOGGER.info("[TridentVisibilityMixin] Mixin class loaded successfully");
        } catch (Exception e) {
            // logger might not be ready at static-init time
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ascension$forceInvisible(CallbackInfo ci) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (TempestTrident.tridentToDisplay.containsKey(self.getUUID())) {
            self.setInvisible(true);
            // Belt-and-suspenders: directly set shared flag 5 (invisible bit) so the
            // entity-tracking metadata flush that fires every 4 ticks cannot overwrite it.
            ((EntitySharedFlagInvoker) self).invokeSetSharedFlag(5, true);
            self.setGlowingTag(false);
        }
    }
}
