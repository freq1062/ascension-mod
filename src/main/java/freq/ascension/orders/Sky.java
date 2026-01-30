package freq.ascension.orders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import freq.ascension.Utils;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class Sky implements Order {

    public static final Sky INSTANCE = new Sky();
    private static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWU1MmY3OTYwZmYzY2VjMmY1MTlhNjM1MzY0OGM2ZTMzYmM1MWUxMzFjYzgwOTE3Y2YxMzA4MWRlY2JmZjI0ZCJ9fX0=";
    private static final Map<UUID, Long> DOUBLE_JUMP_COOLDOWNS = new HashMap<>();

    @Override
    public Order getVersion(String rank) {
        if (rank == "god") {
            return SkyGod.INSTANCE;
        }
        return this;
    }

    public static Map<UUID, Long> getDoubleJumpCooldowns() {
        return DOUBLE_JUMP_COOLDOWNS;
    }

    public static void putDoubleJumpCooldown(UUID playerId, long timestamp) {
        DOUBLE_JUMP_COOLDOWNS.put(playerId, timestamp);
    }

    @Override
    public String getOrderName() {
        return "sky";
    }

    public TextColor getOrderColor() {
        // Light blue
        return TextColor.fromRgb(0x00d9ff);
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("dash", this, "utility", (player, stats) -> {
            SpellRegistry.dash(player, Utils.isGod(player), stats.getInt(0)); // Distance (blocks)
        }));

        SpellCooldownManager.register(new Spell("star_strike", this, "combat", (player, stats) -> {
            SpellRegistry.starStrike(player, stats.getBool(0));
            // augmented
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "double_jump" -> new SpellStats(160, "Jump twice mid-air to double jump", 6, false);
            // Jump height, slam
            case "dash" -> new SpellStats(225, "Dash forward 9 blocks", 9);
            case "star_strike" -> new SpellStats(675,
                    "Summon a 2x2 beam of light that damages and launches entities",
                    false);
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "Fall damage immunity. Double jump: Jump twice quickly to activate. Breezes are passive.";
            case "utility" -> "Dash ability.";
            case "combat" -> "Star Strike ability.";
            default -> "";
        };
    }

    @Override
    public void onEntityDamage(ServerPlayer victim, DamageContext context) {
        DamageSource source = context.getSource();
        if (source.is(DamageTypeTags.IS_FALL)) {
            context.setCancelled(true);
        } else if (source.is(DamageTypes.STALAGMITE)) {
            context.setAmount((float) (context.getAmount() * 0.5));
        }
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive")) {
            if (player.gameMode() == GameType.SURVIVAL || player.gameMode() == GameType.ADVENTURE) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        }
    }

    @Override
    public boolean isIgnoredBy(ServerPlayer player, Mob mob) {
        if (mob instanceof Breeze) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isDoubleJumpEnabled() {
        return true;
    }

    @Override
    public void applyProjectileShield(ServerPlayer player, Projectile projectile) {
        Vec3 velocity = projectile.getDeltaMovement();
        // 1. Threshold Check: If it's already barely moving, don't touch it.
        // 0.01 is a good "near zero" point for horizontal movement.
        if (Math.abs(velocity.x) < 0.01 && Math.abs(velocity.z) < 0.01) {
            return;
        }
        projectile.setDeltaMovement(velocity.scale(0.1));
        // if (!projectile.getTags().contains("sky_slowed")) {
        // projectile.addTag("sky_slowed");
        // projectile.setDeltaMovement(velocity.scale(0.5));
        // }
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
    public ItemStack getOrderItem() {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        Utils.applyTexture(head, HEAD_TEXTURE);
        return head;
    }
}
