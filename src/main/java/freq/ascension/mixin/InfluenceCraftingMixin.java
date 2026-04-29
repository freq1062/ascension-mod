package freq.ascension.mixin;

import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the crafting grid update to produce an Influence restoration item when the
 * correct pattern is arranged (AAA-BCB-DDD):
 * <pre>
 *   Row 0: echo_shard, echo_shard, echo_shard
 *   Row 1: netherite_scrap, totem_of_undying, netherite_scrap
 *   Row 2: gold_block, gold_block, gold_block
 * </pre>
 *
 * <p>The JSON recipe file handles recipe-book visibility; this mixin replaces the plain
 * output with the fully-component-decorated InfluenceItem at craft time.
 *
 * <p>This mixin uses the default priority (1000). {@link CraftingEligibilityMixin} also
 * injects at TAIL — it reads the result slot after this mixin sets it, and clears it if
 * the player's influence is not negative. The natural mixin registration order means this
 * mixin runs first, setting the item, then CraftingEligibilityMixin checks and potentially
 * clears it.
 *
 * <p>Mixin is required because JSON recipes cannot set custom DataComponents on the output.
 */
@Mixin(CraftingMenu.class)
public abstract class InfluenceCraftingMixin {

    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void onInfluenceCraft(AbstractContainerMenu menu, ServerLevel level, Player player,
            CraftingContainer container, ResultContainer resultContainer, RecipeHolder<?> recipe,
            CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;

        if (container.getWidth() != 3 || container.getHeight() != 3) {
            return;
        }

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

        // Row 0: echo_shard x3
        boolean row0ok = s0.is(Items.ECHO_SHARD) && s1.is(Items.ECHO_SHARD) && s2.is(Items.ECHO_SHARD);
        // Row 1: netherite_scrap, totem_of_undying, netherite_scrap
        boolean row1ok = s3.is(Items.NETHERITE_SCRAP) && s4.is(Items.TOTEM_OF_UNDYING) && s5.is(Items.NETHERITE_SCRAP);
        // Row 2: gold_block x3
        boolean row2ok = s6.is(Items.GOLD_BLOCK) && s7.is(Items.GOLD_BLOCK) && s8.is(Items.GOLD_BLOCK);

        if (!row0ok || !row1ok || !row2ok) return;

        AscensionData data = (AscensionData) sp;

        resultContainer.setItem(0, InfluenceItem.createItem());
    }
}
