package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiConsumer;
import freq.ascension.managers.AbilityManager;
import freq.ascension.orders.Order;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

@Mixin(AnvilMenu.class)
public abstract class AnvilPrepareMixin {

    // We shadow the 'player' field which exists in the parent ItemCombinerMenu.
    // Since AnvilMenu is a child, it has access to this field.
    @Shadow
    @Final
    protected Player player;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void onAnvilUpdate(CallbackInfo ci) {
        // Since we are on the server, we cast to ServerPlayer
        if (this.player instanceof ServerPlayer serverPlayer) {
            AbilityManager.broadcast(serverPlayer, (order) -> order.onAnvilPrepare((AnvilMenu) (Object) this));
        }
    }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40))
    private int modifyAnvilLimit(int constant) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            if (AbilityManager.anyMatch(serverPlayer, Order::ignoreAnvilCostLimit)) {
                return Integer.MAX_VALUE;
            }
        }
        return constant;
    }

    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"))
    private void redirectAnvilDamage(ContainerLevelAccess access, BiConsumer<Level, BlockPos> action) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            if (AbilityManager.anyMatch(serverPlayer, Order::preventAnvilDamage)) {
                return;
            }
        }
        access.execute(action);
    }
}