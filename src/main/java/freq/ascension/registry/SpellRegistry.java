package freq.ascension.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import freq.ascension.Utils;
import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.orders.Earth;
import freq.ascension.orders.Order;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

public class SpellRegistry {
    // {id: spell}
    public static final Map<String, Spell> SPELLS = new HashMap<>();
    // {order: {type: [ spell1, spell2 ]}}
    private static final Map<Order, Map<String, List<Spell>>> BY_ORDER_AND_TYPE = new HashMap<>();

    public static void register(Spell spell) {
        SPELLS.put(spell.getId(), spell);
        BY_ORDER_AND_TYPE
                .computeIfAbsent(spell.getOrder(), k -> new HashMap<>())
                .computeIfAbsent(spell.getType(), k -> new ArrayList<>())
                .add(spell);
    }

    public static List<Spell> getEquippableSpells(ServerPlayer player) {
        List<Spell> equippable = new ArrayList<>();
        AscensionData data = (AscensionData) player;
        
        addSpellsIfPresent(equippable, data.getPassive(), "passive");
        addSpellsIfPresent(equippable, data.getUtility(), "utility");
        addSpellsIfPresent(equippable, data.getCombat(), "combat");
        
        return equippable;
    }

    private static void addSpellsIfPresent(List<Spell> targetList, Order order, String type) {
        if (order == null) return;
        Map<String, List<Spell>> orderSpells = BY_ORDER_AND_TYPE.get(order);
        if (orderSpells == null) return;
        List<Spell> spells = orderSpells.get(type);
        if (spells != null) {
            targetList.addAll(spells);
        }
    }

    public static void magmaBubble(ServerPlayer p, int radius, float damagePercent, boolean launch) {
        BlockPos center = p.getOnPos();

        // Allow a 1-block tolerance (e.g., slightly above the ground)
        Block blockBelow1 = p.level().getBlockState(center.below()).getBlock();
        Block blockBelow2 = p.level().getBlockState(center.below(2)).getBlock();

        boolean standingOnSolid = !blockBelow1.defaultBlockState().isAir() || !blockBelow2.defaultBlockState().isAir();

        boolean submergedInLava = p.level().getFluidState(center).is(net.minecraft.tags.FluidTags.LAVA)
                || (blockBelow1 != null
                        && p.level().getFluidState(center.below()).is(net.minecraft.tags.FluidTags.LAVA));

        if (!standingOnSolid && !submergedInLava) {
            p.sendSystemMessage(Component.literal("You need solid ground or lava to unleash Magma Bubble."));
            return;
        }

        ActiveSpell as = SpellCooldownManager.addToActiveSpells(p, SpellCooldownManager.get("magma_bubble"));

        // float heightScale = launch ? 1.0f : 0.6f;
        // MagmaSpike.spawnMagmaSpikes(center, heightScale);

        if (launch) {
            net.minecraft.world.phys.Vec3 velocity = p.getDeltaMovement();
            p.setDeltaMovement(velocity.x, Math.max(velocity.y, 1.2), velocity.z);
            p.hasImpulse = true;
            p.setIgnoreFallDamageFromCurrentImpulse(true);
        }

        AABB searchBox = new AABB(center).inflate(radius);
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (target != p) {
                Utils.spellDmg(target, p, damagePercent);
            }
        }

        net.minecraft.world.level.Level level = p.level();
        net.minecraft.sounds.SoundSource source = net.minecraft.sounds.SoundSource.PLAYERS;

        level.playSound(null, center, net.minecraft.sounds.SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), source, 1.0f,
                0.5f);
        level.playSound(null, center, net.minecraft.sounds.SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE,
                source,
                1.0f, 0.625f);
        level.playSound(null, center, net.minecraft.sounds.SoundEvents.MACE_SMASH_GROUND, source, 1.0f,
                launch ? 0.5f : 0.65f);

        as.setInUse(false);
    }

    // Actual supermine handled in EARTH event listeners
    public static void toggleSupermine(ServerPlayer player) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("supermine"));
        Earth.toggleSupermine(player);
        as.setInUse(false);
    }
}
