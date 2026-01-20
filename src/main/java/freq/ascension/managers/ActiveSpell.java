package freq.ascension.managers;

import net.minecraft.server.level.ServerPlayer;

public class ActiveSpell {
    private final ServerPlayer player;
    private final Spell spell;
    private int remainingCooldown;
    private boolean inUse;

    public ActiveSpell(ServerPlayer player, Spell spell, int cooldownTicks) {
        this.player = player;
        this.spell = spell;
        this.remainingCooldown = cooldownTicks;
        this.inUse = false;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Spell getSpell() {
        return spell;
    }

    public int getRemainingCooldown() {
        return remainingCooldown;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public void decrementCooldown() {
        if (remainingCooldown > 0)
            remainingCooldown--;
    }

    public boolean isCooldownFinished() {
        return remainingCooldown <= 0;
    }
}
