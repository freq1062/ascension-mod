package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@Mixin(Player.class)
public abstract class LavaSwimmingMixin {

    /**
     * Allow Nether order players to swim in lava by treating it like water
     */
    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void allowLavaSwimming(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Check if this is a Nether order player with passive capability
        boolean canSwimInLava = AbilityManager.anyMatch(serverPlayer, (order) -> order.canSwimInlava(serverPlayer));

        if (!canSwimInLava) {
            return;
        }

        // If in lava, handle swimming
        if (player.isInLava()) {
            // Enable swimming in lava (simplified - removed isEyeInFluid check)
            // TODO: Add proper fluid eye check when correct method is found
            player.setSwimming(true);
            ci.cancel();
        }
    }

    /**
     * Allow Nether order players to move in lava like in water
     */
    @Inject(method = "travel", at = @At("HEAD"))
    private void modifyLavaTravel(Vec3 travelVector, CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Check if this is a Nether order player with passive capability
        boolean canSwimInLava = AbilityManager.anyMatch(serverPlayer,
                (order) -> order.getOrderName().equals("nether") && order.hasCapability(serverPlayer, "passive"));

        if (!canSwimInLava || !player.isInLava()) {
            return;
        }

        // Make lava movement feel more like water
        // This helps with vertical movement control
        Vec3 motion = player.getDeltaMovement();

        // If player is looking up/down while in lava, give them better vertical control
        if (player.isSwimming() && player.isInLava()) {
            double verticalBoost = player.getXRot() < -20 ? 0.04 : (player.getXRot() > 20 ? -0.04 : 0);
            if (verticalBoost != 0) {
                player.setDeltaMovement(motion.add(0, verticalBoost, 0));
            }
        }
    }

    // TODO: Fix this injection - isEyeInFluid doesn't exist on Player class in MC
    // 1.21.10
    // Need to find the correct method name or target Entity class instead
    /*
     * @Inject(method = "isEyeInFluid", at = @At("HEAD"), cancellable = true)
     * private void
     * treatLavaAsWater(net.minecraft.tags.TagKey<net.minecraft.world.level.material
     * .Fluid> fluidTag,
     * CallbackInfoReturnable<Boolean> cir) {
     * Player player = (Player) (Object) this;
     * 
     * if (!(player instanceof ServerPlayer serverPlayer)) {
     * return;
     * }
     * 
     * // Check if this is a Nether order player with passive capability
     * boolean canSwimInLava = AbilityManager.anyMatch(serverPlayer,
     * (order) -> order.getOrderName().equals("nether") &&
     * order.hasCapability(serverPlayer, "passive"));
     * 
     * if (!canSwimInLava) {
     * return;
     * }
     * 
     * // If checking for water and player is in lava, return true to enable
     * swimming
     * // mechanics
     * if (fluidTag == FluidTags.WATER && player.isEyeInFluid(FluidTags.LAVA)) {
     * cir.setReturnValue(true);
     * }
     * }
     */
}
