package me.qmftm.quantumPhobia.medicine;

import me.qmftm.quantumPhobia.insanity.InsanityManager;
import me.qmftm.quantumPhobia.insanity.StatChange;
import me.qmftm.quantumPhobia.integration.QuantumAddersEffects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puts a player into overdose when they take too many doses within a short window, and
 * one stage deeper each time that happens again before the overdose wears off.
 */
public final class OverdoseTracker implements Listener {

    private static final Component PREFIX = Component.text("[QuantumPhobia] ", NamedTextColor.GRAY);

    private final JavaPlugin plugin;
    /** Null when QuantumAdders is absent; overdose then runs without its action-bar effect. */
    private final QuantumAddersEffects effects;
    private final OverdoseSymptoms symptoms;
    private final InsanityManager insanity;
    private final Map<UUID, Deque<Long>> doses = new HashMap<>();

    private final int doseCount;
    private final long windowMillis;
    private final List<OverdoseLevel> levels;
    /** Applied each time a player overdoses or goes a stage deeper. */
    private final StatChange onOverdose;

    public OverdoseTracker(JavaPlugin plugin, QuantumAddersEffects effects, OverdoseSymptoms symptoms,
                           InsanityManager insanity) {
        this.plugin = plugin;
        this.effects = effects;
        this.symptoms = symptoms;
        this.insanity = insanity;
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("overdose");
        this.doseCount = Math.max(1, config == null ? 3 : config.getInt("dose-count", 3));
        this.windowMillis = Math.max(1, config == null ? 20 : config.getInt("window-seconds", 20)) * 1000L;
        this.levels = OverdoseLevel.load(plugin);
        this.onOverdose = StatChange.from(config == null ? null : config.getConfigurationSection("on-overdose"),
                new StatChange(0, -20));
    }

    public boolean isOverdosed(Player player) {
        return symptoms.level(player) > 0;
    }

    /** Records one dose; returns whether it caused an overdose or deepened one. */
    public boolean recordDose(Player player) {
        long now = System.currentTimeMillis();
        Deque<Long> history = doses.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        history.addLast(now);
        while (!history.isEmpty() && now - history.peekFirst() > windowMillis) {
            history.removeFirst();
        }
        if (history.size() < doseCount) {
            return false;
        }

        // Start counting afresh, so the next stage takes another full run of doses.
        history.clear();

        int current = symptoms.level(player);
        int next = Math.min(current + 1, levels.size());
        OverdoseLevel level = levels.get(next - 1);

        if (effects != null) {
            if (current > 0) {
                effects.remove(player, levels.get(current - 1).effectId());
            }
            effects.apply(player, level.effectId(), level.durationTicks());
        } else {
            plugin.getLogger().warning("QuantumAdders가 없어 과다복용 상태효과를 표시하지 못했습니다.");
        }
        symptoms.begin(player, next, level);
        insanity.apply(player, onOverdose);

        String message = current == 0
                ? "약을 너무 많이 먹었습니다! 과다복용 상태가 되었습니다."
                : "과다복용이 심해졌습니다! (" + next + "단계)";
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.RED)));
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        doses.remove(event.getPlayer().getUniqueId());
    }
}
