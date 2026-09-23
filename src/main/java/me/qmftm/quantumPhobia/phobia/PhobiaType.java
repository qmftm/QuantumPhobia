package me.qmftm.quantumPhobia.phobia;

import org.bukkit.Material;

import java.util.Optional;

public enum PhobiaType {
    SCHIZOPHRENIA("정신분열증", Material.ENDER_EYE);

    private final String displayName;
    private final Material icon;

    PhobiaType(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public static Optional<PhobiaType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (PhobiaType type : values()) {
            if (type.name().equalsIgnoreCase(id) || type.displayName.equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
