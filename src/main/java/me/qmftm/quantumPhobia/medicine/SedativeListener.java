package me.qmftm.quantumPhobia.medicine;

import me.qmftm.quantumPhobia.insanity.InsanityManager;
import me.qmftm.quantumPhobia.insanity.StatChange;
import me.qmftm.quantumPhobia.integration.QuantumAddersItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** The recovery item: calms insanity and restores some willpower. Doesn't count toward overdose. */
public final class SedativeListener implements Listener {

    /** The key QuantumAdders' ItemFactory stamps its item id under. */
    private static final NamespacedKey ADDERS_ITEM_ID = new NamespacedKey("quantumadders", "item_id");
    private static final Component PREFIX = Component.text("[QuantumPhobia] ", NamedTextColor.GRAY);

    private final InsanityManager insanity;
    private final StatChange effect;

    public SedativeListener(JavaPlugin plugin, InsanityManager insanity) {
        this.insanity = insanity;
        this.effect = StatChange.from(plugin.getConfig().getConfigurationSection("sedative"), new StatChange(-15, 10));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        String id = event.getItem().getPersistentDataContainer().get(ADDERS_ITEM_ID, PersistentDataType.STRING);
        if (!QuantumAddersItems.SEDATIVE_ITEM_ID.equals(id)) {
            return;
        }
        insanity.apply(event.getPlayer(), effect);
        event.getPlayer().sendMessage(PREFIX.append(Component.text("마음이 조금 가라앉습니다.", NamedTextColor.WHITE)));
    }
}
