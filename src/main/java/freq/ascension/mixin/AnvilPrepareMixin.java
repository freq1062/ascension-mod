package freq.ascension.mixin;

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
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

@Mixin(AnvilMenu.class)
public abstract class AnvilPrepareMixin {

    // We capture the player from the constructor because shadowing the inherited
    // field
    // from ItemCombinerMenu can fail if the mapping context is incomplete.
    @org.spongepowered.asm.mixin.Unique
    private Player ascension$player;

    // Direct access to AnvilMenu.cost DataSlot — avoids reflection entirely
    @Shadow private DataSlot cost;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void ascension$capturePlayer(int syncId, net.minecraft.world.entity.player.Inventory inventory,
            ContainerLevelAccess access, CallbackInfo ci) {
        this.ascension$player = inventory.player;
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void onAnvilUpdate(CallbackInfo ci) {
        // Since we are on the server, we cast to ServerPlayer
        if (this.ascension$player instanceof ServerPlayer serverPlayer) {
            AbilityManager.broadcast(serverPlayer, (order) -> order.onAnvilPrepare((AnvilMenu) (Object) this));

            // Earth anvil cost reduction — god gets 90% off (10% of cost), demigod gets 50% off
            if (AbilityManager.anyMatch(serverPlayer,
                    order -> "earth".equals(order.getOrderName()) && order.hasCapability(serverPlayer, "passive"))) {
                int current = this.cost.get();
                if (current > 0) {
                    AscensionData data = (AscensionData) serverPlayer;
                    boolean isGod = "god".equals(data.getRank())
                            && "earth".equalsIgnoreCase(data.getGodOrder());
                    int reduced = isGod
                            ? (int) Math.max(1, Math.floor(current * 0.1))
                            : Math.max(1, current / 2);
                    // Only apply if actually different to avoid double-reducing
                    if (reduced != current) {
                        this.cost.set(reduced);
                    }
                }
            }
        }
    }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40))
    private int modifyAnvilLimit(int constant) {
        if (this.ascension$player instanceof ServerPlayer serverPlayer) {
            // Only bypass the anvil limit when the earth order is in the passive slot
            if (AbilityManager.anyMatch(serverPlayer,
                    order -> order.ignoreAnvilCostLimit() && order.hasCapability(serverPlayer, "passive"))) {
                return Integer.MAX_VALUE;
            }
        }
        return constant;
    }

    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"))
    private void redirectAnvilDamage(ContainerLevelAccess access, BiConsumer<Level, BlockPos> action) {
        if (this.ascension$player instanceof ServerPlayer serverPlayer) {
            if (AbilityManager.anyMatch(serverPlayer, Order::preventAnvilDamage)) {
                return;
            }
        }
        access.execute(action);
    }
}