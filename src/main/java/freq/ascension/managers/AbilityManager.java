package freq.ascension.managers;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;

public class AbilityManager {
    public static void broadcast(ServerPlayer player, Consumer<Order> action) {
        AscensionData data = (AscensionData) player;
        for (Order order : data.getEquippedOrders()) {
            action.accept(order);
        }
    }

    public static boolean anyMatch(ServerPlayer player, Predicate<Order> predicate) {
        AscensionData data = (AscensionData) player;
        for (Order order : data.getEquippedOrders()) {
            if (predicate.test(order)) {
                return true;
            }
        }
        return false;
    }

    // EVENTS THAT DO NOT REQUIRE MIXINS
    public static void init() {
        // Block Break Events
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel)
                broadcast(serverPlayer, (order) -> order.onBlockBreak(serverPlayer, serverLevel, pos, state, entity));
            return true; // Return true to allow the break
        });

        // Block Damage Events
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel) {
                AbilityManager.broadcast(serverPlayer,
                        (order) -> order.onBlockDamage(serverPlayer, serverLevel, pos, player.getItemInHand(hand)));
            }
            return InteractionResult.PASS;
        });

        // Auto Refreshed Effects (2s)
        Ascension.scheduler.schedule(new ContinuousTask(40, () -> {
            for (ServerPlayer player : Ascension.getServer().getPlayerList().getPlayers()) {
                broadcast(player, (order) -> order.applyEffect(player));
            }
        }));

        // Spell activations
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                if (!player.isCrouching())
                    return InteractionResult.PASS;

                AscensionData data = (AscensionData) player;
                int slot = player.getInventory().getSelectedSlot();
                Map<Integer, String> bindings = data.getSpellBindings();
                String spellId = bindings.get(slot);

                if (spellId == null || spellId.isEmpty())
                    return InteractionResult.PASS;
                Spell spell = SpellCooldownManager.get(spellId);
                if (spell == null)
                    return InteractionResult.PASS; // unknown spell

                // Check if player is affected by Desolation of Time
                // if (End.isAffectedByDesolation(player) && "combat".equals(spell.getType())) {
                // player.sendMessage("§cYour combat abilities are disabled by Desolation of
                // Time!");
                // event.setCancelled(true);
                // return;
                // }

                // If on cooldown, cancel and inform
                if (SpellCooldownManager.isSpellOnCooldown(serverPlayer, spell)) {
                    return InteractionResult.PASS;
                }

                try {
                    Order order = spell.getOrder();
                    // Activate the spell (spell implementations should clear inUse when
                    // appropriate)
                    order.executeActiveSpell(spellId, serverPlayer);
                } catch (Exception e) {
                    serverPlayer.sendSystemMessage(Component.literal("Error activating spell: " + e.getMessage()));
                }

            }
            return InteractionResult.PASS;
        });
    }
}