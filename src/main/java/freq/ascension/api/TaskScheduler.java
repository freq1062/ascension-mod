package freq.ascension.api;

import java.util.ArrayList;
import java.util.List;

public class TaskScheduler {
    private final List<Task> activeTasks = new ArrayList<>();
    private final List<Task> pendingAdd = new ArrayList<>();

    public void schedule(Task task) {
        synchronized (pendingAdd) {
            pendingAdd.add(task);
        }
    }

    public void tick(long currentTick) {
        synchronized (pendingAdd) {
            if (!pendingAdd.isEmpty()) {
                activeTasks.addAll(pendingAdd);
                pendingAdd.clear();
            }
        }
        for (int i = activeTasks.size() - 1; i >= 0; i--) {
            Task task = activeTasks.get(i);
            try {
                if (task.shouldRun(currentTick)) {
                    task.run();
                    if (task.isFinished()) {
                        activeTasks.remove(i);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                activeTasks.remove(i);
            }
        }
    }
}