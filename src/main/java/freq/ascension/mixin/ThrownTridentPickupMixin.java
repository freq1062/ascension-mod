package freq.ascension.mixin;

import java.util.UUID;

import freq.ascension.weapons.TempestTrident;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restricts Tempest Trident pickup so only the player who threw it may retrieve it.
 *
 * <p>Rationale for Mixin: Fabric 1.21.10 exposes no event for trident-specific pickup
 * cancellation. {@code PlayerPickupArrowCallback} targets only {@code AbstractArrow}
 * subtypes used as arrows (not tridents). The only injection point that works without
 * additional packet manipulation is {@code ThrownTrident.playerTouch}, which is where
 * vanilla decides whether to add the trident to the player's inventory.
 */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentPickupMixin {

    /**
     * Injected at HEAD of {@code playerTouch} with {@code cancellable = true}.
     * Cancels the interaction if the touching player is not the registered thrower of this
     * Tempest Trident.
     */
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void ascension$restrictTempestTridentPickup(Player player, CallbackInfo ci) {
        ThrownTrident trident = (ThrownTrident) (Object) this;
        UUID tridentId = trident.getUUID();

        UUID registeredThrower = TempestTrident.tridentToThrower.get(tridentId);
        if (registeredThrower == null) {
            // Not a tracked Tempest Trident — check if the item itself is one
            ItemStack stack = trident.getWeaponItem();
            if (!TempestTrident.INSTANCE.isItem(stack)) return;
            // Tempest Trident without a tracked thrower: allow pickup normally
            return;
        }

        // Only the original thrower may pick it up
        if (!player.getUUID().equals(registeredThrower)) {
            ci.cancel();
        }
    }
}
