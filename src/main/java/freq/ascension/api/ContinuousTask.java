package freq.ascension.api;

public class ContinuousTask implements Task {

    private final Runnable action;
    private final long delayTicks;
    private long nextExecutionTick;
    private boolean stopped;

    public ContinuousTask(long delayTicks, Runnable action) {
        this.delayTicks = delayTicks;
        this.action = action;
        this.nextExecutionTick = delayTicks;
        this.stopped = false;
    }

    public boolean shouldRun(long currentTick) {
        return !stopped && currentTick >= nextExecutionTick;
    }

    public void run() {
        if (!stopped) {
            action.run();
            nextExecutionTick += delayTicks;
        }
    }

    public void stop() {
        this.stopped = true;
    }
}