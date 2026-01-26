package freq.ascension.api;

import java.util.ArrayList;
import java.util.List;

public class TaskScheduler {
    private final List<Task> tasks = new ArrayList<>();

    public void schedule(Task task) {
        tasks.add(task);
    }

    public void tick(long currentTick) {
        List<Task> toRemove = new ArrayList<>();
        List<Task> tasksCopy = new ArrayList<>(tasks);
        for (Task task : tasksCopy) {
            if (task.shouldRun(currentTick)) {
                try {
                    task.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                if (task.isFinished()) {
                    toRemove.add(task);
                }
            }
        }
        tasks.removeAll(toRemove);
    }
}
