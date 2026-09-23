package me.qmftm.quantumPhobia.integration;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Drops this plugin's item definitions into QuantumAdders' folders.
 *
 * <p>QuantumAdders has no API for other plugins to register items; it only reads
 * items/*.yml, effects/*.yml and textures/*.png. Run from onLoad so the files are in place before
 * QuantumAdders enables and reads them.
 */
public final class QuantumAddersItems {

    public static final String CURE_ITEM_ID = "phobia_cure";
    public static final String DIAGNOSIS_KIT_ITEM_ID = "phobia_diagnosis_kit";
    public static final String SEDATIVE_ITEM_ID = "sedative";

    private QuantumAddersItems() {
    }

    public static void install(JavaPlugin plugin) {
        File addersFolder = new File(plugin.getDataFolder().getParentFile(), "QuantumAdders");
        copyIfAbsent(plugin, "quantumadders/quantumphobia.yml", new File(addersFolder, "items/quantumphobia.yml"));
        copyIfAbsent(plugin, "quantumadders/phobia_cure.png", new File(addersFolder, "textures/phobia_cure.png"));
        copyIfAbsent(plugin, "quantumadders/quantumphobia-diagnosis.yml", new File(addersFolder, "items/quantumphobia-diagnosis.yml"));
        copyIfAbsent(plugin, "quantumadders/phobia_diagnosis_kit.png", new File(addersFolder, "textures/phobia_diagnosis_kit.png"));
        copyIfAbsent(plugin, "quantumadders/quantumphobia-sedative.yml", new File(addersFolder, "items/quantumphobia-sedative.yml"));
        copyIfAbsent(plugin, "quantumadders/sedative.png", new File(addersFolder, "textures/sedative.png"));
        copyIfAbsent(plugin, "quantumadders/quantumphobia-effects.yml", new File(addersFolder, "effects/quantumphobia.yml"));
    }

    /** Existing files are left alone so server owners can edit the name, lore or texture. */
    private static void copyIfAbsent(JavaPlugin plugin, String resource, File target) {
        if (target.exists()) {
            return;
        }
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                plugin.getLogger().warning("jar에 " + resource + " 리소스가 없습니다.");
                return;
            }
            Files.createDirectories(target.getParentFile().toPath());
            Files.copy(in, target.toPath());
            plugin.getLogger().info("QuantumAdders에 " + target.getName() + "을(를) 추가했습니다.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, target.getPath() + " 생성에 실패했습니다.", e);
        }
    }
}
