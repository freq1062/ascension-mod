package freq.ascension.weapons;

import java.util.List;

import freq.ascension.Ascension;
import freq.ascension.Config;
import freq.ascension.Utils;
import freq.ascension.animation.Thorns;
import freq.ascension.api.ContinuousTask;
import freq.ascension.api.DelayedTask;
import freq.ascension.orders.Flora;
import freq.ascension.orders.Order;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The Vinewrath Axe — Flora order mythical weapon.
 *
 * <p>A retextured Diamond Axe enchanted with Sharpness V, Efficiency V, and Curse of Vanishing.
 *
 * <p><b>Active ability (onAttack):</b> When the Flora god strikes a blocking player, the hit
 * disables their shield (axes always break shields). One tick later this weapon:
 * <ol>
 *   <li>Extends the shield cooldown from the vanilla 100 ticks to 200 ticks (10 seconds).</li>
 *   <li>Applies vine freeze — Slowness 255 for 3 seconds (60 ticks) with the thorns
 *       animation — identical to the Flora Thorns spell freeze phase.</li>
 *   <li>Deals 15% of the victim's maximum health as spell damage.</li>
 * </ol>
 */
public class VinewrathAxe implements MythicWeapon {

    public static final VinewrathAxe INSTANCE = new VinewrathAxe();

    @Override
    public String getWeaponId() {
        return "vinewrath_axe";
    }

    @Override
    public Item getBaseItem() {
        return Items.DIAMOND_AXE;
    }

    @Override
    public Order getParentOrder() {
        return Flora.INSTANCE;
    }

    @Override
    public ItemStack createItem() {
        ItemStack stack = buildBaseItem();

        var enchReg = Ascension.getServer().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);

        stack.enchant(enchReg.getOrThrow(Enchantments.SHARPNESS), 5);
        stack.enchant(enchReg.getOrThrow(Enchantments.EFFICIENCY), 5);
        stack.enchant(enchReg.getOrThrow(Enchantments.VANISHING_CURSE), 1);

        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Hitting a blocking player extends their shield cooldown").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
            Component.literal("to 10 seconds and applies vine freeze (3s stun).").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
            Component.literal("All hits deal 15% of target's max HP as spell damage.").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY))
        )));

        return stack;
    }

    /**
     * Shield-disable ability: triggers on any ServerPlayer victim.
     *
     * <p>Axes in vanilla always disable a blocking player's shield, applying a 100-tick cooldown.
     * We capture the blocking state before a 1-tick delay (by tick+1, isBlocking() may have
     * already changed), then override the cooldown and layer vine freeze + spell damage.
     *
     * <p>Vine freeze (Slowness 255 for 60 ticks) is applied to blocking victims regardless of
     * armor enchantments.  Spell damage (15% max HP) applies to all victims.
     */
    @Override
    public void onAttack(ServerPlayer attacker, LivingEntity victim, Order.DamageContext ctx) {
        if (!(victim instanceof ServerPlayer victimPlayer)) return;
        // Capture blocking state NOW (before the 1-tick delay)
        boolean wasBlocking = victimPlayer.isBlocking();

        Ascension.scheduler.schedule(new DelayedTask(1, () -> {
            if (!victimPlayer.isAlive()) return;

            // Extend shield disable from vanilla 100 ticks to 200 ticks (10 seconds)
            // This applies whether or not they were blocking — the axe always disables shield
            victimPlayer.getCooldowns().addCooldown(Items.SHIELD.getDefaultInstance(), 200);

            if (wasBlocking) {
                // Vine freeze: Slowness 255 for stun ticks (configurable, default 3 seconds)
                Thorns.spawnThorns(attacker, victimPlayer, 6, Config.vinewrathAxeStunTicks);
                victimPlayer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Config.vinewrathAxeStunTicks, 255, false, false));
                victimPlayer.setDeltaMovement(0, 0, 0);
                victimPlayer.setNoGravity(true);

                // Zero out velocity every tick for the full stun duration so no knockback applies
                int[] stunTicks = {0};
                ContinuousTask[] stunTaskRef = {null};
                stunTaskRef[0] = new ContinuousTask(1, () -> {
                    stunTicks[0]++;
                    if (!victimPlayer.isAlive() || stunTicks[0] >= Config.vinewrathAxeStunTicks) {
                        stunTaskRef[0].stop();
                        return;
                    }
                    victimPlayer.setDeltaMovement(0, 0, 0);
                });
                Ascension.scheduler.schedule(stunTaskRef[0]);

                Ascension.scheduler.schedule(new DelayedTask(Config.vinewrathAxeStunTicks, () -> {
                    victimPlayer.removeEffect(MobEffects.SLOWNESS);
                    victimPlayer.setNoGravity(false);
                }));
            }

            // 15% max HP spell damage regardless of blocking
            Utils.spellDmg(victimPlayer, attacker, 15.0f);
        }));
    }
}
