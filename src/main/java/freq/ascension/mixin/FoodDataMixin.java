package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    private static final ThreadLocal<ServerPlayer> currentPlayer = ThreadLocal.withInitial(() -> null);

    public static void setCurrentPlayer(ServerPlayer player) {
        currentPlayer.set(player);
    }

    @ModifyVariable(method = "eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private ItemStack modifyFoodSaturation(ItemStack stack) {
        ServerPlayer player = currentPlayer.get();

        if (player == null || AbilityManager.shouldSkipModification()) {
            currentPlayer.remove();
            return stack;
        }

        // We can't directly modify the ItemStack's food properties,
        // so we need a different approach
        currentPlayer.remove();
        return stack;
    }

    @ModifyVariable(method = "eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F", ordinal = 1), ordinal = 1)
    private float modifySaturationValue(float saturation) {
        ServerPlayer player = currentPlayer.get();

        if (player == null || AbilityManager.shouldSkipModification()) {
            return saturation;
        }

        float[] modifiedSaturation = { saturation };

        AbilityManager.broadcast(player, (order) -> {
            modifiedSaturation[0] = order.modifySaturation(player, modifiedSaturation[0]);
        });

        currentPlayer.remove();
        return modifiedSaturation[0];
    }
}
