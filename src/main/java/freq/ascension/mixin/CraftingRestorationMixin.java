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
        // Slot mapping in the 3x3 grid (0-indexed): 0-8 (top-left to bottom-right)
        // ECHO_SLOT=1, GOLD=3&5, TOTEM=4, AMETHYST=7
        int echo = slots.get(1).getItem().getCount();
        int gold = slots.get(3).getItem().getCount() + slots.get(5).getItem().getCount();
        int totem = slots.get(4).getItem().getCount();
        int amethyst = slots.get(7).getItem().getCount();

        return Math.min(Math.min(gold / 2, totem), Math.min(echo, amethyst));
    }

    @Unique
    private void consumeIngredients(List<Slot> slots, int amount) {
        slots.get(1).remove(amount); // Echo
        slots.get(4).remove(amount); // Totem
        slots.get(7).remove(amount); // Amethyst

        // Split gold consumption across two slots
        int remainingGold = amount * 2;
        remainingGold = takeFromSlot(slots, 3, remainingGold);
        takeFromSlot(slots, 5, remainingGold);
    }

    @Unique
    private int takeFromSlot(List<Slot> slots, int index, int amount) {
        ItemStack stack = slots.get(index).getItem();
        int canTake = Math.min(stack.getCount(), amount);
        slots.get(index).remove(canTake);
        return amount - canTake;
    }
}