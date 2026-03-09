package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import freq.ascension.orders.Nether;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Preserves fire ticks for Nether passive players even when they have fire
 * resistance.
 * This keeps the "on fire" state active so the autocrit-when-on-fire ability
 * works.
 * 
 * Rationale: Vanilla fire resistance causes fire ticks to be cleared/reduced,
 * which
 * breaks Nether's autocrit mechanic that requires the player to be visibly on
 * fire.
 * Water/rain will still extinguish fire normally.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFireMixin {

    /**
     * After aiStep completes, if the player has Nether passive and was recently on
     * fire
     * but now has 0 fire ticks (due to fire resistance clearing them), restore a
     * minimum
     * fire tick count to keep the visual flame effect active.
     * 
     * This allows water/rain to still extinguish fire (by not recording fire
     * contact),
     * while preventing fire resistance from clearing the flames.
     */
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void nether$maintainFireTicksWithResistance(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        // Only affect ServerPlayers with Nether passive
        if (!(self instanceof ServerPlayer player)) {
            return;
        }

        // Check if they have Nether passive equipped
        boolean hasNetherPassive = AbilityManager.anyMatch(player,
                order -> order.hasCapability(player, "passive") && "nether".equals(order.getOrderName()));

        if (!hasNetherPassive) {
            return;
        }

        // If player was recently in fire but fire ticks got cleared (and they're not in
        // water),
        // restore some fire ticks to keep the visual effect
        if (Nether.wasRecentlyOnFire(player) && self.getRemainingFireTicks() == 0 && !self.isInWaterOrRain()) {
            // Set to 20 ticks (1 second) to maintain the visual effect
            // This will be refreshed each tick as long as they're standing in fire
            self.setRemainingFireTicks(20);
        }
    }
}
