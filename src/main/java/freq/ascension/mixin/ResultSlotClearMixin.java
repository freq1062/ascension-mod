package freq.ascension.mixin;

import freq.ascension.items.ChallengerSigil;
import freq.ascension.items.InfluenceItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * <p>Two injections are used for robustness:
 * <ul>
 *   <li>HEAD — fires before vanilla's ingredient-consumption logic. Directly verifies
 *       the crafting grid matches the influence recipe pattern and clears all 9 slots
 *       immediately, guaranteeing corners are consumed even if vanilla fails to match
 *       the recipe against our custom InfluenceItem result.</li>
 *   <li>RETURN — safety net for cases where vanilla does consume correctly but
 *       might miss slots, and also handles the ChallengerSigil recipe.</li>
 * </ul>
 *
 * <p>Mixin is required because there is no Fabric API hook that fires after result-slot
 * pickup with access to the crafting container slots.
 */
@Mixin(ResultSlot.class)
public class ResultSlotClearMixin {

    /**
     * Fires at the start of onTake, before vanilla's recipe-matching and ingredient
     * consumption logic. Clears all 9 crafting input slots when the influence recipe
     * pattern is detected, ensuring corners are always consumed.
     */
    @Inject(method = "onTake", at = @At("HEAD"))
    private void ascension$clearInfluenceRecipeBeforeVanilla(Player player, ItemStack taken, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!(sp.containerMenu instanceof CraftingMenu craftingMenu)) return;

        // The result slot is already cleared before onTake fires, so use the `taken`
        // parameter directly. Accept either our custom InfluenceItem or the vanilla
        // amethyst_shard result in case the replacement hasn't fired yet.
        if (!InfluenceItem.isInfluenceItem(taken) && !taken.is(Items.AMETHYST_SHARD)) return;

        if (!matchesInfluencePattern(craftingMenu)) return;

        // Clear all 9 crafting input slots before vanilla attempts consumption.
        for (int i = 1; i <= 9; i++) {
            craftingMenu.getSlot(i).set(ItemStack.EMPTY);
        }
    }

    /**
     * Fires after onTake returns as a safety net. Handles the ChallengerSigil recipe and
     * catches any edge cases where vanilla consumed some ingredients but left others behind.
     */
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

    /** Returns true only if the 3×3 crafting grid contains the influence recipe (AAA/BCB/DDD). */
    private static boolean matchesInfluencePattern(CraftingMenu menu) {
        // Row 0 (slots 1–3): echo_shard × 3
        if (!menu.getSlot(1).getItem().is(Items.ECHO_SHARD)) return false;
        if (!menu.getSlot(2).getItem().is(Items.ECHO_SHARD)) return false;
        if (!menu.getSlot(3).getItem().is(Items.ECHO_SHARD)) return false;
        // Row 1 (slots 4–6): netherite_scrap, totem_of_undying, netherite_scrap
        if (!menu.getSlot(4).getItem().is(Items.NETHERITE_SCRAP)) return false;
        if (!menu.getSlot(5).getItem().is(Items.TOTEM_OF_UNDYING)) return false;
        if (!menu.getSlot(6).getItem().is(Items.NETHERITE_SCRAP)) return false;
        // Row 2 (slots 7–9): gold_block × 3
        if (!menu.getSlot(7).getItem().is(Items.GOLD_BLOCK)) return false;
        if (!menu.getSlot(8).getItem().is(Items.GOLD_BLOCK)) return false;
        if (!menu.getSlot(9).getItem().is(Items.GOLD_BLOCK)) return false;
        return true;
    }
}
