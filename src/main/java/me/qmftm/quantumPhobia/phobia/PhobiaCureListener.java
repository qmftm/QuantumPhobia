package me.qmftm.quantumPhobia.phobia;

import me.qmftm.quantumPhobia.insanity.InsanityManager;
import me.qmftm.quantumPhobia.insanity.StatChange;
import me.qmftm.quantumPhobia.integration.QuantumAddersItems;
import me.qmftm.quantumPhobia.medicine.MedicationManager;
import me.qmftm.quantumPhobia.medicine.OverdoseTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Taking the QuantumAdders medicine item. It doesn't cure the phobia, only holds its
 * symptoms off for a while. Taken at a sensible interval it steadies the mind; taken too
 * soon, or while already overdosed, it does the opposite, and too many doses too fast
 * count toward an overdose.
 */
public final class PhobiaCureListener implements Listener {

    /** The key QuantumAdders' ItemFactory stamps its item id under. */
    private static final NamespacedKey ADDERS_ITEM_ID = new NamespacedKey("quantumadders", "item_id");
    private static final Component PREFIX = Component.text("[QuantumPhobia] ", NamedTextColor.GRAY);

    private final MedicationManager medication;
    private final OverdoseTracker overdose;
    private final InsanityManager insanity;
    private final StatChange normalDose;
    private final StatChange tooSoon;
    private final StatChange duringOverdose;

    public PhobiaCureListener(JavaPlugin plugin, MedicationManager medication, OverdoseTracker overdose,
                              InsanityManager insanity) {
        this.medication = medication;
        this.overdose = overdose;
        this.insanity = insanity;
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("medication");
        this.normalDose = StatChange.from(config == null ? null : config.getConfigurationSection("normal-dose"),
                new StatChange(-10, 5));
        this.tooSoon = StatChange.from(config == null ? null : config.getConfigurationSection("too-soon"),
                new StatChange(10, 0));
        this.duringOverdose = StatChange.from(config == null ? null : config.getConfigurationSection("during-overdose"),
                new StatChange(15, 0));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        String id = event.getItem().getPersistentDataContainer().get(ADDERS_ITEM_ID, PersistentDataType.STRING);
        if (!QuantumAddersItems.CURE_ITEM_ID.equals(id)) {
            return;
        }

        Player player = event.getPlayer();
        // Checked before this dose is counted, since the dose itself may be what tips them over.
        boolean alreadyOverdosed = overdose.isOverdosed(player);
        MedicationManager.Dose dose = medication.take(player);

        if (alreadyOverdosed) {
            insanity.apply(player, duringOverdose);
            player.sendMessage(PREFIX.append(Component.text("약을 복용했습니다. 머리가 깨질 것 같습니다.", NamedTextColor.RED)));
        } else if (dose == MedicationManager.Dose.TOO_SOON) {
            insanity.apply(player, tooSoon);
            player.sendMessage(PREFIX.append(Component.text("약을 복용했습니다. 너무 자주 먹은 것 같습니다.", NamedTextColor.GOLD)));
        } else {
            insanity.apply(player, normalDose);
            player.sendMessage(PREFIX.append(Component.text("약을 복용했습니다.", NamedTextColor.WHITE)));
        }
        overdose.recordDose(player);
    }
}
