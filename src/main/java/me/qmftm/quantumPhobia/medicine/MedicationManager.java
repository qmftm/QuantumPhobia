package me.qmftm.quantumPhobia.medicine;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Whether a player's medicine is still working. Each dose holds phobia symptoms off for a
 * while; taking another restarts that window rather than adding to it, so hoarding doses
 * buys nothing and the medicine has to be taken regularly.
 *
 * <p>Both the expiry and the last dose time are kept in the player's persistent data, so
 * they survive rejoins and restarts.
 */
public final class MedicationManager {

    public enum Dose {
        /** Taken at a sensible interval after the last dose. */
        NORMAL,
        /** Taken again before the minimum interval had passed. */
        TOO_SOON
    }

    private final NamespacedKey untilKey;
    private final NamespacedKey lastDoseKey;
    private final long protectionMillis;
    private final long minIntervalMillis;

    public MedicationManager(JavaPlugin plugin) {
        this.untilKey = new NamespacedKey(plugin, "medicated_until");
        this.lastDoseKey = new NamespacedKey(plugin, "last_dose");
        this.protectionMillis = Math.max(1, plugin.getConfig().getInt("medication.protection-seconds", 300)) * 1000L;
        this.minIntervalMillis = Math.max(0, plugin.getConfig().getInt("medication.min-interval-seconds", 60)) * 1000L;
    }

    /** Records a dose and says whether it came too soon after the previous one. */
    public Dose take(Player player) {
        long now = System.currentTimeMillis();
        long lastDose = player.getPersistentDataContainer().getOrDefault(lastDoseKey, PersistentDataType.LONG, 0L);
        player.getPersistentDataContainer().set(lastDoseKey, PersistentDataType.LONG, now);
        player.getPersistentDataContainer().set(untilKey, PersistentDataType.LONG, now + protectionMillis);
        return now - lastDose < minIntervalMillis ? Dose.TOO_SOON : Dose.NORMAL;
    }

    public boolean isMedicated(Player player) {
        long until = player.getPersistentDataContainer().getOrDefault(untilKey, PersistentDataType.LONG, 0L);
        return System.currentTimeMillis() < until;
    }
}
