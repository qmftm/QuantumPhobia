package me.qmftm.quantumPhobia.insanity;

import java.util.Map;
import java.util.UUID;

/** The stats are stored whole, so small per-second changes pile up here until they add to one. */
final class Fractions {

    private Fractions() {
    }

    /** Adds {@code delta} to the player's carried fraction and returns the whole part to apply now. */
    static int take(Map<UUID, Double> remainders, UUID id, double delta) {
        double total = remainders.getOrDefault(id, 0.0) + delta;
        int whole = (int) total; // truncates toward zero, so losses carry just like gains
        remainders.put(id, total - whole);
        return whole;
    }
}
