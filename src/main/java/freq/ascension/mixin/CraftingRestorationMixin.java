package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;

import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(AbstractContainerMenu.class)
public abstract class CraftingRestorationMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void onCraftingClick(int slotIndex, int button, ClickType clickType, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        // 1. Only process if it's a CraftingMenu and the Result Slot (Slot 0)
        if (!(menu instanceof CraftingMenu craftingMenu))
            return;
        if (slotIndex != 0)
            return;

        // 2. Get the result item
        Slot resultSlot = menu.getSlot(0);
        ItemStack resultStack = resultSlot.getItem();

        // 3. Verify it's our Influence Item
        if (!InfluenceItem.isInfluenceItem(resultStack))
            return;

        // 4. Server-side only logic
        if (player instanceof ServerPlayer serverPlayer) {
            AscensionData data = (AscensionData) serverPlayer;
            int currentInfluence = data.getInfluence();

            // Eligibility check
            if (currentInfluence >= 0) {
                serverPlayer.sendSystemMessage(
                        Component.literal("Your Influence is not negative.").withStyle(ChatFormatting.RED));
                ci.cancel(); // Stop the click entirely
                return;
            }

            // Access the 3x3 crafting grid slots (indices 0-8, top-left to bottom-right)
            List<Slot> inputSlots = craftingMenu.getInputGridSlots();
            int maxPossible = calculateMaxCrafts(inputSlots);
            if (maxPossible <= 0)
                return;

            // Handle Shift Click vs Single Click
            int needed = -currentInfluence;
            int toRestore = (clickType == ClickType.QUICK_MOVE) ? Math.min(maxPossible, needed) : 1;

            // 5. Execute Restoration
            consumeIngredients(inputSlots, toRestore);
            data.addInfluence(toRestore);

            serverPlayer.sendSystemMessage(Component.literal("Your Influence has been restored by " + toRestore + "!")
                    .withStyle(ChatFormatting.GOLD));

            // 6. Update the menu and CANCEL the standard item pickup
            menu.broadcastChanges();
            ci.cancel();
        }
    }

    @Unique
    private int calculateMaxCrafts(List<Slot> slots) {
        // Recipe AAA/BCB/DDD uses one item from each of the 9 grid slots per craft.
        // Max crafts = minimum count across all 9 slots.
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            int count = slots.get(i).getItem().getCount();
            if (count == 0) return 0;
            if (count < min) min = count;
        }
        return min;
    }

    @Unique
    private void consumeIngredients(List<Slot> slots, int amount) {
        // Consume one item from each of the 9 crafting grid slots per craft unit.
        for (int i = 0; i < 9; i++) {
            slots.get(i).remove(amount);
        }
    }
}