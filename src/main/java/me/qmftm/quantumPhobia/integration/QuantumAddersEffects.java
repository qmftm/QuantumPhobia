package me.qmftm.quantumPhobia.integration;

import me.qmftm.quantumAdders.QuantumAdders;
import me.qmftm.quantumAdders.effect.CustomEffect;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Applies QuantumAdders custom effects, which show on the action bar.
 *
 * <p>Links against QuantumAdders, so only construct this once that plugin is enabled.
 */
public final class QuantumAddersEffects {

    private final JavaPlugin plugin;
    private final QuantumAdders adders;
    /** Warn about each problem once, not every time an effect is applied. */
    private final Set<String> warned = new HashSet<>();

    public QuantumAddersEffects(JavaPlugin plugin, Plugin adders) {
        this.plugin = plugin;
        this.adders = (QuantumAdders) adders;
    }

    public void apply(Player player, String effectId, int durationTicks) {
        CustomEffect effect = adders.effectRegistry().get(effectId);
        if (effect == null) {
            if (warned.add(effectId)) {
                plugin.getLogger().warning("QuantumAdders에 커스텀 이펙트 '" + effectId + "'가 없습니다.");
            }
            return;
        }
        try {
            adders.activeEffects().apply(player, effect, durationTicks);
        } catch (NoSuchMethodError e) {
            warnOutdated();
        }
    }

    public void remove(Player player, String effectId) {
        try {
            adders.activeEffects().remove(player, effectId);
        } catch (NoSuchMethodError e) {
            warnOutdated();
        }
    }

    private void warnOutdated() {
        if (warned.add("<outdated>")) {
            plugin.getLogger().warning("설치된 QuantumAdders가 이펙트 API를 지원하지 않습니다. QuantumAdders를 업데이트하세요.");
        }
    }
}
