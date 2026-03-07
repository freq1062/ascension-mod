package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import freq.ascension.managers.AbilityManager;
import freq.ascension.orders.End;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Intercepts {@link EnderChestBlock#useWithoutItem} on the server side so that players
 * with the End passive equipped see a 4-row custom GUI (27 vanilla + 9 extra slots)
 * instead of the default 27-slot ender chest.
 *
 * <p><b>Why a Mixin?</b> No Fabric API event fires <em>before</em>
 * {@code EnderChestBlock.useWithoutItem()} in 1.21.10, making a Mixin the only reliable
 * way to cancel the vanilla GUI open and substitute the custom one.
 */
@Mixin(EnderChestBlock.class)
public class EnderChestMixin {

    /**
     * Cancels the vanilla ender-chest open and shows the custom 4-row SGUI when
     * the interacting player has the End passive equipped.
     */
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void end$interceptEnderChestOpen(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {

        if (level.isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (!AbilityManager.anyMatch(serverPlayer,
                o -> "end".equals(o.getOrderName()) && o.hasCapability(serverPlayer, "passive"))) {
            return;
        }

        End.INSTANCE.openEnderChestWithExtraRow(serverPlayer);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
