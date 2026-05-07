package freq.ascension.orders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import freq.ascension.Utils;
import freq.ascension.config.ConfigGroup;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.GameType;

public class Sky implements Order {

    public static final Sky INSTANCE = new Sky();
    private static final Map<UUID, Long> DOUBLE_JUMP_COOLDOWNS = new HashMap<>();

    public static Map<UUID, Long> getDoubleJumpCooldowns() {
        return DOUBLE_JUMP_COOLDOWNS;
    }

    public static void putDoubleJumpCooldown(UUID playerId, long timestamp) {
        DOUBLE_JUMP_COOLDOWNS.put(playerId, timestamp);
    }

    /*
     * Default configs
     */

    public static final ConfigGroup CONFIG_GROUP = new ConfigGroup("sky")
            .add("dripstone_dmg_percent", 50)
            .add("projectile_speed_percent", 50)
            .add("double_jump.cooldown_ticks", 160)
            .add("double_jump_height", 6)
            .add("dash.cooldown_ticks", 225)
            .add("dash.distance", 9)
            .add("star_strike.cooldown_ticks", 675)
            .add("star_strike.damage_percent", 30)
            .add("star_strike.range", 32)
            .add("star_strike.glowcol", 0xFFFFFF);

    /*
     * Metadata
     */

    @Override
    public String getOrderName() {
        return "sky";
    }

    public TextColor getOrderColor() {
        // Light blue
        return TextColor.fromRgb(0x00d9ff);
    }

    @Override
    public String getOrderIcon() {
        return "\uE182";
    }

    /*
     * Stats, spells, descriptions
     */

    @Override
    public Order getVersion(String rank) {
        if ("god".equals(rank)) {
            return SkyGod.INSTANCE;
        }
        return this;
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            // Double jump is also implemented in SpellRegistry.java
            case "passive" -> {
                int dp = CONFIG_GROUP.get("dripstone_dmg_percent");
                int pp = CONFIG_GROUP.get("projectile_speed_percent");
                yield "No fall damage. Dripstone deals " + dp + "% damage. Harmful projectiles slowed to " + pp
                        + "%. Breezes are passive. Double jump by jumping twice quickly.";
            }
            default -> "";
        };
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("dash", this, "utility", (player, stats) -> {
            SpellRegistry.dash(player, Utils.isGod(player), stats.getInt(0)); // Distance (blocks)
        }));

        SpellCooldownManager.register(new Spell("star_strike", this, "combat", (player, stats) -> {
            SpellRegistry.starStrike(
                    player,
                    stats.getInt(0), // glowcol
                    stats.getInt(1), // range(blocks)
                    stats.getInt(2) // damage(percent)
            );
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dash" -> {
                int cd = CONFIG_GROUP.get("dash.cooldown_ticks");
                int ds = CONFIG_GROUP.get("dash.distance");
                yield new SpellStats(cd, "Dash in the direction you're facing by " + ds + " blocks. ", ds);
            }
            case "star_strike" -> {
                int cd = CONFIG_GROUP.get("star_strike.cooldown_ticks");
                int gl = CONFIG_GROUP.get("star_strike.glowcol");
                int ran = CONFIG_GROUP.get("star_strike.range");
                int dmg = CONFIG_GROUP.get("star_strike.damage_percent");
                yield new SpellStats(cd,
                        "Summon a 2x2 beam of light that deals " + dmg
                                + "% max hp and launches entities into the sky. Range" + ran + " blocks.",
                        gl, ran, dmg);
            }
            default -> null;
        };
    }

    /*
     * Main body
     */

    @Override
    public void onEntityDamage(ServerPlayer victim, DamageContext context) {
        DamageSource source = context.getSource();

        // STALAGMITE is tagged IS_FALL in vanilla, so it must be checked first.
        // Dripstone damage: 50% reduction for demigods (gods get full immunity via
        // SkyGod).
        if (source.is(DamageTypes.STALAGMITE)) {
            context.setAmount(context.getAmount() * (CONFIG_GROUP.get("dripstone_dmg_percent") / 100));
        } else if (source.is(DamageTypeTags.IS_FALL)) {
            // Demigods: only fall damage is cancelled, not projectile damage
            context.setCancelled(true);
        }
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive")) {
            if (player.gameMode() == GameType.SURVIVAL || player.gameMode() == GameType.ADVENTURE) {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = false; // prevent stale flying state from being broadcast
                player.onUpdateAbilities();
            }
        }
    }

    @Override
    public boolean isIgnoredBy(ServerPlayer player, Mob mob) {
        if (mob instanceof Breeze && hasCapability(player, "passive"))
            return true;
        return false;
    }

    @Override
    public boolean isDoubleJumpEnabled() {
        return true;
    }

    protected boolean nonHarmfulProjectiles(Projectile proj) {
        EntityType<?> type = proj.getType();
        if (type == EntityType.ENDER_PEARL
                || type == EntityType.EYE_OF_ENDER
                || type == EntityType.EXPERIENCE_BOTTLE) {
            return true;
        }

        if (type == EntityType.SPLASH_POTION || type == EntityType.LINGERING_POTION) {
            if (proj instanceof AbstractThrownPotion thrown) {
                ItemStack stack = thrown.getItem();
                PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);

                if (contents == null || contents.equals(PotionContents.EMPTY)) {
                    return true;
                }
                for (MobEffectInstance inst : contents.getAllEffects()) {
                    MobEffectCategory category = inst.getEffect().value().getCategory();

                    if (category == MobEffectCategory.HARMFUL || category == MobEffectCategory.NEUTRAL) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        return false;
    }

    @Override
    public void applyProjectileShield(ServerPlayer player, Projectile projectile) {
        if (nonHarmfulProjectiles(projectile))
            return;
        if (projectile.getTags().contains("sky_slowed") || projectile.getOwner() == player)
            return;

        if (projectile.level() instanceof ServerLevel serverLevel) {
            // Demigods: reduce projectile velocity by 50%
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.5));

            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.GLOW,
                    projectile.getX(), projectile.getY(), projectile.getZ(),
                    5,
                    0.1, 0.1, 0.1,
                    0.01);
            projectile.addTag("sky_slowed"); // prevent multiple velocity reductions
        }
    }

    @Override
    public void onToggleFlight(ServerPlayer player) {
        // Don't prevent spectators or creatives from flying
        if (player.gameMode() == GameType.CREATIVE || player.gameMode() == GameType.SPECTATOR)
            return;
        if (!hasCapability(player, "passive"))
            return;
        SpellStats stats = getSpellStats("double_jump");
        SpellRegistry.double_jump(player, stats.getInt(0), stats.getBool(1));
    }

    @Override
    public void onUnequip(ServerPlayer player, String slotType) {
        if ("passive".equals(slotType)) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }
}
