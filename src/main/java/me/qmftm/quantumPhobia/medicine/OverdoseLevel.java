package me.qmftm.quantumPhobia.medicine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** How hard one stage of overdose hits. Stage N is the Nth entry of overdose.levels in config.yml. */
public record OverdoseLevel(
        int durationTicks,
        String effectId,
        double insanityPerSecond,
        int heartbeatMinTicks,
        int heartbeatMaxTicks
) {

    private static final List<OverdoseLevel> DEFAULTS = List.of(
            new OverdoseLevel(60 * 20, "overdose_1", 2.0, 16, 60),
            new OverdoseLevel(90 * 20, "overdose_2", 3.0, 10, 36),
            new OverdoseLevel(120 * 20, "overdose_3", 5.0, 5, 20));

    public static List<OverdoseLevel> load(JavaPlugin plugin) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("overdose");
        if (config == null || config.getMapList("levels").isEmpty()) {
            return DEFAULTS;
        }
        List<OverdoseLevel> levels = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("levels")) {
            int beatMin = Math.max(1, (int) number(raw, "heartbeat-min-ticks", 16));
            levels.add(new OverdoseLevel(
                    Math.max(1, (int) number(raw, "duration-seconds", 60)) * 20,
                    String.valueOf(raw.containsKey("effect") ? raw.get("effect") : "overdose_" + (levels.size() + 1)),
                    Math.max(0.0, number(raw, "insanity-per-second", 2.0)),
                    beatMin,
                    Math.max(beatMin, (int) number(raw, "heartbeat-max-ticks", 60))));
        }
        return List.copyOf(levels);
    }

    private static double number(Map<?, ?> map, String key, double fallback) {
        return map.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }
}
