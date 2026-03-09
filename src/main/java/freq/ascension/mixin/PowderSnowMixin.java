package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import freq.ascension.managers.AbilityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PowderSnowBlock.class)
public class PowderSnowMixin {
    /**
     * Makes Ocean passive players walk on powder snow as if they have leather boots.
     *
     * <p>When the player is <em>not</em> crouching we return {@code true} so that
     * {@code getCollisionShape} gives a solid block shape server-side, keeping the
     * player on top via the normal movement-validation path.
     *
     * <p>When the player <em>is</em> crouching we return {@code false}, matching
     * what the unmodded client already predicts (both sides agree = no
     * rubber-banding), allowing the player to slowly sink through the powder snow
     * via the {@code makeStuckInBlock} velocity dampening inside
     * {@code entityInside}.
     *
     * <p>Note: on a dedicated server, standing on powder snow still produces minor
     * rubber-banding because the unmodded client predicts sinking. The server
     * rejects the position and corrects — this is the inherent limit of a
     * server-side-only implementation.
     */
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void ocean$allowPowderSnowWalk(Entity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, order -> order.canWalkOnPowderSnow(player))) {
                // Solid when standing, passable when crouching (allow sinking through).
                cir.setReturnValue(!player.isShiftKeyDown());
            }
        }
    }

    /**
     * Suppresses the movement-slow and freeze for Ocean passive players who are
     * standing on powder snow (shouldn't be inside the block at all — this fires
     * only because the unmodded client briefly predicts sinking before the server
     * sends a position correction).
     *
     * <p>When the player <em>is</em> crouching we let the method run normally so
     * that {@code makeStuckInBlock} produces the smooth, slow-sinking feel.
     * Freeze ticks are cleared separately each tick via
     * {@code AbilityManager.init()} → {@code ServerTickEvents}.
     */
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void ocean$preventPowderSnowSlow(BlockState state, Level level,
            BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean hasPowderSnowBoots,
            CallbackInfo ci) {
        if (entity instanceof ServerPlayer player && !player.isShiftKeyDown()) {
            if (AbilityManager.anyMatch(player, order -> order.canWalkOnPowderSnow(player))) {
                ci.cancel();
            }
        }
    }
}
