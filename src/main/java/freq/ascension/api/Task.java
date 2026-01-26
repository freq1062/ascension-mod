package freq.ascension.api;

public interface Task {
    public boolean shouldRun(long currentTick);

    public void run();

    public default boolean isFinished() {
        return true;
    }
}
