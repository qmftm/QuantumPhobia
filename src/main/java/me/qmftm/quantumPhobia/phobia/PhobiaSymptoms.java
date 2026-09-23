package me.qmftm.quantumPhobia.phobia;

import me.qmftm.quantumPhobia.insanity.InsanityManager;
import me.qmftm.quantumPhobia.medicine.MedicationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What a phobia does to a player whose medicine has worn off: for now, insanity rises
 * every second (see {@link InsanityManager#raise}).
 */
public final class PhobiaSymptoms implements Listener {

    private static final long PERIOD_TICKS = 20L;
    private static final Component PREFIX = Component.text("[QuantumPhobia] ", NamedTextColor.GRAY);

    private final JavaPlugin plugin;
    private final PhobiaManager phobias;
    private final MedicationManager medication;
    private final InsanityManager insanity;
    private final double insanityPerSecond;
    /** Who was protected on the last pass, so the moment the medicine wears off can be noticed. */
    private final Set<UUID> medicated = new HashSet<>();
    private BukkitTask task;

    public PhobiaSymptoms(JavaPlugin plugin, PhobiaManager phobias, MedicationManager medication, InsanityManager insanity) {
        this.plugin = plugin;
        this.phobias = phobias;
        this.medication = medication;
        this.insanity = insanity;
        this.insanityPerSecond = Math.max(0.0, plugin.getConfig().getDouble("phobia-symptoms.insanity-per-second", 1.0));
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (phobias.get(id).isEmpty()) {
                medicated.remove(id);
                continue;
            }

            if (medication.isMedicated(player)) {
                medicated.add(id);
                continue;
            }
            if (medicated.remove(id)) {
                player.sendMessage(PREFIX.append(Component.text("약 기운이 떨어졌습니다.", NamedTextColor.GRAY)));
            }

            insanity.raise(player, insanityPerSecond);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        medicated.remove(event.getPlayer().getUniqueId());
    }
}
