package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    private static final ThreadLocal<ServerPlayer> currentPlayer = ThreadLocal.withInitial(() -> null);

    public static void setCurrentPlayer(ServerPlayer player) {
        currentPlayer.set(player);
    }

    // Intercept the private add(int, float) method to modify saturation before
    // clamping.
    // The target Mth.clamp(FFF)F is called as: Mth.clamp(f + this.saturationLevel,
    // 0.0F, maxSaturation)
    @ModifyArg(method = "add(IF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"), index = 0)
    private float modifySaturationValue(float value) {
        ServerPlayer player = currentPlayer.get();

        if (player == null || AbilityManager.shouldSkipModification()) {
            currentPlayer.remove();
            return value;
        }

        float[] modifiedSaturation = { value };

        AbilityManager.broadcast(player, (order) -> {
            modifiedSaturation[0] = order.modifySaturation(player, modifiedSaturation[0]);
        });

        currentPlayer.remove();
        return modifiedSaturation[0];
    }
}
