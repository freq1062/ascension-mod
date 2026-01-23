package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;

import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

@Mixin(CraftingMenu.class)
public abstract class CraftingEligibilityMixin {

    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void onCraftingUpdate(AbstractContainerMenu menu, ServerLevel level, Player player,
            CraftingContainer container, ResultContainer resultContainer, RecipeHolder<?> recipe, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        ItemStack result = resultContainer.getItem(0);

        // If the result of the current craft is our Influence Item
        if (InfluenceItem.isInfluenceItem(result)) {
            AscensionData data = (AscensionData) serverPlayer;

            if (data.getInfluence() >= 0) {
                // If they aren't in "debt", they can't see/take the restoration item
                resultContainer.setItem(0, ItemStack.EMPTY);
            }
        }
    }
}