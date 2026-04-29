package freq.ascension.mixin;

import freq.ascension.items.ChallengerSigil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the crafting grid update to produce a Challenger's Sigil when the correct
 * pattern is arranged (ABA-BCB-ABA):
 * <pre>
 *   Row 0: gold_block, copper_block, gold_block
 *   Row 1: copper_block, ominous_trial_key, copper_block
 *   Row 2: gold_block, copper_block, gold_block
 * </pre>
 * Copper slots accept both {@code copper_block} and {@code waxed_copper_block}.
 * The JSON recipe file handles recipe-book visibility; this mixin replaces the plain
 * output with the fully-component-decorated sigil at craft time.
 *
 * <p>Mixin is required because JSON recipes cannot set custom DataComponents on the output.
 */
@Mixin(CraftingMenu.class)
public abstract class SigilCraftingMixin {

    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void onSigilCraft(AbstractContainerMenu menu, ServerLevel level, Player player,
            CraftingContainer container, ResultContainer resultContainer, RecipeHolder<?> recipe,
            CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;

        if (container.getWidth() != 3 || container.getHeight() != 3) {
            return;
        }

        // ABA-BCB-ABA: corners + cross-edges = gold_block; diagonal edges = copper; centre = ominous_trial_key
        // Slot layout: 0 1 2 / 3 4 5 / 6 7 8
        ItemStack s0 = container.getItem(0);
        ItemStack s1 = container.getItem(1);
        ItemStack s2 = container.getItem(2);
        ItemStack s3 = container.getItem(3);
        ItemStack s4 = container.getItem(4);
        ItemStack s5 = container.getItem(5);
        ItemStack s6 = container.getItem(6);
        ItemStack s7 = container.getItem(7);
        ItemStack s8 = container.getItem(8);

        // Row 0: gold, copper, gold
        boolean row0ok = s0.is(Items.GOLD_BLOCK) && isCopper(s1.getItem()) && s2.is(Items.GOLD_BLOCK);
        // Row 1: copper, ominous_trial_key, copper
        boolean row1ok = isCopper(s3.getItem()) && s4.is(Items.OMINOUS_TRIAL_KEY) && isCopper(s5.getItem());
        // Row 2: gold, copper, gold
        boolean row2ok = s6.is(Items.GOLD_BLOCK) && isCopper(s7.getItem()) && s8.is(Items.GOLD_BLOCK);

        if (!row0ok || !row1ok || !row2ok) return;

        resultContainer.setItem(0, ChallengerSigil.createSigil());
    }

    /** Accepts only unwaxed or waxed copper block (not exposed/weathered/oxidized variants). */
    private static boolean isCopper(Item item) {
        return item == Items.COPPER_BLOCK || item == Items.WAXED_COPPER_BLOCK;
    }
}
