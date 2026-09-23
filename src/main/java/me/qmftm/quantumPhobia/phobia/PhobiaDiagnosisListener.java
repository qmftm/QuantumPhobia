package me.qmftm.quantumPhobia.phobia;

import me.qmftm.quantumPhobia.integration.QuantumAddersItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/** Tells the user which phobia they have when they right-click the QuantumAdders diagnosis kit. */
public final class PhobiaDiagnosisListener implements Listener {

    /** The key QuantumAdders' ItemFactory stamps its item id under. */
    private static final NamespacedKey ADDERS_ITEM_ID = new NamespacedKey("quantumadders", "item_id");
    private static final Component PREFIX = Component.text("[QuantumPhobia] ", NamedTextColor.GRAY);

    private final PhobiaManager manager;

    public PhobiaDiagnosisListener(PhobiaManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        String id = item.getPersistentDataContainer().get(ADDERS_ITEM_ID, PersistentDataType.STRING);
        if (!QuantumAddersItems.DIAGNOSIS_KIT_ITEM_ID.equals(id)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract();
        }

        Optional<ActivePhobia> phobia = manager.get(player.getUniqueId());
        if (phobia.isEmpty()) {
            player.sendMessage(PREFIX.append(Component.text("진단 결과: 앓고 있는 공포증이 없습니다.", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(PREFIX.append(Component.text("진단 결과: ", NamedTextColor.WHITE))
                    .append(Component.text(phobia.get().type().displayName(), NamedTextColor.RED)));
        }
    }
}
