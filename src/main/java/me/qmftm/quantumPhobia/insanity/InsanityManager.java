package me.qmftm.quantumPhobia.insanity;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A hidden 0-100 insanity value per player. Never shown to the player themselves.
 *
 * <p>Kept in the player's persistent data container, so it survives rejoins and restarts
 * with the rest of their player data.
 */
public final class InsanityManager implements PlayerStat, Listener {

    private final NamespacedKey key;
    private final WillpowerManager willpower;
    private final double rainMultiplier;
    private final double thunderMultiplier;
    private final double willpowerResistance;
    private final Map<UUID, Double> remainders = new HashMap<>();

    public InsanityManager(Plugin plugin, WillpowerManager willpower) {
        this.key = new NamespacedKey(plugin, "insanity");
        this.willpower = willpower;
        this.rainMultiplier = plugin.getConfig().getDouble("insanity.rain-multiplier", 1.5);
        this.thunderMultiplier = plugin.getConfig().getDouble("insanity.thunder-multiplier", 2.0);
        this.willpowerResistance = Math.clamp(plugin.getConfig().getDouble("willpower.resistance", 0.5), 0.0, 1.0);
    }

    @Override
    public String displayName() {
        return "정신병 수치";
    }

    @Override
    public int get(Player player) {
        return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, MIN);
    }

    @Override
    public int set(Player player, int value) {
        int clamped = Math.clamp(value, MIN, MAX);
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, clamped);
        return clamped;
    }

    /**
     * Pushes insanity up by {@code amount}, made worse by rain or thunder and softened by
     * willpower in proportion to how much is left. Returns the insanity value afterwards.
     */
    public int raise(Player player, double amount) {
        double shield = willpowerResistance * willpower.get(player) / (double) MAX;
        return change(player, amount * weatherMultiplier(player.getWorld()) * (1.0 - shield));
    }

    /** Brings insanity down by {@code amount}, as-is. */
    public int lower(Player player, double amount) {
        return change(player, -amount);
    }

    /** Applies a change to both stats: an increase in insanity goes through {@link #raise}. */
    public void apply(Player player, StatChange change) {
        if (change.insanity() > 0) {
            raise(player, change.insanity());
        } else if (change.insanity() < 0) {
            lower(player, -change.insanity());
        }
        if (change.willpower() != 0) {
            willpower.change(player, change.willpower());
        }
    }

    private int change(Player player, double delta) {
        int whole = Fractions.take(remainders, player.getUniqueId(), delta);
        return whole == 0 ? get(player) : add(player, whole);
    }

    /**
     * How much faster insanity rises in this world's weather. Thunder only happens during a
     * storm, so it replaces the rain multiplier rather than stacking.
     */
    public double weatherMultiplier(World world) {
        if (world.isThundering()) {
            return thunderMultiplier;
        }
        if (world.hasStorm()) {
            return rainMultiplier;
        }
        return 1.0;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remainders.remove(event.getPlayer().getUniqueId());
        willpower.forget(event.getPlayer());
    }
}
