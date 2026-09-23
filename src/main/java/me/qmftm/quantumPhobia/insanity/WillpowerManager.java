package me.qmftm.quantumPhobia.insanity;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A 0-100 willpower value per player, starting full. It shields against insanity (see
 * {@link InsanityManager#raise}). Unlike insanity, the player gets to see it.
 *
 * <p>Kept in the player's persistent data container, so it survives rejoins and restarts.
 */
public final class WillpowerManager implements PlayerStat {

    private final NamespacedKey key;
    /** When each player's value last changed, so the status bar can show it for a moment. */
    private final Map<UUID, Long> lastChanged = new HashMap<>();
    private final Map<UUID, Double> remainders = new HashMap<>();

    public WillpowerManager(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "willpower");
    }

    @Override
    public String displayName() {
        return "정신력";
    }

    @Override
    public int get(Player player) {
        return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, MAX);
    }

    @Override
    public int set(Player player, int value) {
        int clamped = Math.clamp(value, MIN, MAX);
        if (clamped != get(player)) {
            lastChanged.put(player.getUniqueId(), System.currentTimeMillis());
        }
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, clamped);
        return clamped;
    }

    /** Changes by a possibly fractional amount; the fraction carries over to the next call. */
    public int change(Player player, double delta) {
        int whole = Fractions.take(remainders, player.getUniqueId(), delta);
        return whole == 0 ? get(player) : add(player, whole);
    }

    /** Whether the value changed within the last {@code millis} milliseconds. */
    public boolean changedWithin(Player player, long millis) {
        Long at = lastChanged.get(player.getUniqueId());
        return at != null && System.currentTimeMillis() - at <= millis;
    }

    public void forget(Player player) {
        lastChanged.remove(player.getUniqueId());
        remainders.remove(player.getUniqueId());
    }
}
