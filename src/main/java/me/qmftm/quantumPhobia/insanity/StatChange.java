package me.qmftm.quantumPhobia.insanity;

import org.bukkit.configuration.ConfigurationSection;

/**
 * One nudge to both stats at once, as written in config.yml: {@code insanity: 10} raises
 * insanity, {@code willpower: -5} lowers willpower. Fractions are allowed.
 */
public record StatChange(double insanity, double willpower) {

    public static final StatChange NONE = new StatChange(0, 0);

    /** Reads {@code insanity} and {@code willpower} from the section, keeping the fallback's value for any left out. */
    public static StatChange from(ConfigurationSection section, StatChange fallback) {
        if (section == null) {
            return fallback;
        }
        return new StatChange(
                section.getDouble("insanity", fallback.insanity()),
                section.getDouble("willpower", fallback.willpower()));
    }

    public StatChange times(double factor) {
        return new StatChange(insanity * factor, willpower * factor);
    }
}
