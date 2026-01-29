package freq.ascension.api;

public class DelayedTask implements Task {
    private long ticksRemaining;
    private final Runnable action;
    private boolean done = false;

    public DelayedTask(long delay, Runnable action) {
        this.ticksRemaining = delay;
        this.action = action;
    }

    public boolean shouldRun(long currentTick) {
        return !done;
    }

    public void run() {
        ticksRemaining--;
        if (ticksRemaining <= 0) {
            action.run();
            done = true;
        }
    }

    public boolean isFinished() {
        return done;
    }
}