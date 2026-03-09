package freq.ascension.mixin;

import freq.ascension.items.ChallengerSigil;
import freq.ascension.items.InfluenceItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a player takes our custom-crafted InfluenceItem or ChallengerSigil from the result
 * slot, forcibly clear all 9 crafting input slots.
 *
 * <p>The vanilla crafting engine consumes ingredients based on the matched JSON recipe
 * pattern, which may leave corner/filler slots untouched if the JSON recipe definition
 * did not include all 9 positions. This mixin ensures every input slot is cleared.
 *
 * <p>Mixin is required because there is no Fabric API hook that fires after result-slot
 * pickup with access to the crafting container slots.
 */
@Mixin(ResultSlot.class)
public class ResultSlotClearMixin {

    @Inject(method = "onTake", at = @At("RETURN"))
    private void ascension$clearCustomRecipeIngredients(Player player, ItemStack taken, CallbackInfo ci) {
        if (!InfluenceItem.isInfluenceItem(taken) && !ChallengerSigil.isSigil(taken)) return;
        if (!(player instanceof ServerPlayer sp)) return;
        if (!(sp.containerMenu instanceof CraftingMenu craftingMenu)) return;
        // CraftingMenu slot layout: 0 = result, 1–9 = 3×3 crafting grid
        for (int i = 1; i <= 9; i++) {
            Slot slot = craftingMenu.getSlot(i);
            slot.set(ItemStack.EMPTY);
        }
    }
}
