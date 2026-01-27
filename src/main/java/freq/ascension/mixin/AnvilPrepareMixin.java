package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicBoolean;

import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.AscensionData; 
import freq.ascension.orders.Order;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

@Mixin(AnvilMenu.class)
public abstract class AnvilPrepareMixin {

    @Inject(method = "createResult", at = @At("TAIL"))
    private void onAnvilUpdate(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ServerPlayer player = getServerPlayer(menu);
        if (player != null) {
            AbilityManager.broadcast(player, (order) -> order.onAnvilPrepare(menu));
        }
    }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40))
    private int modifyAnvilLimit(int constant) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ServerPlayer player = getServerPlayer(menu);
        if (player != null) {
            AscensionData data = (AscensionData) player;
            for (Order order : data.getEquippedOrders()) {
                if (order.ignoreAnvilCostLimit()) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return constant;
    }

    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"))
    private void redirectAnvilDamage(ContainerLevelAccess access, BiConsumer<Level, BlockPos> action) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ServerPlayer player = getServerPlayer(menu);
        
        if (player != null) {
            AtomicBoolean prevent = new AtomicBoolean(false);
            AbilityManager.broadcast(player, (order) -> {
                if (order.preventAnvilDamage()) prevent.set(true);
            });
            if (prevent.get()) return;
        }
        
        access.execute(action);
    }

    private ServerPlayer getServerPlayer(AnvilMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory inventory
                    && inventory.player instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }
        return null;
    }
}