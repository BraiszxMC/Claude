package com.braiszx.bbranked.menu;

import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.queue.QueueManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Clicks del menu de /ranked menu.
 */
public final class MenuListener implements Listener {

    private final QueueManager queues;

    public MenuListener(QueueManager queues) {
        this.queues = queues;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof RankedMenu menu)) {
            return;
        }

        // Es un menu, no un cofre: nada se puede coger.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Los clicks en el inventario propio del jugador se ignoran.
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof RankedMenu)) {
            return;
        }

        int slot = event.getSlot();

        if (menu.isLeaveSlot(slot)) {
            player.closeInventory();
            queues.leave(player, true);
            return;
        }

        QueueMode mode = menu.modeAt(slot);
        if (mode != null) {
            player.closeInventory();
            queues.join(player, mode);
        }
    }
}
