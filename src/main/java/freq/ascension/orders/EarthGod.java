package com.ascension.orders;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.ascension.managers.SpellStats;

public class EarthGod extends Earth {
    public static final EarthGod INSTANCE = new EarthGod();

    private EarthGod() {
        super();
    }

    @Override
    public void applyEffect(Player player) {
        if (hasCapability(player, "passive"))
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.HASTE, 60, 1));
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Haste 2, Ore drops doubled, automatically smelted without silk touch, anvils cost 80% less";
            case "utility" -> "Supermine spell, 3x3";
            case "combat" -> "Magma bubble spell, launches you";
            default -> "";
        };
    }

    @Override
    public void onAnvilPrepare(PrepareAnvilEvent event, Player player) {
        if (!(event.getView() instanceof AnvilView view))
            return;

        int originalCost = view.getRepairCost();
        if (originalCost <= 0)
            return;

        int reducedCost = (int) Math.floor(originalCost * 0.2);
        view.setRepairCost(Math.max(1, reducedCost));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "supermine" -> new SpellStats(60,
                    "Activate to toggle 3x3 mining. Consumes normal durability.",
                    3, 1); // diameter, max durability loss
            case "magma_bubble" -> new SpellStats(600,
                    "Scorches enemy with magma spikes in a 4x4 centered area, dealing 4 hearts and launching you into the air. Must be activated on land or in lava.",
                    4, 40, true);
            default -> null;
        };
    }
}
