package freq.ascension.api;

public class RepeatedTask implements Task {

    private long executeInTicks;
    private final long period;
    private final Runnable action;
    private long lastExecutionTick;
    private long currentTickInternal;

    public RepeatedTask(long delay, long period, Runnable action) {
        this.executeInTicks = delay;
        this.period = period;
        this.action = action;
        this.lastExecutionTick = -period;
    }

    public boolean shouldRun(long currentTick) {
        this.currentTickInternal = currentTick;
        return currentTick >= executeInTicks && (currentTick - lastExecutionTick) >= period;
    }

    public void run() {
        action.run();
        lastExecutionTick = currentTickInternal;
    }

    @Override
    public boolean isFinished() {
        return false;
    }
}