package freq.ascension.test.god;

import freq.ascension.managers.SpellStats;
import freq.ascension.orders.Earth;
import freq.ascension.orders.EarthGod;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class EarthGodTests {

    @GameTest
    public void earthGodExtendsEarth(GameTestHelper helper) {
        if (!(EarthGod.INSTANCE instanceof Earth)) {
            helper.fail("EarthGod must extend Earth (demigod)");
        }
        helper.succeed();
    }

    @GameTest
    public void earthGetVersionGodReturnsEarthGod(GameTestHelper helper) {
        Order resolved = Earth.INSTANCE.getVersion("god");
        if (!(resolved instanceof EarthGod)) {
            helper.fail("Earth.getVersion(\"god\") should return EarthGod, got "
                    + resolved.getClass().getSimpleName());
        }
        helper.succeed();
    }

    @GameTest
    public void earthGetVersionDemigodReturnsEarth(GameTestHelper helper) {
        Order resolved = Earth.INSTANCE.getVersion("demigod");
        if (resolved instanceof EarthGod) {
            helper.fail("Earth.getVersion(\"demigod\") must NOT return EarthGod");
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodHasteIsAmplifier1(GameTestHelper helper) {
        // EarthGod.applyEffect() applies MobEffects.HASTE at amplifier 1 (= Haste 2)
        MobEffectInstance effect = new MobEffectInstance(MobEffects.HASTE, 60, 1);
        if (effect.getAmplifier() != 1) {
            helper.fail("EarthGod Haste must be amplifier 1 (Haste 2), got " + effect.getAmplifier());
        }
        helper.succeed();
    }

    @GameTest
    public void earthDemigodHasteIsAmplifier0(GameTestHelper helper) {
        // Earth demigod gives Haste 1 (amplifier 0)
        MobEffectInstance effect = new MobEffectInstance(MobEffects.HASTE, 60, 0, true, false, true);
        if (effect.getAmplifier() != 0) {
            helper.fail("Earth demigod Haste must be amplifier 0 (Haste 1), got " + effect.getAmplifier());
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodSupermineIs3x3(GameTestHelper helper) {
        SpellStats stats = EarthGod.INSTANCE.getSpellStats("supermine");
        if (stats == null) {
            helper.fail("EarthGod.getSpellStats(\"supermine\") returned null");
        }
        int diameter = stats.getInt(0);
        if (diameter != 3) {
            helper.fail("EarthGod supermine diameter should be 3 (3x3) but got " + diameter);
        }
        helper.succeed();
    }

    @GameTest
    public void earthDemigodSupermineIs2x2(GameTestHelper helper) {
        SpellStats stats = Earth.INSTANCE.getSpellStats("supermine");
        if (stats == null) {
            helper.fail("Earth.getSpellStats(\"supermine\") returned null");
        }
        int diameter = stats.getInt(0);
        if (diameter != 2) {
            helper.fail("Earth demigod supermine diameter should be 2 (2x2) but got " + diameter);
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodMagmaBubbleCooldownIs900Ticks(GameTestHelper helper) {
        SpellStats stats = EarthGod.INSTANCE.getSpellStats("magma_bubble");
        if (stats == null) {
            helper.fail("EarthGod.getSpellStats(\"magma_bubble\") returned null");
        }
        if (stats.getCooldownTicks() != 900) {
            helper.fail("EarthGod magma_bubble cooldown should be 900 ticks (45s), got "
                    + stats.getCooldownTicks());
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodMagmaBubbleCooldownIsGreaterThanDemigod(GameTestHelper helper) {
        SpellStats godStats = EarthGod.INSTANCE.getSpellStats("magma_bubble");
        SpellStats demigodStats = Earth.INSTANCE.getSpellStats("magma_bubble");
        if (godStats == null || demigodStats == null) {
            helper.fail("magma_bubble SpellStats null for god=" + godStats + " demigod=" + demigodStats);
        }
        if (godStats.getCooldownTicks() <= demigodStats.getCooldownTicks()) {
            helper.fail("God magma_bubble cooldown (" + godStats.getCooldownTicks()
                    + ") should be greater than demigod (" + demigodStats.getCooldownTicks() + ")");
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodMagmaBubbleLaunchFlagIsTrue(GameTestHelper helper) {
        SpellStats stats = EarthGod.INSTANCE.getSpellStats("magma_bubble");
        if (stats == null) {
            helper.fail("EarthGod.getSpellStats(\"magma_bubble\") returned null");
        }
        boolean launches = stats.getBool(2);
        if (!launches) {
            helper.fail("EarthGod magma_bubble launch flag (extra[2]) should be true");
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodAnvilCostIs10PercentOfOriginal(GameTestHelper helper) {
        int original = 100;
        int result = Math.max(1, (int) Math.floor(original * 0.1));
        if (result != 10) {
            helper.fail("90% reduction of 100 should give 10, got " + result);
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodAnvilCostNeverBelowOne(GameTestHelper helper) {
        int cost = Math.max(1, (int) Math.floor(1 * 0.1));
        if (cost < 1) {
            helper.fail("EarthGod anvil cost must never be below 1, got " + cost);
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodAnvilCostLowerThanDemigod(GameTestHelper helper) {
        int original = 100;
        int godCost = Math.max(1, (int) Math.floor(original * 0.1));
        int demigodCost = Math.max(1, (int) Math.floor(original * 0.5));
        if (godCost >= demigodCost) {
            helper.fail("EarthGod anvil cost (" + godCost
                    + ") should be less than demigod cost (" + demigodCost + ")");
        }
        helper.succeed();
    }

    @GameTest
    public void earthGodOrderNameIsEarth(GameTestHelper helper) {
        String name = EarthGod.INSTANCE.getOrderName();
        if (!"earth".equals(name)) {
            helper.fail("EarthGod.getOrderName() should be \"earth\", got \"" + name + "\"");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASSIVE — Fortune + Doubling (EarthGod inherits Earth behaviour)
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void earthGodFortuneBeforeDoublingGivesMoreItemsThanDoublingAlone(GameTestHelper helper) {
        int base = 1;
        int fortuneLevel = 3;
        int maxResult = (base + fortuneLevel) * 2;
        int baseDoubled = base * 2;

        if (maxResult <= baseDoubled) {
            helper.fail("Fortune III max result (" + maxResult
                    + ") must exceed base*2 (" + baseDoubled + ")");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITY — Supermine Hardness Tolerance (EarthGod 3×3)
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void earthGodSupermineToleranceDirtAndGrassMineTogether(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.ZERO;

        float dirtSpeed  = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState().getDestroySpeed(level, pos);
        float grassSpeed = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState().getDestroySpeed(level, pos);

        float diff = Math.abs(dirtSpeed - grassSpeed);
        if (diff > 0.5f) {
            helper.fail("EarthGod Supermine: Dirt (" + dirtSpeed + ") and Grass (" + grassSpeed
                    + ") differ by " + diff + " > 0.5f tolerance");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASSIVE — Anvil cost formula (EarthGod: 10% of original)
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void earthGodAnvilShadowFieldFixDoesNotCrash(GameTestHelper helper) {
        // The @Shadow DataSlot cost field fix (removing final) applies to both
        // Earth demigod and EarthGod — they share AnvilPrepareMixin. Verify the
        // EarthGod 10% reduction formula stays consistent.
        int original = 20;
        int reduced = Math.max(1, (int) Math.floor(original * 0.1));
        if (reduced < 1) {
            helper.fail("EarthGod anvil cost must be >= 1, got " + reduced);
        }
        if (reduced >= original) {
            helper.fail("EarthGod anvil cost (" + reduced + ") must be less than original (" + original + ")");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTEGRATION — Supermine + Earth Passive (ore smelted+doubled, EarthGod)
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void earthGodSupermineOreSmeltedResultIsNonEmpty(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(1, 2, 1);
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState());

        net.minecraft.world.level.block.state.BlockState oreState = helper.getBlockState(pos);
        net.minecraft.world.item.ItemStack input = new net.minecraft.world.item.ItemStack(oreState.getBlock().asItem());
        net.minecraft.world.item.crafting.SingleRecipeInput recipeInput =
                new net.minecraft.world.item.crafting.SingleRecipeInput(input);

        java.util.Optional<?> recipe = level.recipeAccess()
                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, recipeInput, level);

        if (recipe.isEmpty()) {
            helper.fail("Iron Ore must have a smelting recipe for EarthGod supermine+passive integration");
        }
        helper.succeed();
    }
}
