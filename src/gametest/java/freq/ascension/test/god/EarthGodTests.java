package freq.ascension.test.god;

import freq.ascension.orders.Earth;
import freq.ascension.orders.EarthGod;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Integration tests for the Earth God order.
 */
public class EarthGodTests {

    private static void promoteToEarthGod(GameTestHelper helper, ServerPlayer player) {
        TestHelper.setGodDirect(helper, player, "earth");
    }

    private static int createRenameCost(GameTestHelper helper, ServerPlayer player, int repairCost) {
        AnvilMenu menu = new AnvilMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), player.blockPosition()));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(net.minecraft.core.component.DataComponents.REPAIR_COST, repairCost);
        menu.getSlot(0).set(sword);
        menu.setItemName("earth-god");
        menu.createResult();
        return menu.getCost();
    }

    private static ItemStack fortunePickaxe(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE),
                3);
        return pickaxe;
    }

    private static void fillCube(GameTestHelper helper, BlockPos origin, int radius, net.minecraft.world.level.block.state.BlockState state) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    helper.getLevel().setBlockAndUpdate(origin.offset(dx, dy, dz), state);
                }
            }
        }
    }

    private static int countAirInCube(GameTestHelper helper, BlockPos origin, int radius) {
        int air = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (helper.getLevel().getBlockState(origin.offset(dx, dy, dz)).isAir()) {
                        air++;
                    }
                }
            }
        }
        return air;
    }

    private static int countItem(GameTestHelper helper, BlockPos origin, net.minecraft.world.item.Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(2.0)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static int totalXp(GameTestHelper helper, BlockPos origin) {
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(origin).inflate(2.0)).stream()
                .mapToInt(ExperienceOrb::getValue)
                .sum();
    }

    @GameTest(maxTicks = 60)
    public void hasteIIAppliedOnEarthGodPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, player);

        helper.runAfterDelay(41, () -> {
            var haste = player.getEffect(MobEffects.HASTE);
            if (haste != null && haste.getAmplifier() == 1) {
                helper.succeed();
            } else {
                helper.fail("Expected Haste II from earth god passive");
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void hasteIIRefreshesAfter40Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, player);

        helper.runAfterDelay(85, () -> {
            var haste = player.getEffect(MobEffects.HASTE);
            if (haste != null && haste.getAmplifier() == 1) {
                helper.succeed();
            } else {
                helper.fail("Earth god haste was not refreshed");
            }
        });
    }

    @GameTest
    public void anvilCostReducedTo10PctForGod(GameTestHelper helper) {
        ServerPlayer baseline = helper.makeMockServerPlayerInLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, player);

        helper.runAfterDelay(2, () -> {
            int baselineCost = createRenameCost(helper, baseline, 9);
            int godCost = createRenameCost(helper, player, 9);
            if (baselineCost != 10) {
                helper.fail("Expected baseline anvil cost 10, got " + baselineCost);
            } else if (godCost == 1) {
                helper.succeed();
            } else {
                helper.fail("Expected earth god anvil cost 1, got " + godCost);
            }
        });
    }

    @GameTest
    public void anvilCostLowerThanDemigod(GameTestHelper helper) {
        ServerPlayer godPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer demigodPlayer = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, godPlayer);
        TestHelper.equip(helper, demigodPlayer, "passive", "earth");

        helper.runAfterDelay(2, () -> {
            int godCost = createRenameCost(helper, godPlayer, 9);
            int demigodCost = createRenameCost(helper, demigodPlayer, 9);
            if (godCost < demigodCost) {
                helper.succeed();
            } else {
                helper.fail("Expected earth god anvil cost < demigod cost, got " + godCost + " vs " + demigodCost);
            }
        });
    }

    @GameTest
    public void supermine3x3AreaForGod(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, player);

        helper.runAfterDelay(2, () -> {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            TestHelper.bind(helper, player, 1, "supermine");
            TestHelper.activateSpell(helper, player);

            BlockPos origin = player.blockPosition().offset(4, 0, 0);
            fillCube(helper, origin, 1, Blocks.STONE.defaultBlockState());
            EarthGod.INSTANCE.onBlockBreak(player, helper.getLevel(), origin,
                    helper.getLevel().getBlockState(origin), helper.getLevel().getBlockEntity(origin));

            helper.runAfterDelay(1, () -> {
                int airBlocks = countAirInCube(helper, origin, 1);
                if (airBlocks == 27) {
                    helper.succeed();
                } else {
                    helper.fail("Expected earth god supermine to clear 27 blocks, got " + airBlocks);
                }
            });
        });
    }

    @GameTest
    public void supermine3x3LargerThanDemigod2x2(GameTestHelper helper) {
        ServerPlayer godPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer demigodPlayer = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, godPlayer);
        TestHelper.equip(helper, demigodPlayer, "utility", "earth");

        helper.runAfterDelay(2, () -> {
            godPlayer.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            demigodPlayer.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            TestHelper.bind(helper, godPlayer, 1, "supermine");
            TestHelper.bind(helper, demigodPlayer, 1, "supermine");
            TestHelper.activateSpell(helper, godPlayer);
            TestHelper.activateSpell(helper, demigodPlayer);

            BlockPos godOrigin = godPlayer.blockPosition().offset(4, 0, 0);
            BlockPos demigodOrigin = demigodPlayer.blockPosition().offset(-4, 0, 0);
            fillCube(helper, godOrigin, 1, Blocks.STONE.defaultBlockState());
            fillCube(helper, demigodOrigin, 1, Blocks.STONE.defaultBlockState());

            EarthGod.INSTANCE.onBlockBreak(godPlayer, helper.getLevel(), godOrigin,
                    helper.getLevel().getBlockState(godOrigin), helper.getLevel().getBlockEntity(godOrigin));
            Earth.INSTANCE.onBlockBreak(demigodPlayer, helper.getLevel(), demigodOrigin,
                    helper.getLevel().getBlockState(demigodOrigin), helper.getLevel().getBlockEntity(demigodOrigin));

            helper.runAfterDelay(1, () -> {
                int godAir = countAirInCube(helper, godOrigin, 1);
                int demigodAir = countAirInCube(helper, demigodOrigin, 1);
                if (godAir > demigodAir) {
                    helper.succeed();
                } else {
                    helper.fail("Expected earth god supermine to break more blocks than demigod, got "
                            + godAir + " vs " + demigodAir);
                }
            });
        });
    }

    @GameTest
    public void fortuneBeforeDoublingYieldsMoreDropsThanDemigod(GameTestHelper helper) {
        ServerPlayer godPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer demigodPlayer = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, godPlayer);
        TestHelper.equip(helper, demigodPlayer, "passive", "earth");

        helper.runAfterDelay(2, () -> {
            godPlayer.getInventory().setItem(0, fortunePickaxe(helper));
            demigodPlayer.getInventory().setItem(0, fortunePickaxe(helper));

            BlockPos godOrigin = godPlayer.blockPosition().offset(4, 0, 0);
            BlockPos demigodOrigin = demigodPlayer.blockPosition().offset(-4, 0, 0);
            helper.getLevel().setBlockAndUpdate(godOrigin, Blocks.COAL_ORE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(demigodOrigin, Blocks.COAL_ORE.defaultBlockState());

            EarthGod.INSTANCE.onBlockBreak(godPlayer, helper.getLevel(), godOrigin,
                    helper.getLevel().getBlockState(godOrigin), helper.getLevel().getBlockEntity(godOrigin));
            Earth.INSTANCE.onBlockBreak(demigodPlayer, helper.getLevel(), demigodOrigin,
                    helper.getLevel().getBlockState(demigodOrigin), helper.getLevel().getBlockEntity(demigodOrigin));

            helper.runAfterDelay(1, () -> {
                int godXp = totalXp(helper, godOrigin);
                int demigodXp = totalXp(helper, demigodOrigin);
                if (godXp > demigodXp) {
                    helper.succeed();
                } else {
                    helper.fail("Expected earth god ore break to yield more XP than demigod, got "
                            + godXp + " vs " + demigodXp);
                }
            });
        });
    }

    @GameTest
    public void ancientDebrisNotFortuneBonusedForGod(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, player);

        helper.runAfterDelay(2, () -> {
            player.getInventory().setItem(0, fortunePickaxe(helper));
            BlockPos origin = player.blockPosition().offset(4, 0, 0);
            helper.getLevel().setBlockAndUpdate(origin, Blocks.ANCIENT_DEBRIS.defaultBlockState());

            EarthGod.INSTANCE.onBlockBreak(player, helper.getLevel(), origin,
                    helper.getLevel().getBlockState(origin), helper.getLevel().getBlockEntity(origin));

            helper.runAfterDelay(1, () -> {
                int scraps = countItem(helper, origin, Items.NETHERITE_SCRAP);
                if (scraps == 2) {
                    helper.succeed();
                } else {
                    helper.fail("Expected ancient debris to drop exactly 2 netherite scraps, got " + scraps);
                }
            });
        });
    }

    @GameTest
    public void magmaBubbleLaunchesPlayersForGod(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToEarthGod(helper, player);

        helper.runAfterDelay(2, () -> {
            TestHelper.bind(helper, player, 1, "magma_bubble");
            TestHelper.activateSpell(helper, player);
            helper.runAfterDelay(2, () -> {
                if (player.getDeltaMovement().y >= 1.0) {
                    helper.succeed();
                } else {
                    helper.fail("Expected earth god magma bubble to launch the caster upward");
                }
            });
        });
    }
}
