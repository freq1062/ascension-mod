package freq.ascension.test.misc;

import java.lang.reflect.Method;
import java.util.List;

import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.InfluenceManager;
import freq.ascension.menus.AscensionMenu;
import freq.ascension.registry.OrderRegistry;
import freq.ascension.registry.SpellRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.HappyGhast;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Tests general gameplay mechanics: influence item drops on death,
 * ascension menu display, and order-based speed comparisons.
 */
public class GameplayTests {

    /**
     * Applies the production natural-death loss path and asserts it drops one
     * influence item while deducting one influence.
     */
    @GameTest
    public void influenceItemDroppedOnNaturalDeath(GameTestHelper helper) {
        ServerPlayer player = createPlayer(helper, new BlockPos(1, 2, 1));
        AscensionData data = (AscensionData) player;
        data.setInfluence(3);
        BlockPos deathPos = player.blockPosition();

        invokeNaturalDeathLoss(player, data);
        if (data.getInfluence() == 2 && hasInfluenceDrop(helper, deathPos, 3.0)) {
            helper.succeed();
        } else {
            helper.fail("Expected a natural death with positive influence to drop an Influence item");
        }
    }

    /**
     * Gives player 0 influence; applies the natural-death loss path; asserts no influence item on ground.
     */
    @GameTest
    public void influenceItemNotDroppedWithZeroInfluence(GameTestHelper helper) {
        ServerPlayer player = createPlayer(helper, new BlockPos(1, 2, 1));
        AscensionData data = (AscensionData) player;
        data.setInfluence(0);
        BlockPos deathPos = player.blockPosition();

        invokeNaturalDeathLoss(player, data);
        if (data.getInfluence() == -1 && !hasInfluenceDrop(helper, deathPos, 3.0)) {
            helper.succeed();
        } else {
            helper.fail("Expected zero-influence natural death to drop no Influence item");
        }
    }

    /**
     * Uses /set passive ocean; opens ascension menu; asserts ocean entry is marked as selected/equipped.
     */
    @GameTest
    public void equippedOrderHighlightedInAscensionMenu(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AscensionData data = (AscensionData) player;
        data.unlock("ocean", "passive");
        data.setPassive("ocean");

        List<Component> pages = new AscensionMenu().createOrderPages(OrderRegistry.get("ocean"), data, 2);
        boolean highlighted = pages.get(0).toFlatList().stream().anyMatch(component ->
                component.getString().contains("Passive")
                        && component.getStyle().getColor() != null
                        && component.getStyle().getColor().getValue() == ChatFormatting.GREEN.getColor());

        if (highlighted) {
            helper.succeed();
        } else {
            helper.fail("Expected the equipped ocean passive entry to be highlighted in the Ascension menu");
        }
    }

    /**
     * Compares lava movement speed of a Nether demigod player vs Nether god player;
     * asserts god player moves faster in lava.
     */
    @GameTest
    public void netherGodLavaSpeedHigherThanNetherDemigod(GameTestHelper helper) {
        ServerPlayer demigod = createPlayer(helper, new BlockPos(1, 2, 1));
        ServerPlayer god = createPlayer(helper, new BlockPos(5, 2, 1));
        configureNetherSwimmer(demigod, false);
        configureNetherSwimmer(god, true);

        fillLavaColumn(helper, demigod.blockPosition());
        fillLavaColumn(helper, god.blockPosition());

        helper.runAfterDelay(3, () -> {
            double demigodSpeed = demigod.getDeltaMovement().length();
            double godSpeed = god.getDeltaMovement().length();
            if (godSpeed > demigodSpeed) {
                helper.succeed();
            } else {
                helper.fail("Expected Nether god lava movement speed to exceed demigod speed; got "
                        + godSpeed + " vs " + demigodSpeed);
            }
        });
    }

    /**
     * Captures ghast as nether demigod (record speed); captures as nether god;
     * asserts god capture speed > demigod capture speed.
     */
    @GameTest
    public void ghastCarrySpeedHigherForGodRankThanDemigod(GameTestHelper helper) {
        ServerPlayer demigod = createPlayer(helper, new BlockPos(1, 2, 1));
        ServerPlayer god = createPlayer(helper, new BlockPos(12, 2, 1));
        configureGhastCarryPilot(demigod);
        configureGhastCarryPilot(god);

        SpellRegistry.ghast_carry(demigod, false, 1.0);
        SpellRegistry.ghast_carry(god, true, 11.5 / 6.0);

        helper.runAfterDelay(6, () -> {
            HappyGhast demigodGhast = findNearbyGhast(helper, demigod);
            HappyGhast godGhast = findNearbyGhast(helper, god);
            if (demigodGhast == null || godGhast == null) {
                helper.fail("Expected both ghast carry casts to spawn rideable ghasts");
                return;
            }

            double demigodSpeed = demigodGhast.getDeltaMovement().length();
            double godSpeed = godGhast.getDeltaMovement().length();
            if (godSpeed > demigodSpeed) {
                helper.succeed();
            } else {
                helper.fail("Expected god ghast carry speed to exceed demigod speed; got "
                        + godSpeed + " vs " + demigodSpeed);
            }
        });
    }

    /**
     * Uses /setinfluence player 5; opens ascension menu;
     * asserts "5" is displayed in the influence field.
     */
    @GameTest
    public void influenceDisplayShowsCorrectCountInMenu(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AscensionData data = (AscensionData) player;
        data.setInfluence(5);

        try {
            Method method = AscensionMenu.class.getDeclaredMethod("makeInfluenceIcon", AscensionData.class);
            method.setAccessible(true);
            Component icon = (Component) method.invoke(new AscensionMenu(), data);
            if (icon.getString().contains("5")) {
                helper.succeed();
            } else {
                helper.fail("Expected the Ascension menu influence display to include the current influence count");
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Failed to inspect the Ascension menu influence icon: " + e.getMessage());
        }
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, BlockPos relativePos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        player.setPos(absolutePos.getX() + 0.5, absolutePos.getY(), absolutePos.getZ() + 0.5);
        return player;
    }

    private static void invokeNaturalDeathLoss(ServerPlayer player, AscensionData data) {
        try {
            Method method = InfluenceManager.class.getDeclaredMethod(
                    "handleNaturalDeathInfluenceLoss", ServerPlayer.class, AscensionData.class);
            method.setAccessible(true);
            method.invoke(null, player, data);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke natural-death influence handling", e);
        }
    }

    private static boolean hasInfluenceDrop(GameTestHelper helper, BlockPos center, double radius) {
        return helper.getEntities(EntityType.ITEM, center, radius).stream()
                .map(ItemEntity.class::cast)
                .anyMatch(item -> InfluenceItem.isInfluenceItem(item.getItem()));
    }

    private static void configureNetherSwimmer(ServerPlayer player, boolean god) {
        AscensionData data = (AscensionData) player;
        data.setPassive("nether");
        data.setRank(god ? "god" : "demigod");
        data.setGodOrder(god ? "nether" : null);
        player.setYRot(0.0f);
        player.setXRot(0.0f);
        player.zza = 1.0f;
        player.xxa = 0.0f;
    }

    private static void fillLavaColumn(GameTestHelper helper, BlockPos origin) {
        helper.getLevel().setBlockAndUpdate(origin, Blocks.LAVA.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin.above(), Blocks.LAVA.defaultBlockState());
    }

    private static void configureGhastCarryPilot(ServerPlayer player) {
        player.setYRot(0.0f);
        player.setXRot(0.0f);
        player.zza = 1.0f;
        player.xxa = 0.0f;
    }

    private static HappyGhast findNearbyGhast(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(
                HappyGhast.class,
                new AABB(player.blockPosition()).inflate(8.0))
                .stream()
                .findFirst()
                .orElse(null);
    }
}
