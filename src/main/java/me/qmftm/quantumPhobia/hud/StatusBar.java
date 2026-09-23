package me.qmftm.quantumPhobia.hud;

import me.qmftm.quantumPhobia.insanity.PlayerStat;
import me.qmftm.quantumPhobia.insanity.WillpowerManager;
import me.qmftm.quantumPhobia.medicine.OverdoseSymptoms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The one action-bar line this plugin owns: the overdose stage while it lasts, and
 * willpower for a few seconds after it changes. Drawn together because two sources
 * writing the action bar separately just overwrite each other and flicker.
 */
public final class StatusBar {

    private static final long PERIOD_TICKS = 10L;
    private static final long WILLPOWER_SHOW_MILLIS = 3000L;
    private static final int BAR_SEGMENTS = 10;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final Component SEPARATOR = Component.text("  |  ", NamedTextColor.DARK_GRAY);

    private final JavaPlugin plugin;
    private final OverdoseSymptoms overdose;
    private final WillpowerManager willpower;
    private BukkitTask task;

    public StatusBar(JavaPlugin plugin, OverdoseSymptoms overdose, WillpowerManager willpower) {
        this.plugin = plugin;
        this.overdose = overdose;
        this.willpower = willpower;
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
            List<Component> parts = new ArrayList<>();

            int level = overdose.level(player);
            if (level > 0) {
                String stage = level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
                parts.add(Component.text("과다복용 " + stage, level >= 3 ? NamedTextColor.DARK_RED : NamedTextColor.RED)
                        .append(Component.text(" (" + overdose.remainingSeconds(player) + "s)", NamedTextColor.GRAY)));
            }
            if (willpower.changedWithin(player, WILLPOWER_SHOW_MILLIS)) {
                parts.add(willpowerBar(willpower.get(player)));
            }

            // Leave the action bar alone when there's nothing to say, so other plugins can use it.
            if (!parts.isEmpty()) {
                player.sendActionBar(Component.join(JoinConfiguration.separator(SEPARATOR), parts));
            }
        }
    }

    private static Component willpowerBar(int value) {
        int filled = Math.round(value / (float) PlayerStat.MAX * BAR_SEGMENTS);
        return Component.text("정신력 ", NamedTextColor.AQUA)
                .append(Component.text("|".repeat(filled), NamedTextColor.AQUA))
                .append(Component.text("|".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + value, NamedTextColor.WHITE));
    }
}
