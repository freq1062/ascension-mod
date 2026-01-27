package freq.ascension.managers;

public interface DisplayEffect {
    void tick();

    boolean isExpired();

    void discard();
}