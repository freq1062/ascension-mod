package freq.ascension.api;

import java.util.function.Consumer;

public class RepeatedTask implements Task {

    private long executeInTicks;
    private final long period;
    private final Consumer<RepeatedTask> action;
    private long lastExecutionTick;
    private long currentTickInternal;
    private boolean cancelled;

    public RepeatedTask(long delay, long period, Consumer<RepeatedTask> action) {
        this.executeInTicks = delay;
        this.period = period;
        this.action = action;
        this.lastExecutionTick = -period;
        this.cancelled = false;
    }

    public boolean shouldRun(long currentTick) {
        this.currentTickInternal = currentTick;
        return currentTick >= executeInTicks && (currentTick - lastExecutionTick) >= period;
    }

    public void run() {
        action.accept(this);
        lastExecutionTick = currentTickInternal;
    }

    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public boolean isFinished() {
        return this.cancelled;
    }
}