package me.qmftm.quantumPhobia.stress;

import io.papermc.paper.event.player.PlayerDeepSleepEvent;
import me.qmftm.quantumPhobia.insanity.InsanityManager;
import me.qmftm.quantumPhobia.insanity.StatChange;
import me.qmftm.quantumPhobia.insanity.WillpowerManager;
import me.qmftm.quantumPhobia.medicine.MedicationManager;
import me.qmftm.quantumPhobia.medicine.OverdoseSymptoms;
import me.qmftm.quantumPhobia.phobia.PhobiaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everyday stress and recovery, outside of medicine and overdose.
 *
 * <p>Once-off: getting hurt costs a little of both stats; a real night's sleep restores a
 * lot. Every second: going without sleep, being in danger, and letting willpower stay low
 * or insanity stay high all wear the player down, while resting somewhere safe, or simply
 * going a while without stress, lets them recover.
 */
public final class StressMonitor implements Listener {

    private static final long PERIOD_TICKS = 20L;
    private static final Component PREFIX = Component.text("[QuantumPhobia] ", NamedTextColor.GRAY);
    /** Moving less than this per second still counts as keeping still. */
    private static final double STILL_DISTANCE_SQUARED = 0.2 * 0.2;

    /** Timestamps of what the player has been doing, all in milliseconds. */
    private static final class Tracker {
        Location lastLocation;
        long stillSince;
        long lastDamage;
        long dangerSince = -1;
        long lowWillpowerSince = -1;
        long highInsanitySince = -1;
        long calmSince;
    }

    private final JavaPlugin plugin;
    private final InsanityManager insanity;
    private final WillpowerManager willpower;
    private final PhobiaManager phobias;
    private final MedicationManager medication;
    private final OverdoseSymptoms overdose;
    private final Map<UUID, Tracker> trackers = new HashMap<>();
    private BukkitTask task;

    private final StatChange damage;
    private final StatChange sleep;

    private final int sleepDeprivedAfterTicks;
    private final StatChange sleepDeprivation;

    private final int lowWillpowerBelow;
    private final long lowWillpowerAfter;
    private final StatChange lowWillpower;

    private final int highInsanityAbove;
    private final long highInsanityAfter;
    private final StatChange highInsanity;

    private final double dangerRadius;
    private final double dangerHealth;
    private final long dangerAfter;
    private final StatChange danger;

    private final long restStillFor;
    private final double restSafeRadius;
    private final int restMinLight;
    private final long restNoDamageFor;
    private final StatChange rest;

    private final long calmAfter;
    private final StatChange calm;

    public StressMonitor(JavaPlugin plugin, InsanityManager insanity, WillpowerManager willpower,
                         PhobiaManager phobias, MedicationManager medication, OverdoseSymptoms overdose) {
        this.plugin = plugin;
        this.insanity = insanity;
        this.willpower = willpower;
        this.phobias = phobias;
        this.medication = medication;
        this.overdose = overdose;

        ConfigurationSection config = plugin.getConfig().getConfigurationSection("stress");
        ConfigurationSection s;

        damage = StatChange.from(section(config, "damage"), new StatChange(1, -1));
        sleep = StatChange.from(section(config, "sleep"), new StatChange(-30, 40));

        s = section(config, "sleep-deprivation");
        sleepDeprivedAfterTicks = s == null ? 72000 : s.getInt("after-ticks", 72000);
        sleepDeprivation = StatChange.from(s, new StatChange(0.05, -0.05));

        s = section(config, "low-willpower");
        lowWillpowerBelow = s == null ? 30 : s.getInt("below", 30);
        lowWillpowerAfter = seconds(s, "after-seconds", 60);
        lowWillpower = StatChange.from(s, new StatChange(0.1, 0));

        s = section(config, "high-insanity");
        highInsanityAbove = s == null ? 70 : s.getInt("above", 70);
        highInsanityAfter = seconds(s, "after-seconds", 60);
        highInsanity = StatChange.from(s, new StatChange(0, -0.1));

        s = section(config, "danger");
        dangerRadius = s == null ? 8 : s.getDouble("hostile-radius", 8);
        dangerHealth = s == null ? 6 : s.getDouble("low-health", 6);
        dangerAfter = seconds(s, "after-seconds", 10);
        danger = StatChange.from(s, new StatChange(0, -0.2));

        s = section(config, "safe-rest");
        restStillFor = seconds(s, "still-seconds", 5);
        restSafeRadius = s == null ? 12 : s.getDouble("hostile-radius", 12);
        restMinLight = s == null ? 8 : s.getInt("min-light", 8);
        restNoDamageFor = seconds(s, "no-damage-seconds", 10);
        rest = StatChange.from(s, new StatChange(-0.1, 0.2));

        s = section(config, "calm");
        calmAfter = seconds(s, "after-seconds", 60);
        calm = StatChange.from(s, new StatChange(-0.02, 0));
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Tracker tracker = tracker(player, now);
        tracker.lastDamage = now;
        tracker.calmSince = now;
        insanity.apply(player, damage);
    }

    /** Fires once a player has actually fallen asleep in bed, not just lain down. */
    @EventHandler(ignoreCancelled = true)
    public void onSleep(PlayerDeepSleepEvent event) {
        Player player = event.getPlayer();
        insanity.apply(player, sleep);
        player.sendMessage(PREFIX.append(Component.text("잠이 들자 머리가 맑아집니다.", NamedTextColor.WHITE)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        trackers.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Tracker tracker = tracker(player, now);
            updateStillness(player, tracker, now);

            boolean overdosed = overdose.level(player) > 0;
            boolean sleepDeprived = player.getStatistic(Statistic.TIME_SINCE_REST) >= sleepDeprivedAfterTicks;
            boolean inDanger = player.getHealth() <= dangerHealth || hostileNearby(player, dangerRadius);
            // A phobia only stresses the player once their medicine has worn off.
            boolean suffering = phobias.get(player.getUniqueId()).isPresent() && !medication.isMedicated(player);

            if (sleepDeprived) {
                insanity.apply(player, sleepDeprivation);
            }

            tracker.dangerSince = since(tracker.dangerSince, inDanger, now);
            if (inDanger && now - tracker.dangerSince >= dangerAfter) {
                insanity.apply(player, danger);
            }

            tracker.lowWillpowerSince = since(tracker.lowWillpowerSince, willpower.get(player) <= lowWillpowerBelow, now);
            if (tracker.lowWillpowerSince >= 0 && now - tracker.lowWillpowerSince >= lowWillpowerAfter) {
                insanity.apply(player, lowWillpower);
            }

            tracker.highInsanitySince = since(tracker.highInsanitySince, insanity.get(player) >= highInsanityAbove, now);
            if (tracker.highInsanitySince >= 0 && now - tracker.highInsanitySince >= highInsanityAfter) {
                insanity.apply(player, highInsanity);
            }

            if (isRestingSafely(player, tracker, now, overdosed)) {
                insanity.apply(player, rest);
            }

            if (overdosed || sleepDeprived || inDanger || suffering) {
                tracker.calmSince = now;
            } else if (now - tracker.calmSince >= calmAfter) {
                insanity.apply(player, calm);
            }
        }
    }

    private boolean isRestingSafely(Player player, Tracker tracker, long now, boolean overdosed) {
        boolean resting = player.isInsideVehicle() || now - tracker.stillSince >= restStillFor;
        return resting
                && !overdosed
                && now - tracker.lastDamage >= restNoDamageFor
                && player.getEyeLocation().getBlock().getLightLevel() >= restMinLight
                && !hostileNearby(player, restSafeRadius);
    }

    private void updateStillness(Player player, Tracker tracker, long now) {
        Location current = player.getLocation();
        Location last = tracker.lastLocation;
        if (last == null || !last.getWorld().equals(current.getWorld())
                || horizontalDistanceSquared(last, current) > STILL_DISTANCE_SQUARED) {
            tracker.stillSince = now;
        }
        tracker.lastLocation = current;
    }

    /** Hallucinated phantoms are only in the player's head; they don't count as a threat. */
    private boolean hostileNearby(Player player, double radius) {
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Enemy && !overdose.isHallucination(entity)) {
                return true;
            }
        }
        return false;
    }

    private Tracker tracker(Player player, long now) {
        return trackers.computeIfAbsent(player.getUniqueId(), k -> {
            Tracker fresh = new Tracker();
            fresh.stillSince = now;
            fresh.calmSince = now;
            fresh.lastDamage = 0;
            return fresh;
        });
    }

    /** Keeps the time a condition started while it holds, and clears it (to -1) once it stops. */
    private static long since(long current, boolean holds, long now) {
        if (!holds) {
            return -1;
        }
        return current >= 0 ? current : now;
    }

    private static double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static ConfigurationSection section(ConfigurationSection parent, String name) {
        return parent == null ? null : parent.getConfigurationSection(name);
    }

    private static long seconds(ConfigurationSection section, String key, int fallback) {
        return (section == null ? fallback : section.getInt(key, fallback)) * 1000L;
    }
}
