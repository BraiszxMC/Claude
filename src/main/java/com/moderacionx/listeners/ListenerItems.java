package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import com.moderacionx.items.GestorItems;
import com.moderacionx.items.MenuItems;
import com.moderacionx.items.SesionItems;
import com.moderacionx.util.Texto;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

/** Clics y entradas por chat del editor de items. */
public final class ListenerItems implements Listener {

    private final ModeracionX plugin;

    public ListenerItems(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alArrastrar(InventoryDragEvent evento) {
        if (evento.getView().getTopInventory().getHolder() instanceof MenuItems) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alHacerClic(InventoryClickEvent evento) {
        if (!(evento.getView().getTopInventory().getHolder() instanceof MenuItems menu)) {
            return;
        }
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player staff) || evento.getClickedInventory() == null) {
            return;
        }
        if (!evento.getClickedInventory().equals(evento.getView().getTopInventory())) {
            return;
        }

        if (menu.pantalla() == MenuItems.Pantalla.PRINCIPAL) {
            principal(staff, evento.getRawSlot());
        } else {
            encantamientos(staff, evento.getRawSlot(), evento.isShiftClick(), evento.isRightClick());
        }
    }

    private void principal(Player staff, int slot) {
        GestorItems items = plugin.items();
        switch (slot) {
            case GestorItems.SLOT_ENCANTAR -> {
                if (!staff.hasPermission("moderacionx.items.encantar")) {
                    plugin.mensajes().enviar(staff, "general.sin-permiso");
                    return;
                }
                items.sesion(staff).pagina(0);
                luego(() -> items.abrirEncantamientos(staff));
            }
            case GestorItems.SLOT_NOMBRE -> {
                if (!staff.hasPermission("moderacionx.items.nombre")) {
                    plugin.mensajes().enviar(staff, "general.sin-permiso");
                    return;
                }
                pedirTexto(staff, SesionItems.Entrada.NOMBRE, "items.pide-nombre");
            }
            case GestorItems.SLOT_LORE -> {
                if (!staff.hasPermission("moderacionx.items.lore")) {
                    plugin.mensajes().enviar(staff, "general.sin-permiso");
                    return;
                }
                pedirTexto(staff, SesionItems.Entrada.LORE, "items.pide-lore");
            }
            case GestorItems.SLOT_LORE_LIMPIAR -> {
                items.limpiarLore(staff);
                luego(() -> items.abrirPrincipal(staff));
            }
            case GestorItems.SLOT_IRROMPIBLE -> {
                if (!staff.hasPermission("moderacionx.items.atributos")) {
                    plugin.mensajes().enviar(staff, "general.sin-permiso");
                    return;
                }
                items.alternarIrrompible(staff);
                luego(() -> items.abrirPrincipal(staff));
            }
            case GestorItems.SLOT_FLAGS -> {
                if (!staff.hasPermission("moderacionx.items.atributos")) {
                    plugin.mensajes().enviar(staff, "general.sin-permiso");
                    return;
                }
                items.alternarFlags(staff);
                luego(() -> items.abrirPrincipal(staff));
            }
            case GestorItems.SLOT_DESENCANTAR -> {
                items.quitarEncantamientos(staff);
                luego(() -> items.abrirPrincipal(staff));
            }
            case GestorItems.SLOT_CERRAR -> luego(staff::closeInventory);
            default -> {
                // hueco de relleno o vista previa
            }
        }
    }

    private void encantamientos(Player staff, int slot, boolean shift, boolean derecho) {
        GestorItems items = plugin.items();
        SesionItems sesion = items.sesion(staff);

        if (slot < GestorItems.POR_PAGINA) {
            List<Enchantment> lista = items.encantamientos();
            int indice = sesion.pagina() * GestorItems.POR_PAGINA + slot;
            if (indice >= lista.size()) {
                return;
            }
            Enchantment encantamiento = lista.get(indice);
            if (derecho || shift) {
                items.quitarEncantamiento(staff, encantamiento);
            } else {
                items.encantar(staff, encantamiento, sesion.nivel());
            }
            luego(() -> items.abrirEncantamientos(staff));
            return;
        }

        int corto = plugin.ajustes().raiz().getInt("items.incremento-corto", 1);
        int largo = plugin.ajustes().raiz().getInt("items.incremento-largo", 10);

        switch (slot) {
            case GestorItems.SLOT_ANTERIOR -> sesion.pagina(sesion.pagina() - 1);
            case GestorItems.SLOT_SIGUIENTE -> sesion.pagina(sesion.pagina() + 1);
            case GestorItems.SLOT_NIVEL_MENOS -> sesion.sumarNivel(-(shift ? largo : corto), items.nivelMaximo());
            case GestorItems.SLOT_NIVEL_MAS -> sesion.sumarNivel(shift ? largo : corto, items.nivelMaximo());
            case GestorItems.SLOT_NIVEL_INFO -> sesion.nivel(derecho ? 1 : items.nivelMaximo());
            case GestorItems.SLOT_VOLVER -> {
                luego(() -> items.abrirPrincipal(staff));
                return;
            }
            default -> {
                return;
            }
        }
        luego(() -> items.abrirEncantamientos(staff));
    }

    // ---------------------------------------------------------- entrada por chat

    private void pedirTexto(Player staff, SesionItems.Entrada entrada, String ruta) {
        plugin.items().sesion(staff).esperando(entrada);
        luego(() -> {
            staff.closeInventory();
            plugin.mensajes().enviar(staff, ruta);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alEscribir(AsyncChatEvent evento) {
        Player staff = evento.getPlayer();
        SesionItems sesion = plugin.items().sesionSiExiste(staff);
        if (sesion == null || sesion.esperando() == SesionItems.Entrada.NINGUNA) {
            return;
        }
        evento.setCancelled(true);

        String texto = Texto.plano(evento.message()).trim();
        SesionItems.Entrada entrada = sesion.esperando();
        sesion.esperando(SesionItems.Entrada.NINGUNA);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (List.of("cancelar", "cancel", "salir").contains(texto.toLowerCase(java.util.Locale.ROOT))) {
                plugin.mensajes().enviar(staff, "items.cancelado");
                return;
            }
            if (entrada == SesionItems.Entrada.NOMBRE) {
                plugin.items().renombrar(staff, texto);
            } else {
                plugin.items().anadirLore(staff, texto);
            }
            plugin.items().abrirPrincipal(staff);
        });
    }

    private void luego(Runnable tarea) {
        Bukkit.getScheduler().runTask(plugin, tarea);
    }
}
