package freq.ascension.orders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import freq.ascension.Utils;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.telemetry.TelemetryProperty.GameMode;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

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

    // @Override
    // public void applyEffect(ServerPlayer player) {
    // if (hasCapability(player, "passive") && player.getAllowFlight() == false) {
    // player.setFlying(false);
    // player.setAllowFlight(true);
    // // Make double jump possible
    // }
    // }

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
    public void onEntityDamage(ServerPlayer victim, DamageSource source, float amount) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_STALACTITE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE)) {
            // This method usually requires a way to modify the damage, but since the
            // signature
            // provided in the interface implies a listener rather than a cancellable event,
            // we will assume the caller handles the return logic or healing.
            // However, based on the context of the errors, it looks like you want to negate
            // damage.

            // Note: Preventing damage in a simple void listener is difficult without a
            // Returnable/Cancellable event.
            // Assuming this hook is called *before* damage is applied (e.g. from a Mixin),
            // you might heal the player immediately or the interface allows returning a
            // boolean/float.

            // Since I cannot change the interface signature provided in your full file,
            // I will adapt the logic to best fit what is available in Vanilla/Fabric.

            if (Utils.isGod(victim)) {
                // Gods receive no dripstone damage - usually requires cancelling via mixin
                // return
                // For now, we set the victim to be invulnerable for a tick if possible, or just
                // heal back.
                victim.heal(amount);
            } else {
                // Demigods receive 50% less dripstone damage
                victim.heal(amount * 0.5f);
            }
        }
    }

    @Override
    public void onToggleFlight(ServerPlayer player, boolean flying) {
        // Don't prevent spectators or creatives from flying
        if (player.gameMode() == GameType.CREATIVE || player.gameMode() == GameType.SPECTATOR)
            return;
        if (!hasCapability(player, "passive"))
            return;
        SpellStats stats = getSpellStats("double_jump");
        SpellRegistry.double_jump(player, stats.getInt(0), stats.getBool(1));
        flying = false;
    }

    @Override
    public ItemStack getOrderItem() {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        Utils.applyTexture(head, HEAD_TEXTURE);
        return head;
    }
}
