package freq.ascension.managers;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import com.ascension.managers.DivineDataManager;
import com.ascension.managers.Spell;
import com.ascension.managers.SpellCooldownManager;
import com.ascension.managers.WeaponRegistry;
import com.ascension.orders.Ocean;
import com.ascension.weapons.MythicWeapon;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public class AbilityManager {
    private final DivineDataManager data = DivineDataManager.getInstance();

    private List<Order> getPlayerOrders(Player player) {
        if (player == null || data == null)
            return List.of();
        return data.getEquippedOrders(player);
    }

    public static void broadcast(ServerPlayer player, Consumer<Order> action) {
        for (Order order : getPlayerOrders(player)) {
            action.accept(order);
        }
    }

    // EVENTS THAT DO NOT REQUIRE MIXINS
    public static void init() {
        // Block Break Events
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel)
                broadcast(serverPlayer, (order) -> order.onBlockBreak(serverLevel, pos, state, entity));
            return true; // Return true to allow the break
        });

        // Block Damage Events
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel) {
                AbilityManager.broadcast(serverPlayer,
                        (order) -> order.onBlockDamage(serverLevel, pos, player.getItemInHand(hand)));
            }
            return InteractionResult.PASS;
        });

        // Auto Refreshed Effects (2s)
        Ascension.scheduler.schedule(new ContinuousTask(40, () -> {
            for (ServerPlayer player : Ascension.getServer().getPlayerList().getPlayers()) {
                broadcast(player, (order) -> order.applyEffect(player));
            }
        }));
    }

    // Handle spell activations
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        // Only consider right-click actions for spell activation
        if (!rightClick)
            return;

        Player player = event.getPlayer();
        // require sneaking
        if (!player.isSneaking())
            return;

        int slot = player.getInventory().getHeldItemSlot();
        Map<Integer, String> bindings = data.getSpellBindings(player);
        String spellId = bindings.get(slot);
        if (spellId == null || spellId.isEmpty())
            return;
        Spell spell = SpellCooldownManager.get(spellId);
        if (spell == null)
            return; // unknown spell

        // Check if player is affected by Desolation of Time
        // if (End.isAffectedByDesolation(player) && "combat".equals(spell.getType())) {
        // player.sendMessage("§cYour combat abilities are disabled by Desolation of
        // Time!");
        // event.setCancelled(true);
        // return;
        // }

        // If on cooldown, cancel and inform
        if (SpellCooldownManager.isSpellOnCooldown(player, spell)) {
            return;
        }

        try {
            Order order = spell.getOrder();
            // Activate the spell (spell implementations should clear inUse when
            // appropriate)
            order.executeActiveSpell(spellId, player);
        } catch (Exception e) {
            player.sendMessage("Error activating spell: " + e.getMessage());
        }
        event.setCancelled(true);
    }

    // MYTHIC WEAPON ABILITITES

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        MythicWeapon w = WeaponRegistry.get(e.getItem());
        if (w == null)
            return;
        PluginMain.LOGGER.info("WEAPON: " + w.getModelData() + " | Action: " + e.getAction());

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
            w.onRightClick(e);
        else if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)
            w.onLeftClick(e);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            MythicWeapon w = WeaponRegistry.get(p.getInventory().getItemInMainHand());
            if (w != null)
                w.onHit(p, e.getEntity(), p.getInventory().getItemInMainHand(), e);
            return;
        }

        // Tempest trident special case
        if (e.getDamager() instanceof Trident trident && trident.getShooter() instanceof Player shooter) {
            ItemStack source = trident.getItemStack();
            MythicWeapon w = WeaponRegistry.get(source);
            if (w != null)
                w.onProjectileHit(shooter, trident, source, e);
        }
    }

    @EventHandler
    public void onTridentPickup(PlayerPickupArrowEvent event) {
        if (!(event.getArrow() instanceof Trident trident))
            return;
        if (WeaponRegistry.get(trident.getItemStack()) == null)
            return;
        DivineDataManager manager = DivineDataManager.getInstance();
        Player player = event.getPlayer();
        if (manager == null || player == null)
            return;

        // Allow the player who threw it to pick it up
        if (trident.getShooter() instanceof Player shooter && shooter.getUniqueId().equals(player.getUniqueId()))
            return;

        // Also allow Ocean gods to pick up any Tempest Trident
        if (manager.getRank(player) == DivineDataManager.Rank.GOD
                && manager.getGodOrder(player) == Ocean.INSTANCE.getOrderName())
            return;

        // Block pickup by other players
        event.setCancelled(true);
    }

    // MYTHIC WEAPON PROTECTIONS

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (player.isOp() || player.hasPermission("ascension.admin")) {
            return;
        }

        ItemStack item = event.getItemDrop().getItemStack();

        // Check if it's a mythic weapon
        if (WeaponRegistry.get(item) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot drop this mythic weapon!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (player.isOp() || player.hasPermission("ascension.admin")) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Check if player is trying to move a mythic weapon out of their inventory
        if (clickedItem != null && WeaponRegistry.get(clickedItem) != null) {
            // If clicking in player inventory and shift-clicking with another inventory
            // open, block it
            if (event.isShiftClick() && event.getClickedInventory() != null &&
                    event.getClickedInventory().getType() == InventoryType.PLAYER) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot move this mythic weapon out of your inventory!");
                return;
            }
        }

        // Check if player is trying to place a mythic weapon from cursor into another
        // inventory
        if (cursorItem != null && WeaponRegistry.get(cursorItem) != null) {
            if (event.getClickedInventory() != null &&
                    event.getClickedInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot move this mythic weapon out of your inventory!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (player.isOp() || player.hasPermission("ascension.admin")) {
            return;
        }

        ItemStack draggedItem = event.getOldCursor();

        // Check if dragging a mythic weapon
        if (draggedItem != null && WeaponRegistry.get(draggedItem) != null) {
            // Check if any of the dragged slots are outside player inventory
            for (int slot : event.getRawSlots()) {
                if (event.getView().getInventory(slot) != null &&
                        event.getView().getInventory(slot).getType() != InventoryType.PLAYER) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot move this mythic weapon out of your inventory!");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        // Prevent other entities from picking up dropped mythic weapons (just in case)
        if (!(event.getEntity() instanceof Player)) {
            ItemStack item = event.getItem().getItemStack();
            if (WeaponRegistry.get(item) != null) {
                event.setCancelled(true);
            }
        }
    }
}