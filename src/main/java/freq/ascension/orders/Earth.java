package freq.ascension.orders;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import freq.ascension.Utils;
import freq.ascension.managers.*;
import freq.ascension.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class Earth implements Order {

    public static final Earth INSTANCE = new Earth();
    private static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTlhNWExZTY5YjRmODEwNTYyNTc1MmJjZWUyNTM0MDY2NGIwODlmYTFiMmY1MjdmYTkxNDNkOTA2NmE3YWFkMiJ9fX0=";

    // Keep track of supermine state per player
    private static final Map<UUID, Boolean> SUPERMINE_ENABLED = new ConcurrentHashMap<>();
    private static final Set<UUID> IS_MINING_INTERNALLY = new HashSet<>();

    @Override
    public Order getVersion(String rank) {
        if (rank == "god") {
            return EarthGod.INSTANCE;
        }
        return this;
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Haste 1, Ore drops doubled, automatically smelted without silk touch, anvils cost 50% less";
            case "utility" -> "Supermine spell, 2x2";
            case "combat" -> "Magma bubble spell";
            default -> "";
        };
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("supermine", this, "utility",
                (player, stats) -> {
                    SpellRegistry.toggleSupermine(player);
                }));

        SpellCooldownManager.register(new Spell("magma_bubble", this, "combat",
                (player, stats) -> {
                    SpellRegistry.magmaBubble(player,
                            stats.getInt(0), // radius
                            stats.getInt(1), // damage percent
                            stats.getBool(2) // launch
                    );
                }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "supermine" -> new SpellStats(60,
                    "Activate to toggle 2x2 mining.",
                    2, 4); // diameter, max durability loss
            case "magma_bubble" -> new SpellStats(600,
                    "Scorches enemy with magma spikes in a 4x4 centered area, dealing 3 hearts. Must be activated on land or in lava.",
                    4, 30, false);
            default -> null;
        };
    }

    // For Ores
    private static final TagKey<Block> ORE_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "ores"));

    // For Tools (Pickaxes/Shovels)
    private static final TagKey<Item> PICKAXES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("minecraft", "pickaxes"));
    private static final TagKey<Item> SHOVELS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("minecraft", "shovels"));

    private boolean isSupermineTool(ItemStack tool) {
        return tool != null && (tool.is(PICKAXES) || tool.is(SHOVELS));
    }

    private ItemStack getSmeltedResult(ServerLevel level, BlockState state) {
        // 1. Get the item representation of the block
        ItemStack input = new ItemStack(state.getBlock().asItem());

        // 2. Create a "wrapper" for the recipe search
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);

        // 3. Search for a Smelting Recipe
        return level.recipeAccess()
                .getRecipeFor(RecipeType.SMELTING, recipeInput, level)
                .map(recipe -> recipe.value().assemble(recipeInput, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    public String getOrderName() {
        return "earth";
    }

    public TextColor getOrderColor() {
        // Orange
        return TextColor.fromRgb(0xff9d00);
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.HASTE, 60, 0));
    }

    public static void toggleSupermine(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean current = SUPERMINE_ENABLED.getOrDefault(uuid, false);
        SUPERMINE_ENABLED.put(uuid, !current);

        String status = !current ? "enabled" : "disabled";
        player.sendSystemMessage(Component.literal("§a[Earth] §7Supermine " + status + "."));
        player.level().playSound(player, player.blockPosition(),
                !current ? SoundEvents.ANVIL_USE : SoundEvents.ANVIL_LAND,
                SoundSource.UI, 1.0f, 1.0f);
    }

    @Override
    public void onBlockBreak(ServerPlayer player, ServerLevel world, BlockPos pos, BlockState state,
            BlockEntity entity) {

        UUID uuid = player.getUUID();
        if (IS_MINING_INTERNALLY.contains(uuid))
            return;

        ItemStack tool = player.getMainHandItem();
        int silkLevel = EnchantmentHelper.getItemEnchantmentLevel(
                player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),
                tool);
        boolean hasSilkTouch = silkLevel > 0;

        try {
            // Allow autosmelt to work with supermine if enabled
            boolean supermine = SUPERMINE_ENABLED.getOrDefault(uuid, false);
            if (supermine && !hasSilkTouch && isSupermineTool(tool)) {
                SpellStats stats = getSpellStats("supermine");
                IS_MINING_INTERNALLY.add(uuid);
                breakSurroundingCube(player, pos, stats.getInt(0), stats.getInt(1));
            }
        } catch (Throwable ignored) {
        } finally {
            IS_MINING_INTERNALLY.remove(uuid);
        }

        // If using Silk Touch, do not smelt (let vanilla drops happen)
        if (hasSilkTouch)
            return;

        // Ensure player is using a valid tool (pick/shovel)
        if (!isSupermineTool(tool))
            return;

        // Determine the smelted result to check if we should intervene
        ItemStack smelted = getSmeltedResult(world, state);
        if (smelted.isEmpty())
            return;

        // Spawn small fire particles around the broken block
        world.sendParticles(
                net.minecraft.core.particles.ParticleTypes.FLAME,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                10, 0.2, 0.2, 0.2, 0.01);

        double xpMultiplier = Utils.isGod(player) ? 2.0 : 1.0;

        // This handles the doubling (count * 2) and XP dropping
        dropSmeltedOre(world, pos, state, xpMultiplier);
    }

    // ????
    @Override
    public void onAnvilPrepare(AnvilMenu menu) {
        int originalCost = menu.getCost();
        if (originalCost <= 0)
            return;

        int reducedCost = (int) Math.floor(originalCost * 0.5);
        try {
            var costField = AnvilMenu.class.getDeclaredField("cost");
            costField.setAccessible(true);
            costField.setInt(menu, Math.max(1, reducedCost));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void breakSurroundingCube(ServerPlayer player, BlockPos origin, int diameter, int maxDurabilityLoss) {
        if (origin == null)
            return;

        ServerLevel world = player.level();
        if (world == null)
            return;

        int baseX = origin.getX();
        int baseY = origin.getY();
        int baseZ = origin.getZ();

        int durabilityUsed = 0;

        int d = Math.max(1, diameter);
        int half = d / 2;

        // Odd diameter (e.g., 3): -1..1
        // Even diameter (e.g., 4): -2..1 (still breaks exactly 4 blocks per axis)
        int min = -half;
        int max = (d % 2 == 0) ? (half - 1) : half;

        for (int dx = min; dx <= max; dx++) {
            for (int dy = min; dy <= max; dy++) {
                for (int dz = min; dz <= max; dz++) {
                    if (!isSupermineTool(player.getInventory().getSelectedItem()))
                        return;

                    BlockPos targetPos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    BlockState targetState = world.getBlockState(targetPos);

                    if (!Utils.isBreakable(targetState, world, targetPos))
                        continue;

                    if (targetState.is(ORE_TAG)) {
                        dropSmeltedOre(world, targetPos, targetState, 1.0);
                    } else {
                        // Break the block and spawn drops
                        world.destroyBlock(targetPos, true, player);
                    }

                    if (durabilityUsed < maxDurabilityLoss) {
                        ItemStack tool = player.getInventory().getSelectedItem();
                        tool.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                        if (tool.isEmpty())
                            return;
                        durabilityUsed++;
                    }
                }
            }
        }
    }

    private boolean dropSmeltedOre(ServerLevel world, BlockPos pos, BlockState state, double xpMultiplier) {
        ItemStack smelted = getSmeltedResult(world, state);
        if (smelted.isEmpty())
            return false;

        smelted.setCount(smelted.getCount() * 2);
        Block.popResource(world, pos, smelted);

        final int xp = Math.max(1, (int) Math.round(getOreXP(state) * xpMultiplier));
        ExperienceOrb orb = new ExperienceOrb(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, xp);
        world.addFreshEntity(orb);

        world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    private int getOreXP(BlockState state) {
        // Define XP values for different ore types
        Block block = state.getBlock();
        if (block == net.minecraft.world.level.block.Blocks.COAL_ORE ||
                block == net.minecraft.world.level.block.Blocks.DEEPSLATE_COAL_ORE)
            return 2;
        if (block == net.minecraft.world.level.block.Blocks.DIAMOND_ORE ||
                block == net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE)
            return 5;
        if (block == net.minecraft.world.level.block.Blocks.EMERALD_ORE ||
                block == net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE)
            return 5;
        if (block == net.minecraft.world.level.block.Blocks.LAPIS_ORE ||
                block == net.minecraft.world.level.block.Blocks.DEEPSLATE_LAPIS_ORE)
            return 3;
        if (block == net.minecraft.world.level.block.Blocks.REDSTONE_ORE ||
                block == net.minecraft.world.level.block.Blocks.DEEPSLATE_REDSTONE_ORE)
            return 3;
        if (block == net.minecraft.world.level.block.Blocks.NETHER_QUARTZ_ORE)
            return 3;
        if (block == net.minecraft.world.level.block.Blocks.NETHER_GOLD_ORE)
            return 1;
        return 1;
    }

    @Override
    public ItemStack getOrderItem() {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        Utils.applyTexture(head, HEAD_TEXTURE);
        return head;
    }
}
