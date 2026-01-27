package freq.ascension.managers;

import java.util.ArrayList;
import java.util.List;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;

public class EffectManager {
    private static final List<DisplayEffect> ACTIVE_EFFECTS = new ArrayList<>();
    private static final List<DisplayEffect> PENDING_ADD = new ArrayList<>();

    public static void init() {
        Ascension.scheduler.schedule(new ContinuousTask(1, () -> {
            ACTIVE_EFFECTS.addAll(PENDING_ADD);
            PENDING_ADD.clear();

            ACTIVE_EFFECTS.removeIf(effect -> {
                if (effect.isExpired()) {
                    effect.discard();
                    return true;
                }
                effect.tick();
                return false;
            });
        }));
    }

    public static void spawn(DisplayEffect effect) {
        PENDING_ADD.add(effect);
    }
}