package freq.ascension.api;

import java.util.function.Consumer;

public class RepeatedTask implements Task {

    private final long executeInTicks;
    private final long period;
    private final Consumer<RepeatedTask> action;
    private long currentTickInternal;
    private boolean cancelled;
    private Runnable onFinish;

    public RepeatedTask(long executeInTicks, long period, Consumer<RepeatedTask> action) {
        this.executeInTicks = executeInTicks;
        this.currentTickInternal = 0;
        this.period = period;
        this.action = action;
        this.cancelled = false;
    }

    public void setOnFinish(Runnable onFinish) {
        this.onFinish = onFinish;
    }

    public boolean shouldRun(long currentTick) {
        this.currentTickInternal++;
        // Use <= so it actually runs on the final tick
        boolean inRange = this.currentTickInternal >= this.executeInTicks &&
                this.currentTickInternal <= (this.executeInTicks + this.period);

        // Auto-cancel if we've exceeded the duration
        if (this.currentTickInternal > (this.executeInTicks + this.period)) {
            this.cancelled = true;
            return false;
        }

        return inRange && !cancelled;
    }

    // Return ticks since the task started, excluding delay
    public long getTick() {
        return Math.max(0, this.currentTickInternal - this.executeInTicks);
    }

    public void run() {
        action.accept(this);
    }

    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public boolean isFinished() {
        if (this.currentTickInternal >= this.executeInTicks + this.period || this.cancelled) {
            if (onFinish != null) {
                onFinish.run();
                onFinish = null; // Ensure it only runs once
            }
            return true;
        }
        return false;
    }
}