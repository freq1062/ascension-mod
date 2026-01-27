package freq.ascension.orders;

import freq.ascension.managers.SpellStats;

public class SkyGod extends Sky {
    public static final SkyGod INSTANCE = new SkyGod();

    private SkyGod() {
        super();
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dash" -> new SpellStats(225, "Dash forward 12 blocks", 12);
            case "star_strike" -> new SpellStats(675,
                    "Summon a 2x2 beam of light that damages and launches entities",
                    true);
            default -> null;
        };
    }
}
