// package freq.ascension.menus;

// import java.util.ArrayList;
// import java.util.List;

// import eu.pb4.sgui.api.elements.GuiElementBuilder;
// import eu.pb4.sgui.api.gui.SimpleGui;
// import freq.ascension.Utils;
// import freq.ascension.items.InfluenceItem;
// import freq.ascension.managers.AscensionData;
// import freq.ascension.orders.Order;
// import freq.ascension.registry.OrderRegistry;
// import net.minecraft.ChatFormatting;
// import net.minecraft.core.component.DataComponents;
// import net.minecraft.network.chat.Component;
// import net.minecraft.network.chat.Style;
// import net.minecraft.network.chat.TextColor;
// import net.minecraft.server.level.ServerPlayer;
// import net.minecraft.sounds.SoundEvents;
// import net.minecraft.sounds.SoundSource;
// import net.minecraft.world.inventory.MenuType;
// import net.minecraft.world.item.Item;
// import net.minecraft.world.item.ItemStack;
// import net.minecraft.world.item.Items;
// import net.minecraft.world.item.component.ItemLore;

// public class OrderDetailMenu {
// private final Order order;

// public OrderDetailMenu(String order) {
// this.order = OrderRegistry.get(order.toLowerCase());
// }

// public boolean hasAllEquipped(ServerPlayer player) {
// AscensionData data = (AscensionData) player;
// return data.getPassive() == order && data.getUtility() == order &&
// data.getCombat() == order;
// }

// public void open(ServerPlayer player) {
// AscensionData data = (AscensionData) player;
// boolean godMode = hasAllEquipped(player);

// MenuType<?> menuType = godMode ? MenuType.GENERIC_9x2 : MenuType.GENERIC_9x1;
// SimpleGui gui = new SimpleGui(menuType, player, false);

// Component title = Component.literal(Utils.smallCaps("Order - " +
// order.getOrderName()))
// .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(false));
// gui.setTitle(title);

// if (godMode) {
// updateGod(data, gui, player);
// } else {
// updateDemigod(data, gui, player);
// }

// gui.open();
// }

// public void updateDemigod(AscensionData data, SimpleGui gui, ServerPlayer
// player) {
// gui.setSlot(0, InfluenceItem.getInfluenceDisplayItem(data));

// gui.setSlot(2, new GuiElementBuilder(makeSubIcon(Items.BOOK, "passive",
// data.getUnlockedOrder(order.getOrderName()).hasPassive(), data.getPassive()
// == order))
// .setCallback((index, type, action) -> {
// if (deductInfluence(data, 1)) {
// data.setPassive(order.getOrderName());
// player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP,
// SoundSource.PLAYERS,
// 1.0f, 1.0f);
// } else {
// player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT,
// SoundSource.PLAYERS,
// 1.0f, 1.0f);
// }

// this.open(player); // Refresh menu
// }));

// gui.setSlot(4, new GuiElementBuilder(makeSubIcon(Items.IRON_BOOTS, "utility",
// data.getUnlockedOrder(order.getOrderName()).hasUtility(), data.getUtility()
// == order))
// .setCallback((index, type, action) -> {
// if (deductInfluence(data, 1)) {
// data.setUtility(order.getOrderName());
// player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP,
// SoundSource.PLAYERS,
// 1.0f, 1.0f);
// } else {
// player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT,
// SoundSource.PLAYERS,
// 1.0f, 1.0f);
// }
// this.open(player);
// }));

// gui.setSlot(6, new GuiElementBuilder(makeSubIcon(Items.IRON_SWORD, "combat",
// data.getUnlockedOrder(order.getOrderName()).hasCombat(), data.getCombat() ==
// order))
// .setCallback((index, type, action) -> {
// if (deductInfluence(data, 1)) {
// data.setCombat(order.getOrderName());
// player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP,
// SoundSource.PLAYERS,
// 1.0f, 1.0f);
// } else {
// player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT,
// SoundSource.PLAYERS,
// 1.0f, 1.0f);
// }
// this.open(player);
// }));

// gui.setSlot(8, new GuiElementBuilder(makeBackButton())
// .setCallback((index, type, action) -> {
// new AscensionMenu().open(player);
// }));
// }

// private boolean deductInfluence(AscensionData data, int amount) {
// if (data.getInfluence() < amount)
// return false;
// data.addInfluence(-amount);
// return true;
// }

// public void updateGod(AscensionData data, SimpleGui gui, ServerPlayer player)
// {
// updateDemigod(data, gui, player);

// gui.setSlot(9, new GuiElementBuilder(Items.PURPLE_STAINED_GLASS_PANE));
// for (int i = 10; i < 17; i++) {
// if (i != 13)
// gui.setSlot(i, new GuiElementBuilder(Items.YELLOW_STAINED_GLASS_PANE));
// }
// gui.setSlot(17, new GuiElementBuilder(Items.PURPLE_STAINED_GLASS_PANE));

// gui.setSlot(13, new GuiElementBuilder(makePromotionIcon(data.getGodOrder() ==
// order.getOrderName()))
// .setCallback((index, type, action) -> {
// if (deductInfluence(data, 1)) {
// data.setGodOrder(order.getOrderName());
// player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
// 1.0f, 1.0f);
// } else {
// player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT,
// SoundSource.PLAYERS,
// 1.0f, 1.0f);
// }
// this.open(player);
// }));
// }

// private ItemStack makePromotionIcon(boolean currentlyGod) {
// ItemStack icon = order.getOrderItem().copy();
// icon.set(DataComponents.ITEM_NAME, Component.literal(
// Utils.smallCaps(currentlyGod ? "Ascend!" : "Ascended"))
// .withStyle(style -> style
// .withColor(currentlyGod
// ? TextColor.fromRgb(ChatFormatting.GOLD.getColor())
// : order.getOrderColor())
// .withBold(currentlyGod)
// .withItalic(false)));

// List<Component> lore = new ArrayList<>(List.of(
// Component
// .literal((currentlyGod
// ? "You are the god of " + order.getOrderName() + "!"
// : "Click to become the god of ") + order.getOrderName()
// + "!")
// .withStyle(style -> style
// .withColor(ChatFormatting.GOLD)
// .withShadowColor(ChatFormatting.WHITE.getColor()))));
// if (!currentlyGod)
// lore.add(Utils.costComponent(1));

// icon.set(DataComponents.LORE, new ItemLore(lore));
// return icon;
// }

// private ItemStack makeSubIcon(Item item, String type, boolean unlocked,
// boolean equipped) {
// ItemStack icon = new ItemStack(item);
// icon.set(DataComponents.ITEM_NAME, Component.literal(
// Utils.smallCaps(type))
// .withStyle(style -> style
// .withColor(unlocked ? ChatFormatting.GREEN : ChatFormatting.RED)
// .withBold(equipped)
// .withItalic(false)));

// List<Component> lore = new
// ArrayList<>(Utils.wrapToComponents(order.getDescription(type)));
// lore.add(Component
// .literal(equipped ? "Currently equipped!"
// : (unlocked ? "Click to equip!"
// : "Click to unlock (costs 1 influence)"))
// .withStyle(ChatFormatting.GRAY));

// icon.set(DataComponents.LORE, new ItemLore(lore));
// return icon;
// }

// private static ItemStack makeBackButton() {
// ItemStack backButton = new ItemStack(Items.ARROW);
// backButton.set(DataComponents.ITEM_NAME,
// Component.literal("back").withStyle(ChatFormatting.YELLOW));
// return backButton;
// }
// }
