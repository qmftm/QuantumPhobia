package me.qmftm.quantumPhobia.insanity;

import org.bukkit.entity.Player;

/** A 0-100 number kept per player, which admins can inspect and change by command. */
public interface PlayerStat {

    int MIN = 0;
    int MAX = 100;

    String displayName();

    int get(Player player);

    /** Stores the value clamped to 0-100 and returns what was stored. */
    int set(Player player, int value);

    /** Adds (or, if negative, subtracts) and returns the new clamped value. */
    default int add(Player player, int amount) {
        return set(player, get(player) + amount);
    }
}
