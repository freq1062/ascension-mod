package freq.ascension.orders;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Ascension;
import freq.ascension.Config;
import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class Ocean implements Order {
    public static final Ocean INSTANCE = new Ocean();

    /** Tracks powder_snow blocks temporarily replaced with snow_block per-player. */
    public static final ConcurrentHashMap<UUID, Set<BlockPos>> CONVERTED_SNOW = new ConcurrentHashMap<>();

    /** Restores all converted snow_block positions back to powder_snow for a player. */
    public static void restoreConvertedSnow(ServerPlayer player) {
        Set<BlockPos> converted = CONVERTED_SNOW.remove(player.getUUID());
        if (converted == null || converted.isEmpty()) return;
        ServerLevel level = (ServerLevel) player.level();
        for (BlockPos pos : converted) {
            if (level.getBlockState(pos).is(Blocks.SNOW_BLOCK)) {
                level.setBlock(pos, Blocks.POWDER_SNOW.defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.POWDER_SNOW_STEP, SoundSource.BLOCKS, 0.6f, 0.9f);
            }
        }
    }

    @Override
    public Order getVersion(String rank) {
        if (rank.equals("god")) {
            return OceanGod.INSTANCE;
        }
        return this;
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("dolphins_grace", this, "passive", (player, stats) -> {
            SpellRegistry.dolphinsGrace(player);
        }));

        SpellCooldownManager.register(new Spell("molecular_flux", this, "utility", (player, stats) -> {
            SpellRegistry.molecularFlux(player,
                    stats.getInt(0), // range
                    stats.getInt(1) // duration
            );
        }));

        SpellCooldownManager.register(new Spell("drown", this, "combat", (player, stats) -> {
            SpellRegistry.drown(player, stats.getInt(0), stats.getInt(1));
            // duration, radius
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dolphins_grace" -> new SpellStats(Config.oceanDolphinsGraceCD,
                    "Toggle between normal swimming speed and Dolphin's Grace 1.",
                    0);
            case "molecular_flux" ->
                new SpellStats(Config.oceanMolecularFluxCD, "Transforms water related blocks between states", Config.oceanMolecularFluxRange, Config.oceanMolecularFluxDuration);
            case "drown" -> new SpellStats(Config.oceanDrownCD,
                    "Drowns players within 8 blocks and activates passives on land for 7s",
                    Config.oceanDrownDuration, Config.oceanDrownRadius);
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "Permanent Conduit Power. Autocrit in water. DOLPHIN'S GRACE:"
                    + getSpellStats("dolphins_grace").getDescription();
            case "utility" -> {
                SpellStats s = getSpellStats("molecular_flux");
                yield "MOLECULAR FLUX: Transform water-related blocks in a " + s.getInt(0)
                        + "-block range for " + s.getInt(1) + "s. " + s.getCooldownSecs() + "s cooldown.";
            }
            case "combat" -> {
                SpellStats s = getSpellStats("drown");
                yield "DROWN: Drowns players within " + s.getInt(1) + " blocks for " + s.getInt(0)
                        + "s. Activates passives on land for the duration.";
            }
            default -> "";
        };
    }

    @Override
    public boolean canWalkOnPowderSnow(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public void onUnequip(ServerPlayer player, String slotType) {
        if ("passive".equals(slotType)) {
            restoreConvertedSnow(player);
            restoreRealBoots(player);
        }
    }

    /**
     * Sends a fake leather-boots equipment packet to the client so it predicts
     * walking on powder snow correctly. Does NOT change the server inventory.
     */
    public static void sendFakeLeatherBoots(ServerPlayer player) {
        ClientboundSetEquipmentPacket pkt = new ClientboundSetEquipmentPacket(
                player.getId(),
                java.util.List.of(com.mojang.datafixers.util.Pair.of(
                        EquipmentSlot.FEET,
                        new ItemStack(Items.LEATHER_BOOTS)
                ))
        );
        player.connection.send(pkt);
        Ascension.LOGGER.debug("[PowderSnow] Sent fake leather boots to {}", player.getName().getString());
    }

    /**
     * Restores the client's feet slot to match the server's actual item.
     * Called on passive unequip and disconnect.
     */
    public static void restoreRealBoots(ServerPlayer player) {
        ClientboundSetEquipmentPacket pkt = new ClientboundSetEquipmentPacket(
                player.getId(),
                java.util.List.of(com.mojang.datafixers.util.Pair.of(
                        EquipmentSlot.FEET,
                        player.getItemBySlot(EquipmentSlot.FEET).copy()
                ))
        );
        player.connection.send(pkt);
        Ascension.LOGGER.debug("[PowderSnow] Restored real boots to {}", player.getName().getString());
    }

    @Override
    public void onEntityDamageByEntity(ServerPlayer attacker, LivingEntity victim, DamageContext context) {
        float damage = context.getAmount();
        // Ignore very low-damage (sweep) attacks
        if (damage < 0.1)
            return;
        ActiveSpell as = SpellCooldownManager.getActiveSpell(attacker, SpellCooldownManager.get("drown"));
        if ((attacker.isInWaterOrRain() && hasCapability(attacker, "passive"))
                || (as != null && as.isInUse())) {
            context.setAmount((float) (context.getAmount() * 1.5));
            attacker.level().playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        10, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, true, false, true));
    }

    public String getOrderName() {
        return "ocean";
    }

    public TextColor getOrderColor() {
        // Dark blue
        return TextColor.fromRgb(0x001eff);
    }

    @Override
    public String getOrderIcon() {
        return "\uE184";
    }
}