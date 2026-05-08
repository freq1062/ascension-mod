package freq.ascension.orders;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.api.DelayedTask;
import freq.ascension.config.ConfigGroup;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Flora implements Order {
    public static final Flora INSTANCE = new Flora();
    private static final Map<UUID, Boolean> NEAR_PLANTS = new ConcurrentHashMap<>();

    /*
     * Default configs
     */

    public static final ConfigGroup CONFIG_GROUP = new ConfigGroup("flora")
            .add("saturation_additive_percent", 50)
            .add("camouflage_range", 5)
            .add("camouflage_mob_aggro_dist_reduction_percent", 50)
            .add("thorns.cooldown_ticks", 600)
            .add("thorns.freeze_ticks", 60)
            .add("thorns.init_dmg_percent", 15)
            .add("thorns.pull_dmg_percent", 10)
            .add("thorns.poison_ticks", 200);

    /*
     * Metadata
     */

    public String getOrderName() {
        return "flora";
    }

    public TextColor getOrderColor() {
        // Green
        return TextColor.fromRgb(0x22bd0d);
    }

    @Override
    public String getOrderIcon() {
        return "\uE186";
    }

    /*
     * Stats, spells, descriptions
     */

    @Override
    public void init() {
        // Check every 5 ticks for more responsive plant proximity detection
        Ascension.scheduler.schedule(new ContinuousTask(5, () -> {
            for (ServerPlayer player : Ascension.getServer().getPlayerList().getPlayers()) {
                boolean nearPlant = isPlayerNearPlant(player);
                NEAR_PLANTS.put(player.getUUID(), nearPlant);
            }
        }));
    }

    @Override
    public Order getVersion(String rank) {
        if ("god".equals(rank)) {
            return FloraGod.INSTANCE;
        }
        return this;
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Permanent regeneration 1. Immunity to negative potion effects. "
                        + CONFIG_GROUP.get("saturation_additive_percent")
                        + "% more saturation from food. Bees are passive. Crops cannot be trampled.";
            case "utility" -> {
                int ds = CONFIG_GROUP.get("camouflage_range");
                int ma = CONFIG_GROUP.get("camouflage_mob_aggro_dist_reduction_percent");
                yield "While holding or being within " + ds
                        + " blocks of a plant block: Sculk sensors and shriekers ignore you, mob aggro distance reduced by "
                        + ma + " blocks, Creepers are neutral.";
            }
            default -> "";
        };
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("thorns", this, "combat", (player, stats) -> {
            SpellRegistry.thorns(player,
                    stats.getInt(0), // freeze ticks
                    stats.getInt(1), // initial damage percent
                    stats.getInt(2), // pull damage percent
                    stats.getInt(3) // poison duration ticks
            );
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "thorns" -> {
                int cd = CONFIG_GROUP.get("thorns.cooldown_ticks");
                int fs = CONFIG_GROUP.get("thorns.freeze_ticks") / 20;
                int dp = CONFIG_GROUP.get("thorns.init_dmg_percent");
                int pp = CONFIG_GROUP.get("thorns.pull_dmg_percent");
                int ps = CONFIG_GROUP.get("thorns.poison_ticks") / 20;
                yield new SpellStats(cd,
                        "Impale your opponents from the ground, locking them in place for " + fs
                                + "s and giving them poison 1 for " + ps + " s. Deals " + dp + "% max hp and an extra "
                                + pp + "% if the target leaves the stun early.",
                        0);
            }
            default -> null;
        };
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive")) {
            // ambient=true keeps HUD icon without particle spam; showIcon=true ensures it
            // shows
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, true, false, true));
        }
        // Cherry leaf particles for utility slot near/holding plant
        if (hasCapability(player, "utility") && isNearPlant(player)
                && player.level() instanceof ServerLevel sl) {
            for (int i = 0; i < 3; i++) {
                final int tickDelay = i * 14;
                Ascension.scheduler.schedule(new DelayedTask(tickDelay, () -> {
                    if (!player.isAlive() || !isNearPlant(player))
                        return;
                    sl.sendParticles(ParticleTypes.CHERRY_LEAVES,
                            player.getX() + (sl.getRandom().nextFloat() - 0.5f) * 1.5,
                            player.getY() + 1.5,
                            player.getZ() + (sl.getRandom().nextFloat() - 0.5f) * 1.5,
                            1, 0.3, 0.2, 0.3, 0.05);
                }));
            }
        }
    }

    @Override
    public MobEffectInstance onPotionEffect(ServerPlayer player, MobEffectInstance effectInstance) {
        if (!hasCapability(player, "passive") || effectInstance.getEffect().value().isBeneficial()
                || effectInstance.isInfiniteDuration()
                || effectInstance.isAmbient()
                || !effectInstance.isVisible())
            return effectInstance;

        player.level().sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                10,
                0.5,
                0.5,
                0.5,
                0.1);

        return new MobEffectInstance(
                effectInstance.getEffect(),
                0,
                effectInstance.getAmplifier(),
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon());
    }

    @Override
    public boolean isIgnoredBy(ServerPlayer player, Mob mob) {
        if (mob instanceof Bee && hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        if (mob instanceof Creeper && hasCapability(player, "utility")
                && isNearPlant(player))
            return true;
        return false;
    }

    @Override
    public boolean canTrampleCrops(ServerPlayer player) {
        return !hasCapability(player, "passive");
    }

    @Override
    public float modifySaturation(ServerPlayer player, float saturation) {
        if (hasCapability(player, "passive")) {
            player.level().sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    player.getX(),
                    player.getY() + 1.0,
                    player.getZ(),
                    10,
                    0.5,
                    0.5,
                    0.5,
                    0.1);
            return saturation * 1.5f;
        }
        return saturation;
    }

    @Override
    public double reduceFollowRangeMultiplier(ServerPlayer player) {
        if (hasCapability(player, "utility") && (isNearPlant(player) || isHoldingPlant(player))) {
            return 1 - (CONFIG_GROUP.get("flora.camouflage_mob_aggro_dist_reduction_percent") / 100);
        }
        return 1.0;
    }

    public static boolean isNearPlant(ServerPlayer player) {
        return NEAR_PLANTS.getOrDefault(player.getUUID(), false);
    }

    private static boolean isPlayerNearPlant(ServerPlayer player) {
        if (isHoldingPlant(player))
            return true;

        BlockPos playerPos = player.blockPosition();
        int radius = CONFIG_GROUP.get("flora.camouflage_range");

        // Check all blocks in the radius
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    Block block = player.level().getBlockState(checkPos).getBlock();

                    if (isPlantBlock(block, checkPos, player)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns true if the player is holding a plant item in mainhand or offhand.
     */
    public static boolean isHoldingPlant(ServerPlayer player) {
        return isPlantItem(player.getMainHandItem()) || isPlantItem(player.getOffhandItem());
    }

    /** Returns true if the given ItemStack is a plant-type item. */
    public static boolean isPlantItem(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        if (stack.is(ItemTags.FLOWERS) || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LEAVES))
            return true;
        if (stack.getItem() instanceof BlockItem bi) {
            BlockState blockstate = bi.getBlock().defaultBlockState();
            if (isPlantBlock(blockstate))
                return true;
        }
        return false;
    }

    private static boolean isPlantBlock(Block block, BlockPos pos, ServerPlayer player) {
        // Check if block is in the plants tag or is a known plant block
        if (player.level().getBlockState(pos).is(BlockTags.FLOWERS) ||
                player.level().getBlockState(pos).is(BlockTags.CROPS) ||
                player.level().getBlockState(pos).is(BlockTags.LEAVES) ||
                player.level().getBlockState(pos).is(BlockTags.SAPLINGS) ||
                player.level().getBlockState(pos).is(BlockTags.SMALL_FLOWERS)) {
            return true;
        }
        // Additional plant blocks not always in tags
        return block == Blocks.SHORT_GRASS || block == Blocks.CACTUS || block == Blocks.SUGAR_CANE
                || block == Blocks.KELP || block == Blocks.VINE;
    }

    private static boolean isPlantBlock(BlockState blockstate) {
        // Check if block is in the plants tag or is a known plant block
        if (blockstate.is(BlockTags.FLOWERS) ||
                blockstate.is(BlockTags.CROPS) ||
                blockstate.is(BlockTags.LEAVES) ||
                blockstate.is(BlockTags.SAPLINGS) ||
                blockstate.is(BlockTags.SMALL_FLOWERS)) {
            return true;
        }
        Block block = blockstate.getBlock();
        // Additional plant blocks not always in tags
        return block == Blocks.SHORT_GRASS || block == Blocks.CACTUS || block == Blocks.SUGAR_CANE
                || block == Blocks.KELP || block == Blocks.VINE;
    }
}