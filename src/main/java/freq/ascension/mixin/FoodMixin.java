package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

@Mixin(LivingEntity.class)
public abstract class FoodMixin {

    @ModifyVariable(method = "eat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V"), argsOnly = true)
    private ItemStack modifyFoodBeforeEating(ItemStack stack) {
        if (AbilityManager.shouldSkipModification()) {
            return stack;
        }

        LivingEntity entity = (LivingEntity) (Object) this;
        
        if (!(entity instanceof ServerPlayer serverPlayer)) {
            return stack;
        }

        // Store the player context so we can modify saturation in FoodData
        FoodDataMixin.setCurrentPlayer(serverPlayer);
        
        return stack;
    }
}
