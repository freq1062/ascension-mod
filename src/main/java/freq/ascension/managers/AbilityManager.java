package freq.ascension.managers;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;


import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.api.DelayedTask;
import freq.ascension.orders.End;
import freq.ascension.orders.Order;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.MythicWeapon;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;

public class AbilityManager {

    private static final ThreadLocal<Boolean> skipModification = ThreadLocal.withInitial(() -> false);

    public static void skipNextModification() {
        skipModification.set(true);
    }

    public static boolean shouldSkipModification() {
        boolean skip = skipModification.get();
        if (skip) {
            skipModification.set(false);
        }
        return skip;
    }

    public static void broadcast(ServerPlayer player, Consumer<Order> action) {
        AscensionData data = (AscensionData) player;
        // Ensure the same order isn't invoked multiple times if equipped in
        // multiple slots (passive/utility/combat). Deduplicate by order name.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Order order : data.getEquippedOrders()) {
            if (order == null)
                continue;
            String name = order.getOrderName();
            if (!seen.add(name))
                continue; // already invoked this order
            action.accept(order);
        }
    }

    public static boolean anyMatch(ServerPlayer player, Predicate<Order> predicate) {
        AscensionData data = (AscensionData) player;
        for (Order order : data.getEquippedOrders()) {
            if (order == null)
                continue; // empty equipment slot — mirrors the null-guard in broadcast()
            if (predicate.test(order)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Invokes an action on the player's active mythical weapon if one is registered for their
     * god order and they currently hold a matching item in any inventory slot.
     *
     * <p>No-op if the player is not a god, has no weapon registered for their order, or does
     * not have the weapon in their inventory.
     */
    public static void broadcastWeapon(ServerPlayer player, Consumer<MythicWeapon> action) {
        AscensionData data = (AscensionData) player;
        if (!"god".equals(data.getRank())) return;

        String godOrder = data.getGodOrder();
        if (godOrder == null) return;

        MythicWeapon weapon = WeaponRegistry.getForOrder(godOrder);
        if (weapon == null) return;

        // Only invoke if the player actually carries the weapon
        if (!WeaponRegistry.hasWeapon(player)) return;

        action.accept(weapon);
    }

    /**
     * Returns the {@link MythicWeapon} currently associated with the god player, or {@code null}
     * if the player is not a god, has no weapon registered, or does not carry it.
     */
    public static MythicWeapon getActiveWeapon(ServerPlayer player) {
        AscensionData data = (AscensionData) player;
        if (!"god".equals(data.getRank())) return null;
        String godOrder = data.getGodOrder();
        if (godOrder == null) return null;
        MythicWeapon weapon = WeaponRegistry.getForOrder(godOrder);
        if (weapon == null || !WeaponRegistry.hasWeapon(player)) return null;
        return weapon;
    }

    public static void onItemEaten(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        broadcast(player, (order) -> order.onItemEaten(player, stack));
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

        // Auto Refreshed Effects (2s) — also triggers weapon onHold hook
        Ascension.scheduler.schedule(new ContinuousTask(40, () -> {
            for (ServerPlayer player : Ascension.getServer().getPlayerList().getPlayers()) {
                broadcast(player, (order) -> order.applyEffect(player));
                broadcastWeapon(player, (weapon) -> weapon.onHold(player));
            }
        }));

        // Ender pearl cooldown reduction: End passive halves the 20-tick vanilla cooldown
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer sp) {
                if (sp.getItemInHand(hand).is(Items.ENDER_PEARL)
                        && anyMatch(sp, o -> o instanceof End && o.hasCapability(sp, "passive"))) {
                    // Schedule 1 tick after use so vanilla's addCooldown(20) has already run
                    Ascension.scheduler.schedule(new DelayedTask(1, () -> {
                        // In 1.21.10 addCooldown takes (ItemStack, int) or (ResourceLocation, int)
                        sp.getCooldowns().addCooldown(Items.ENDER_PEARL.getDefaultInstance(), 10);
                    }));
                }
            }
            return InteractionResult.PASS;
        });

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
                if (End.isAffectedByDesolation(serverPlayer) && "combat".equals(spell.getType())) {
                    serverPlayer.sendSystemMessage(Component.literal(
                            "§cYour combat abilities are disabled by Desolation of Time!"));
                    return InteractionResult.FAIL;
                }

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

        // God demotion on death — fires before ServerPlayerEvents.COPY_FROM so the demoted
        // data (rank="demigod", godOrder=null) is what gets copied to the respawn entity.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer sp && Ascension.getServer() != null) {
                GodManager gm = GodManager.get(Ascension.getServer());
                if (gm.isGod(sp)) {
                    // Notify weapon of impending death before demotion removes it
                    broadcastWeapon(sp, (weapon) -> weapon.onDeath(sp));

                    gm.demoteFromGod(sp, Ascension.getServer());
                    sp.sendSystemMessage(Component.literal(
                            "§cYou died and lost your god status."));
                }
            }
        });
    }
}
