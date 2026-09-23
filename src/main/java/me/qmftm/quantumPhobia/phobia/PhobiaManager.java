package me.qmftm.quantumPhobia.phobia;

import me.qmftm.quantumPhobia.QuantumPhobia;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class PhobiaManager {

    private final QuantumPhobia plugin;
    private final File dataFile;
    private final Map<UUID, ActivePhobia> active = new HashMap<>();
    private BukkitTask expiryTask;

    public PhobiaManager(QuantumPhobia plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "phobias.yml");
    }

    public void load() {
        active.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        long now = System.currentTimeMillis();
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                PhobiaType type = PhobiaType.fromId(config.getString(key + ".type")).orElse(null);
                long expiresAt = config.getLong(key + ".expiresAt");
                if (type == null || expiresAt <= now) {
                    continue;
                }
                active.put(uuid, new ActivePhobia(type, expiresAt));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "phobias.yml의 잘못된 항목을 건너뜁니다: " + key, e);
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, ActivePhobia> entry : active.entrySet()) {
            String path = entry.getKey().toString();
            config.set(path + ".type", entry.getValue().type().name());
            config.set(path + ".expiresAt", entry.getValue().expiresAt());
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "phobias.yml 저장에 실패했습니다.", e);
        }
    }

    public void startExpiryTask() {
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::purgeExpired, 20L, 20L);
    }

    public void stopExpiryTask() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    private void purgeExpired() {
        boolean changed = false;
        Iterator<Map.Entry<UUID, ActivePhobia>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActivePhobia> entry = iterator.next();
            if (!entry.getValue().isExpired()) {
                continue;
            }
            iterator.remove();
            changed = true;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.sendMessage(Component.text("[QuantumPhobia] ", NamedTextColor.GRAY)
                        .append(Component.text("공포증이 사라졌습니다.", NamedTextColor.WHITE)));
            }
        }
        if (changed) {
            save();
        }
    }

    public void set(UUID uuid, PhobiaType type, long durationMillis) {
        active.put(uuid, new ActivePhobia(type, System.currentTimeMillis() + durationMillis));
        save();
    }

    public void setPermanent(UUID uuid, PhobiaType type) {
        active.put(uuid, new ActivePhobia(type, ActivePhobia.PERMANENT));
        save();
    }

    public boolean remove(UUID uuid) {
        boolean removed = active.remove(uuid) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Optional<ActivePhobia> get(UUID uuid) {
        ActivePhobia phobia = active.get(uuid);
        if (phobia == null || phobia.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(phobia);
    }
}
