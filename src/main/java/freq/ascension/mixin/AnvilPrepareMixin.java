package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
public abstract class AnvilPrepareMixin {

    @Shadow
    private Player player;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void onAnvilUpdate(CallbackInfo ci) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            AnvilMenu menu = (AnvilMenu) (Object) this;
            AbilityManager.broadcast(serverPlayer, (order) -> order.onAnvilPrepare(menu));
        }
    }
}