package freq.ascension.managers;

public record SpellStats(
        int cooldown,
        String description,
        Object... extra) {
    // Helper to get extra values safely
    public int getInt(int index) {
        return (int) extra[index];
    }

    public boolean getBool(int index) {
        return (boolean) extra[index];
    }

    public int getCooldownTicks() {
        return cooldown;
    }

    public String getDescription() {
        return description;
    }

    public int getCooldownSecs() {
        return (int) cooldown / 20;
    }
}
