package freq.ascension.orders;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class FloraGod extends Flora {
    public static final FloraGod INSTANCE = new FloraGod();

    private FloraGod() {
        super();
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        super.applyEffect(player); // Inherited regen
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Permanent regeneration 1. Immunity to negative potion effects. Golden apples give 4 absorption hearts instead of 2."
                        + CONFIG_GROUP.get("saturation_additive_percent")
                        + "% more saturation from food. Bees and creakings are passive. Crops cannot be trampled.";
            case "utility" -> {
                int ds = CONFIG_GROUP.get("camouflage_range");
                int ma = CONFIG_GROUP.get("camouflage_mob_aggro_dist_reduction_percent");
                yield "While being within " + ds
                        + " blocks of a plant block or having one in your inventory: Sculk sensors and shriekers ignore you, mob aggro distance reduced by "
                        + ma + " blocks, Creepers are neutral.";
            }
            default -> "";
        };
    }

    @Override
    public boolean isIgnoredBy(ServerPlayer player, Mob mob) {
        if (mob instanceof Bee && hasCapability(player, "passive"))
            return true;
        if (mob instanceof Creaking && hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        // Creepers neutral when utility is equipped and player is near a plant (or has
        // plant in inventory for gods)
        if (mob instanceof Creeper && hasCapability(player, "utility")
                && (isNearPlant(player)
                        || hasPlantBlockInInventory(player)))
            return true;
        return false;
    }

    @Override
    public double reduceFollowRangeMultiplier(ServerPlayer player) {
        if (hasCapability(player, "utility") && (isNearPlant(player) || hasPlantBlockInInventory(player))) {
            return 1 - (CONFIG_GROUP.get("flora.camouflage_mob_aggro_dist_reduction_percent") / 100);
        }
        return 1.0;
    }

    @Override
    public void onItemEaten(ServerPlayer player, ItemStack stack) {
        if (!hasCapability(player, "passive"))
            return;
        if (!stack.is(Items.GOLDEN_APPLE) && !stack.is(Items.ENCHANTED_GOLDEN_APPLE))
            return;

        // +2 extra absorption hearts (4 half-hearts = 2 full hearts)
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1, false, true, true));
    }

    /** Returns true if the player has any plant-type block in their inventory. */
    static boolean hasPlantBlockInInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isPlantItem(stack))
                return true;
        }
        return false;
    }
}
