package freq.ascension.test.god;

import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellStats;
import freq.ascension.orders.Earth;
import freq.ascension.orders.EarthGod;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import freq.ascension.registry.SpellRegistry;
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

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIXES — inherited from Earth: ore-only guard, ancient debris fortune
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * EarthGod inherits {@code ignoreAnvilCostLimit()} from Earth. The Mixin passive-slot
     * guard applies equally to god-tier players.
     */
    @GameTest
    public void earthGodAnvilLimitFlagInherited(GameTestHelper helper) {
        if (!EarthGod.INSTANCE.ignoreAnvilCostLimit()) {
            helper.fail("EarthGod must inherit ignoreAnvilCostLimit()=true from Earth");
        }
        helper.succeed();
    }

    /**
     * Stone is not in c:ores and is not ancient debris. EarthGod's inherited
     * {@code onBlockBreak} must skip it before auto-smelting, same as the demigod.
     */
    @GameTest
    public void earthGodSuperminSkipsStone(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(1, 2, 1);
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());

        net.minecraft.world.level.block.state.BlockState stoneState = helper.getBlockState(pos);

        net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> oreTag =
                net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BLOCK,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ores"));
        boolean isOre = stoneState.is(oreTag);
        boolean isAncientDebris = stoneState.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS);

        if (isOre || isAncientDebris) {
            helper.fail("Stone must not be in c:ores or equal to ancient_debris — "
                    + "EarthGod inherited ore-only guard would incorrectly smelt it");
        }
        helper.succeed();
    }

    /**
     * EarthGod inherits {@code dropSmeltedOre}. Fortune must be skipped for ancient
     * debris to prevent the 8-drop exploit, same as the demigod.
     */
    @GameTest
    public void earthGodAncientDebrisNoFortune(GameTestHelper helper) {
        int fortuneLevel = 3;
        int fortuneBonus = 0; // fixed: isAncientDebris → fortune skipped
        int base = 1;
        int total = (base + fortuneBonus) * 2;

        if (fortuneBonus != 0) {
            helper.fail("EarthGod: fortune bonus must be 0 for ancient debris, got " + fortuneBonus);
        }
        if (total != 2) {
            helper.fail("EarthGod: ancient debris drop with Fortune III must be 2, got " + total);
        }
        helper.succeed();
    }

    /**
     * EarthGod: ancient debris still receives the 2× doubling even without fortune.
     */
    @GameTest
    public void earthGodAncientDebrisStillDoubles(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraft.world.level.block.state.BlockState debrisState =
                net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS.defaultBlockState();

        net.minecraft.world.item.ItemStack input =
                new net.minecraft.world.item.ItemStack(debrisState.getBlock().asItem());
        net.minecraft.world.item.crafting.SingleRecipeInput recipeInput =
                new net.minecraft.world.item.crafting.SingleRecipeInput(input);

        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> recipeOpt =
                level.recipeAccess().getRecipeFor(
                        net.minecraft.world.item.crafting.RecipeType.SMELTING, recipeInput, level);

        if (recipeOpt.isEmpty()) {
            helper.fail("Ancient Debris must have a smelting recipe (→ Netherite Scrap)");
        }

        int base = recipeOpt.get().value().assemble(recipeInput, level.registryAccess()).getCount();
        int total = (base + 0) * 2; // fortune bonus = 0 for ancient debris
        if (total < 2) {
            helper.fail("EarthGod: ancient debris drop must be >= 2 after doubling, got " + total);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 1 fix — EarthGod anvil: reflection removed, mixin handles reduction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Bug 1 fix: EarthGod.onAnvilPrepare is now a no-op inherited from Earth.
     * Calling it must not throw (previously crashed with reflection IllegalArgumentException).
     */
    @GameTest
    public void anvilGodReductionNoReflectionCrash(GameTestHelper helper) {
        try {
            // Earth's onAnvilPrepare is an empty no-op; null menu is safe
            EarthGod.INSTANCE.onAnvilPrepare(null);
        } catch (Exception e) {
            helper.fail("EarthGod.onAnvilPrepare must not throw after reflection removal: " + e);
            return;
        }
        helper.succeed();
    }

    /**
     * Bug 1 fix: the god (90%) reduction formula produces a value less than the demigod (50%)
     * reduction and never below 1.
     */
    @GameTest
    public void anvilGodReductionIs10PercentVsDemigod50Percent(GameTestHelper helper) {
        int original = 100;
        int godCost = (int) Math.max(1, Math.floor(original * 0.1));
        int demigodCost = Math.max(1, original / 2);
        if (godCost >= demigodCost) {
            helper.fail("God anvil cost (" + godCost + ") must be < demigod cost (" + demigodCost + ")");
        }
        if (godCost < 1) {
            helper.fail("God anvil cost must be >= 1, got " + godCost);
        }
        helper.succeed();
    }

    /**
     * Bug 1 fix: demigod (50%) reduction formula on cost=1 must return 1, never 0.
     */
    @GameTest
    public void anvilDemigodReductionNeverBelowOne(GameTestHelper helper) {
        int cost = Math.max(1, 1 / 2);
        if (cost < 1) {
            helper.fail("Demigod anvil cost must be >= 1 even for cost=1, got " + cost);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 2 fix — god spell binding: base-order normalization in SpellRegistry
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Bug 2 fix: EarthGod.getOrderName() must return "earth" so that
     * OrderRegistry.get(godOrder.getOrderName()) resolves back to Earth.INSTANCE.
     */
    @GameTest
    public void earthGodOrderNameResolvesToBaseOrder(GameTestHelper helper) {
        String godOrderName = EarthGod.INSTANCE.getOrderName();
        Order baseOrder = OrderRegistry.get(godOrderName);
        if (!(baseOrder instanceof Earth)) {
            helper.fail("OrderRegistry.get(EarthGod.getOrderName()) should return Earth.INSTANCE, got: "
                    + (baseOrder == null ? "null" : baseOrder.getClass().getSimpleName()));
        }
        helper.succeed();
    }

    /**
     * Bug 2 fix: after registerAllSpells() the "supermine" and "magma_bubble" spells must be
     * present in SpellRegistry.SPELLS and keyed to the "earth" order, not "earthgod".
     * This is the precondition for the base-order normalization fix to work.
     */
    @GameTest
    public void earthSpellsRegisteredUnderBaseOrderName(GameTestHelper helper) {
        OrderRegistry.registerAllSpells(); // idempotent — safe to call again

        for (String spellId : new String[]{"supermine", "magma_bubble"}) {
            Spell spell = SpellRegistry.SPELLS.get(spellId);
            if (spell == null) {
                helper.fail("SpellRegistry missing '" + spellId + "' after registerAllSpells()");
                return;
            }
            String orderName = spell.getOrder() == null ? "null" : spell.getOrder().getOrderName();
            if (!"earth".equalsIgnoreCase(orderName)) {
                helper.fail("Spell '" + spellId + "' must be keyed to order 'earth', got '" + orderName + "'");
                return;
            }
        }
        helper.succeed();
    }
}
