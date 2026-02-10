package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMixin {

    @Unique
    private Player ascension$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void ascension$capturePlayer(int syncId, Inventory inventory,
            net.minecraft.world.inventory.ContainerLevelAccess access, CallbackInfo ci) {
        this.ascension$player = inventory.player;
    }

    @ModifyVariable(method = "slotsChanged", at = @At(value = "STORE"), ordinal = 0)
    private int modifyEnchantmentCost(int cost) {
        if (this.ascension$player instanceof ServerPlayer serverPlayer) {
            int[] modifiedCost = { cost };
            AbilityManager.broadcast(serverPlayer, (order) -> {
                modifiedCost[0] = order.modifyEnchantmentCost(modifiedCost[0]);
            });
            return modifiedCost[0];
        }
        return cost;
    }
}