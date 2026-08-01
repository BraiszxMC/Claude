package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import com.moderacionx.invsee.VistaDetallada;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Protege las vistas de inventario abiertas con /invsee. */
public final class ListenerInventario implements Listener {

    private final ModeracionX plugin;

    public ListenerInventario(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alHacerClic(InventoryClickEvent evento) {
        if (bloquear(evento.getView().getTopInventory(), evento.getWhoClicked())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alArrastrar(InventoryDragEvent evento) {
        if (bloquear(evento.getView().getTopInventory(), evento.getWhoClicked())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alCerrar(InventoryCloseEvent evento) {
        plugin.invsee().olvidar(evento.getPlayer().getUniqueId());
    }

    private boolean bloquear(Inventory superior, org.bukkit.entity.HumanEntity quien) {
        if (superior.getHolder() instanceof VistaDetallada) {
            return true;
        }
        if (!(quien instanceof Player jugador)) {
            return false;
        }
        if (!plugin.invsee().viendo(jugador.getUniqueId())) {
            return false;
        }
        return plugin.ajustes().invseeSoloLectura() && !jugador.hasPermission("moderacionx.invsee.editar");
    }
}
