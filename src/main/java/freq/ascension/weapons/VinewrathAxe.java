package freq.ascension.weapons;

import freq.ascension.Ascension;
import freq.ascension.Utils;
import freq.ascension.animation.Thorns;
import freq.ascension.api.DelayedTask;
import freq.ascension.orders.Flora;
import freq.ascension.orders.Order;
import net.minecraft.core.registries.Registries;
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

        return stack;
    }

    /**
     * Shield-disable ability: triggers when a blocking ServerPlayer is hit.
     *
     * <p>Axes in vanilla always disable a blocking player's shield, applying a 100-tick cooldown.
     * We schedule a 1-tick-delayed task so vanilla processes the disable first, then override the
     * cooldown and layer on vine freeze + spell damage.
     */
    @Override
    public void onAttack(ServerPlayer attacker, LivingEntity victim, Order.DamageContext ctx) {
        if (!(victim instanceof ServerPlayer victimPlayer)) return;
        if (!victimPlayer.isBlocking()) return;

        Ascension.scheduler.schedule(new DelayedTask(1, () -> {
            if (!victimPlayer.isAlive()) return;

            // Extend shield disable from vanilla 100 ticks to 200 ticks (10 seconds).
            victimPlayer.getCooldowns().addCooldown(Items.SHIELD.getDefaultInstance(), 200);

            // Vine freeze: spawn thorns animation then apply Slowness 255 for 60 ticks.
            Thorns.spawnThorns(attacker, victimPlayer, 6, 60);
            victimPlayer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 255, false, false));
            victimPlayer.setDeltaMovement(0, 0, 0);

            // Remove the slowness when the freeze ends (mirrors executeThorns cleanup).
            Ascension.scheduler.schedule(new DelayedTask(60, () ->
                    victimPlayer.removeEffect(MobEffects.SLOWNESS)));

            // 15% max HP spell damage.
            Utils.spellDmg(victimPlayer, attacker, 15.0f);
        }));
    }
}
