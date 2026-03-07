package freq.ascension.orders;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.SimpleGui;
import freq.ascension.Ascension;
import freq.ascension.managers.EnderRowManager;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Stub scaffold for the End Order (Demigod tier).
 *
 * <p>This file exists so that {@code EndDemigodTests} compiles and fails on
 * assertion (not on missing class). All methods return safe defaults; the
 * actual implementation is Phase 2 work.
 *
 * <p><b>Abilities (to be implemented):</b>
 * <ul>
 *   <li><b>Passive</b>: Non-boss End mobs neutral (Enderman, Endermite,
 *       Shulker); ender pearl cooldown −50%; ender chest +1 extra row;
 *       custom purple+bold chest title; unequip guard while extra row
 *       contains items.</li>
 *   <li><b>Utility</b>: Teleport — 10-block ray trace, stops at 2nd solid
 *       block, teleports to farthest valid position, enderman particles.</li>
 *   <li><b>Combat</b>: Desolation of Time — 7-block radius disables combat
 *       abilities for 5 s + Weakness I for 10 s; DragonCurve VFX.</li>
 * </ul>
 */
public class End implements Order {

    public static final End INSTANCE = new End();

    @Override
    public String getOrderName() {
        return "end";
    }

    @Override
    public String getOrderIcon() {
        return "\uE188";
    }

    @Override
    public TextColor getOrderColor() {
        return TextColor.fromRgb(0x7B2FBE);
    }

    @Override
    public Order getVersion(String rank) {
        // No god tier defined yet — return self
        return this;
    }

    /** Players currently affected by Desolation of Time (combat disabled). */
    public static final Set<UUID> DESOLATED_PLAYERS = ConcurrentHashMap.newKeySet();

    public static boolean isAffectedByDesolation(ServerPlayer player) {
        return DESOLATED_PLAYERS.contains(player.getUUID());
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("teleport", this, "utility", (player, stats) -> {
            SpellRegistry.teleport(player);
        }));
        SpellCooldownManager.register(new Spell("desolation_of_time", this, "combat", (player, stats) -> {
            SpellRegistry.desolationOfTime(player, stats);
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        // Stubs — durations/cooldowns filled in during Phase 2
        return switch (spellId.toLowerCase()) {
            case "teleport" -> new SpellStats(30, "Teleport up to 10 blocks in your look direction.");
            case "desolation_of_time" -> new SpellStats(120,
                    "Within 7 blocks: disable combat abilities 5 s, Weakness I 10 s.",
                    100, // disableDurationTicks (5 s)
                    200  // weaknessDurationTicks (10 s)
            );
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "End mobs neutral. Ender pearl cooldown halved. Ender chest +1 row.";
            case "utility" -> "Teleport up to 10 blocks.";
            case "combat" -> "Desolation of Time — disable combat + Weakness I in radius.";
            default -> "";
        };
    }

    /**
     * End mob neutrality: Enderman, Endermite, and Shulker are neutral when the
     * player has the End passive equipped. Boss mobs (Ender Dragon) are excluded.
     */
    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        // MobTargetMixin only reaches here when the player has End equipped.
        // Enderman, Endermite, and Shulker are neutral; Ender Dragon (boss) is excluded.
        EntityType<?> type = mob.getType();
        return type == EntityType.ENDERMAN
                || type == EntityType.ENDERMITE
                || type == EntityType.SHULKER;
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        // Phase 2: apply passive effects (fire resistance not needed; passive is
        // mob neutrality + cooldown reduction — no potion effect tick needed)
    }

    // ─── Extra ender-chest row ─────────────────────────────────────────────────

    /**
     * Opens a custom 4-row chest GUI for the player:
     * <ul>
     *   <li>Rows 0–2 (slots 0–26) redirect to the player's vanilla ender-chest inventory.</li>
     *   <li>Row 3 (slots 27–35) redirects to a temporary container backed by
     *       {@link EnderRowManager}; changes are persisted when the GUI closes.</li>
     * </ul>
     */
    public void openEnderChestWithExtraRow(ServerPlayer player) {
        MinecraftServer server = Ascension.getServer();
        UUID uuid = player.getUUID();
        EnderRowManager mgr = EnderRowManager.get(server);

        // Build a temporary SimpleContainer for the extra row so slot redirects work
        SimpleContainer extraContainer = new SimpleContainer(EnderRowManager.ROW_SIZE);
        ItemStack[] extraRow = mgr.getExtraRow(uuid);
        for (int i = 0; i < EnderRowManager.ROW_SIZE; i++) {
            extraContainer.setItem(i, extraRow[i].copy());
        }

        // 4-row GUI (36 slots total)
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x4, player, false) {
            @Override
            public void onClose() {
                // Persist the extra row back to EnderRowManager
                for (int i = 0; i < EnderRowManager.ROW_SIZE; i++) {
                    mgr.setSlot(uuid, i, extraContainer.getItem(i).copy());
                }
            }
        };

        gui.setTitle(Component.literal("Ender Chest")
                .withStyle(style -> style
                        .withColor(TextColor.fromRgb(0x7B2FBE))
                        .withBold(true)
                        .withItalic(false)));

        // Redirect first 27 slots to the vanilla ender chest — item movements
        // are handled directly by Minecraft and do not need explicit save logic.
        SimpleContainer enderInv = player.getEnderChestInventory();
        for (int i = 0; i < 27; i++) {
            gui.setSlotRedirect(i, new Slot(enderInv, i, 0, 0));
        }

        // Redirect the extra row to our temporary container
        for (int i = 0; i < EnderRowManager.ROW_SIZE; i++) {
            gui.setSlotRedirect(27 + i, new Slot(extraContainer, i, 0, 0));
        }

        gui.open();
    }

    // ─── Unequip guard ────────────────────────────────────────────────────────

    /**
     * Prevents the player from unequipping the End passive while their extra ender-chest
     * row still contains items.  All other slots (utility, combat) are always allowed.
     */
    @Override
    public boolean canUnequip(ServerPlayer player) {
        if (!hasCapability(player, "passive")) return true;
        EnderRowManager mgr = EnderRowManager.get(Ascension.getServer());
        if (mgr.hasItems(player.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    "§cClear your extra ender chest row before unequipping the End passive!"));
            return false;
        }
        return true;
    }
}
