package freq.ascension.managers;

import freq.ascension.Ascension;
import freq.ascension.items.InfluenceItem;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Date;
import java.util.Map;

public class InfluenceManager {
    private static final String GAIN_INFLUENCE_MSG = "§6✦Gained 1 influence!";
    private static final String LOSE_INFLUENCE_MSG = "§7Lost 1 influence.";
    private static final String NO_DROP_SUFFIX = " No items were dropped as you have no influence.";

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide())
                return InteractionResult.PASS;

            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = player.getItemInHand(hand);

                // Handle Influence Item usage
                if (InfluenceItem.isInfluenceItem(stack)) {
                    AscensionData data = (AscensionData) player;
                    data.addInfluence(1);
                    serverPlayer.sendSystemMessage(Component.literal(GAIN_INFLUENCE_MSG)
                            .withStyle(ChatFormatting.GOLD)
                            .withStyle(ChatFormatting.BOLD));
                    stack.shrink(1); // Consume the item
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            // Cast both players to your custom interface
            AscensionData oldData = (AscensionData) oldPlayer;
            AscensionData newData = (AscensionData) newPlayer;

            // Copy the values from the ghost of the old player to the new body
            newData.setInfluence(oldData.getInfluence());
            newData.setRank(oldData.getRank());
            newData.setGodOrder(oldData.getGodOrder());

            // Copy the Map (Spell Bindings)
            newData.getSpellBindings().putAll(oldData.getSpellBindings());

            // Copy equipped ability slots (passive/utility/combat)
            Order oldPassive = oldData.getPassive();
            Order oldUtility = oldData.getUtility();
            Order oldCombat = oldData.getCombat();
            newData.setPassive(oldPassive != null ? oldPassive.getOrderName() : null);
            newData.setUtility(oldUtility != null ? oldUtility.getOrderName() : null);
            newData.setCombat(oldCombat != null ? oldCombat.getOrderName() : null);

            // Copy previous god slots (used for demotion restore)
            newData.setPreviousPassive(oldData.getPreviousPassive());
            newData.setPreviousUtility(oldData.getPreviousUtility());
            newData.setPreviousCombat(oldData.getPreviousCombat());

            // Copy all unlocked orders progress
            Map<String, AscensionData.OrderUnlock> oldUnlocked = oldData.getUnlocked();
            if (oldUnlocked != null) {
                for (Map.Entry<String, AscensionData.OrderUnlock> entry : oldUnlocked.entrySet()) {
                    AscensionData.OrderUnlock u = entry.getValue();
                    String orderName = entry.getKey();
                    if (u.hasPassive())
                        newData.unlock(orderName, "passive");
                    if (u.hasUtility())
                        newData.unlock(orderName, "utility");
                    if (u.hasCombat())
                        newData.unlock(orderName, "combat");
                }
            }

            // Note: If 'alive' is false, it means they died.
            // (Influence deduction is handled in AFTER_DEATH)
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.level().isClientSide() && ent instanceof ServerPlayer victim) {

                Entity attacker = src.getEntity();
                AscensionData data = (AscensionData) victim;

                if (attacker == null || !(attacker instanceof ServerPlayer killer)) {
                    handleNaturalDeathInfluenceLoss(victim, data);
                } else {
                    // Killer gains influence directly
                    try {
                        AscensionData dataA = (AscensionData) killer;
                        dataA.addInfluence(1);
                        data.addInfluence(-1);
                        checkAndBanIfNeeded(victim, data);
                        victim.sendSystemMessage(
                                Component.literal(LOSE_INFLUENCE_MSG));
                        killer.sendSystemMessage(
                                Component.literal(GAIN_INFLUENCE_MSG));
                    } catch (Throwable ignored) {
                    }
                    // Shapeshift kill history (any living entity death by player)
                    AbilityManager.broadcast(killer, order -> order.onPlayerKill(killer, victim));
                }
            } else if (!ent.level().isClientSide() && ent instanceof LivingEntity victim) {
                // Non-player entity died: check if killer is ServerPlayer for Shapeshift
                // history
                Entity attacker = src.getEntity();
                if (attacker instanceof ServerPlayer killer) {
                    AbilityManager.broadcast(killer, order -> order.onPlayerKill(killer, victim));
                }
            }
        });
    }

    private static void checkAndBanIfNeeded(ServerPlayer victim, AscensionData data) {
        if (data.getInfluence() > -5) return;
        if (Ascension.isGameTestPlayer(victim)) return;
        net.minecraft.server.MinecraftServer server = Ascension.getServer();
        if (server == null) return;
        try {
            Date expires = new Date(System.currentTimeMillis() + (long) freq.ascension.Config.influenceBanDuration * 1000L);
            net.minecraft.server.players.NameAndId nameAndId = new net.minecraft.server.players.NameAndId(victim.getGameProfile());
            net.minecraft.server.players.UserBanListEntry entry = new net.minecraft.server.players.UserBanListEntry(
                    nameAndId,
                    null,
                    "Ascension",
                    expires,
                    "Your influence reached -5. You may rejoin after the ban expires.");
            server.getPlayerList().getBans().add(entry);
            victim.connection.disconnect(net.minecraft.network.chat.Component.literal(
                    "§cYou have been temporarily banned for reaching -5 influence."));
        } catch (Throwable ignored) {}
    }

    private static void handleNaturalDeathInfluenceLoss(ServerPlayer victim, AscensionData data) {
        boolean shouldDropInfluenceItem = data.getInfluence() > 0;
        data.addInfluence(-1);
        checkAndBanIfNeeded(victim, data);

        if (!shouldDropInfluenceItem) {
            victim.sendSystemMessage(Component.literal(LOSE_INFLUENCE_MSG + NO_DROP_SUFFIX));
            return;
        }

        victim.sendSystemMessage(Component.literal(LOSE_INFLUENCE_MSG));
        try {
            ItemStack itemToDrop = InfluenceItem.createItem();
            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                    victim.level(),
                    victim.getX(),
                    victim.getY(),
                    victim.getZ(),
                    itemToDrop);
            itemEntity.setDefaultPickUpDelay();
            victim.level().addFreshEntity(itemEntity);
        } catch (Throwable ignored) {
            // Item drop failure is non-fatal; influence was already deducted
        }
    }
}
