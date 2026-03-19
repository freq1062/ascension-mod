package freq.ascension.orders;

import freq.ascension.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
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
                && (freq.ascension.managers.PlantProximityManager.isNearPlant(player)
                        || hasPlantBlockInInventory(player)))
            return true;
        return false;
    }

    @Override
    public boolean hasInventoryPlantEffect(ServerPlayer player) {
        return hasCapability(player, "utility") && hasPlantBlockInInventory(player);
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

    @Override
    public freq.ascension.managers.SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "thorns" -> new freq.ascension.managers.SpellStats(Config.floraGodThornsCD,
                    "Impale your opponents from the ground, freezing them for 4 seconds and giving them poison 1 for 10 seconds. Deals 25% max health. Pull-out damage: 15%.",
                    0);
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Permanent regeneration 1. Immunity to negative potion effects. 2x saturation from food. Golden apples give 4 absorption hearts instead of 2. Bees are passive. Crops cannot be trampled.";
            case "utility" ->
                "While carrying or being within 7 blocks of a plant block: Sculk sensors and shriekers ignore you, mob aggro distance reduced by 50%, Creepers are neutral.";
            case "combat" -> {
                freq.ascension.managers.SpellStats s = getSpellStats("thorns");
                yield "THORNS: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            default -> "";
        };
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

    private static boolean isPlantItem(ItemStack stack) {
        return stack.is(ItemTags.FLOWERS)
                || stack.is(ItemTags.SAPLINGS)
                || stack.is(ItemTags.LEAVES)
                || stack.is(Items.MOSS_BLOCK)
                || stack.is(Items.MOSS_CARPET)
                || stack.is(Items.AZALEA)
                || stack.is(Items.FLOWERING_AZALEA)
                || stack.is(Items.LILY_PAD)
                || stack.is(Items.VINE)
                || stack.is(Items.CACTUS)
                || stack.is(Items.SUGAR_CANE)
                || stack.is(Items.BAMBOO)
                || stack.is(Items.KELP)
                || stack.is(Items.SEAGRASS)
                || stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.NETHER_WART)
                || stack.is(Items.CRIMSON_FUNGUS)
                || stack.is(Items.WARPED_FUNGUS);
    }
}
