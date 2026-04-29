package freq.ascension.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.ThrownTrident;
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

    /**
     * Contract: all {@link Order} ability methods (e.g. {@code canWalkOnPowderSnow},
     * {@code isNeutralBy}, {@code isIgnoredBy}, {@code canTrampleCrops}) MUST guard
     * with {@code hasCapability(player, slot)} before returning {@code true}.
     * The {@link Order} interface defaults guarantee a safe no-op / {@code false}
     * baseline, but each concrete implementation is responsible for checking the
     * correct equipped slot ("passive", "utility", or "combat") so that abilities
     * are slot-gated rather than always-on.
     */
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
        AttackSnapshotManager.register();

        // Ocean powder snow: reset freeze ticks and fall distance each tick while
        // an Ocean passive player is crouching through (or hovering inside) powder snow.
        // This prevents freeze accumulation from entityInside running during slow sinking,
        // and resets fall distance so the player takes no fall damage when exiting the bottom.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (!anyMatch(sp, order -> order.canWalkOnPowderSnow(sp))) continue;
                if (sp.level().getBlockState(sp.blockPosition())
                        .is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                    sp.setTicksFrozen(0);
                    sp.resetFallDistance();
                }
            }
        });

        // Block Break Events
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel)
                broadcast(serverPlayer, (order) -> order.onBlockBreak(serverPlayer, serverLevel, pos, state, entity));
            return true; // Return true to allow the break
        });

        // Block Damage Events — also handles shift+left-click on blocks for weapon mode toggle
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel) {
                AbilityManager.broadcast(serverPlayer,
                        (order) -> order.onBlockDamage(serverPlayer, serverLevel, pos, player.getItemInHand(hand)));
                if (serverPlayer.isCrouching())
                    broadcastWeapon(serverPlayer, w -> w.onShiftLeftClick(serverPlayer));
            }
            return InteractionResult.PASS;
        });

        // Shift+left-click on entities (e.g. TempestTrident mode toggle, GravitonGauntlet)
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (player instanceof ServerPlayer sp && sp.isCrouching())
                broadcastWeapon(sp, w -> w.onShiftLeftClick(sp));
            return InteractionResult.PASS;
        });

        // Projectile hit callback — routes thrown-trident impacts into weapon.onProjectileHit.
        // No Fabric API event specifically targets thrown-trident hits, so ALLOW_DAMAGE is used
        // to detect any damage whose direct source is a ThrownTrident owned by a ServerPlayer.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.getDirectEntity() instanceof ThrownTrident
                    && source.getEntity() instanceof ServerPlayer player) {
                broadcastWeapon(player, w -> w.onProjectileHit(player, entity, null));
            }
            return true;
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
                        // EndGod gets ¼ cooldown (5 ticks); demigod End gets ½ (10 ticks)
                        AscensionData spData = (AscensionData) sp;
                        boolean isEndGod = "god".equals(spData.getRank()) && "end".equals(spData.getGodOrder());
                        int newCooldown = isEndGod ? 5 : 10;
                        sp.getCooldowns().addCooldown(Items.ENDER_PEARL.getDefaultInstance(), newCooldown);
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

                if (spellId == null || spellId.isEmpty()) {
                    // No spell bound — let the weapon handle shift+right-click
                    broadcastWeapon(serverPlayer, w -> w.onShiftUse(serverPlayer));
                    return InteractionResult.PASS;
                }
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

        // Clean up orphaned TempestTrident display entities from a previous session on player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID playerId = handler.getPlayer().getUUID();
            List<UUID> ownedTridents = new ArrayList<>();
            freq.ascension.weapons.TempestTrident.tridentToThrower.forEach((tid, uid) -> {
                if (uid.equals(playerId)) ownedTridents.add(tid);
            });
            ownedTridents.forEach(tid -> {
                freq.ascension.weapons.TempestTrident.tridentToThrower.remove(tid);
                freq.ascension.api.ContinuousTask task =
                        freq.ascension.weapons.TempestTrident.tridentTrackingTasks.remove(tid);
                if (task != null) task.stop();
                UUID displayId = freq.ascension.weapons.TempestTrident.tridentToDisplay.remove(tid);
                if (displayId != null) {
                    for (var level : server.getAllLevels()) {
                        var e = level.getEntity(displayId);
                        if (e != null && !e.isRemoved()) { e.discard(); break; }
                    }
                }
            });
        });

        // God demotion on death — fires before ServerPlayerEvents.COPY_FROM so the demoted
        // data (rank="demigod", godOrder=null) is what gets copied to the respawn entity.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer sp && Ascension.getServer() != null) {
                GodManager gm = GodManager.get(Ascension.getServer());
                AscensionData data = (AscensionData) sp;

                boolean isGodByManager = gm.isGod(sp);
                boolean isGodByData = "god".equals(data.getRank());

                if (isGodByManager || isGodByData) {
                    // Capture order name BEFORE demotion clears the entry
                    String orderName = gm.getGodOrderName(sp);
                    if (orderName == null) orderName = data.getGodOrder(); // data-mismatch fallback

                    // Notify weapon of impending death before demotion removes it
                    broadcastWeapon(sp, (weapon) -> weapon.onDeath(sp));

                    if (isGodByManager) {
                        gm.demoteFromGod(sp, Ascension.getServer());
                    } else {
                        // Manager entry is missing but AscensionData still says "god" — clear manually
                        data.setRank("demigod");
                        data.setGodOrder(null);
                        if (orderName != null) gm.clearGod(orderName);
                    }

                    sp.sendSystemMessage(Component.literal("§cYou died and lost your god status."));

                    if (orderName != null) {
                        Component broadcastMsg = Component.literal(
                                "§6[Ascension] §e" + sp.getName().getString()
                                + " §6has fallen from godhood as the God of §e"
                                + capitalize(orderName) + "§6!");
                        Ascension.getServer().getPlayerList()
                                .broadcastSystemMessage(broadcastMsg, false);
                    }
                }
            }
        });
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
