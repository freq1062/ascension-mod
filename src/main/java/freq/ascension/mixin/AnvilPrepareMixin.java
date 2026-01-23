package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
public abstract class AnvilPrepareMixin {

    @Inject(method = "createResult", at = @At("TAIL"))
    private void onAnvilUpdate(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory inventory
                    && inventory.player instanceof ServerPlayer serverPlayer) {
                AbilityManager.broadcast(serverPlayer, (order) -> order.onAnvilPrepare(menu));
                break;
            }
        }
    }
}