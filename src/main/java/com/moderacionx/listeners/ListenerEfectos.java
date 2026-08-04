package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import com.moderacionx.efectos.GestorEfectos;
import com.moderacionx.efectos.MenuEfectos;
import com.moderacionx.efectos.SesionEfectos;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Maneja los clics del asistente de /efectos. */
public final class ListenerEfectos implements Listener {

    private final ModeracionX plugin;

    public ListenerEfectos(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alArrastrar(InventoryDragEvent evento) {
        if (evento.getView().getTopInventory().getHolder() instanceof MenuEfectos) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alHacerClic(InventoryClickEvent evento) {
        if (!(evento.getView().getTopInventory().getHolder() instanceof MenuEfectos menu)) {
            return;
        }
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player staff) || evento.getClickedInventory() == null) {
            return;
        }
        if (!evento.getClickedInventory().equals(evento.getView().getTopInventory())) {
            return;
        }

        GestorEfectos efectos = plugin.efectos();
        SesionEfectos sesion = efectos.sesion(staff);
        int slot = evento.getRawSlot();

        switch (menu.pantalla()) {
            case EFECTOS -> pantallaEfectos(staff, sesion, slot, evento.isRightClick());
            case OBJETIVOS -> pantallaObjetivos(staff, sesion, slot);
            case JUGADORES -> pantallaJugadores(staff, sesion, slot);
            case AJUSTES -> pantallaAjustes(staff, sesion, slot);
        }
    }

    // ------------------------------------------------------------- pantallas

    private void pantallaEfectos(Player staff, SesionEfectos sesion, int slot, boolean derecho) {
        GestorEfectos efectos = plugin.efectos();

        if (slot < GestorEfectos.POR_PAGINA) {
            List<PotionEffectType> tipos = efectos.tipos();
            int indice = sesion.paginaEfectos() * GestorEfectos.POR_PAGINA + slot;
            if (indice >= tipos.size()) {
                return;
            }
            sesion.efecto(tipos.get(indice));
            sesion.modo(derecho ? SesionEfectos.Modo.QUITAR : SesionEfectos.Modo.APLICAR);
            luego(() -> efectos.abrirObjetivos(staff));
            return;
        }

        switch (slot) {
            case GestorEfectos.SLOT_ANTERIOR -> {
                sesion.paginaEfectos(sesion.paginaEfectos() - 1);
                luego(() -> efectos.abrirEfectos(staff));
            }
            case GestorEfectos.SLOT_SIGUIENTE -> {
                sesion.paginaEfectos(sesion.paginaEfectos() + 1);
                luego(() -> efectos.abrirEfectos(staff));
            }
            case GestorEfectos.SLOT_QUITAR_TODOS -> {
                sesion.efecto(null);
                sesion.modo(SesionEfectos.Modo.QUITAR_TODOS);
                luego(() -> efectos.abrirObjetivos(staff));
            }
            case GestorEfectos.SLOT_CERRAR -> luego(staff::closeInventory);
            default -> {
                // hueco de relleno
            }
        }
    }

    private void pantallaObjetivos(Player staff, SesionEfectos sesion, int slot) {
        GestorEfectos efectos = plugin.efectos();

        switch (slot) {
            case GestorEfectos.SLOT_UNO -> {
                sesion.seleccion(SesionEfectos.Seleccion.UNO);
                sesion.objetivos().clear();
                sesion.paginaJugadores(0);
                luego(() -> efectos.abrirJugadores(staff));
            }
            case GestorEfectos.SLOT_VARIOS -> {
                sesion.seleccion(SesionEfectos.Seleccion.VARIOS);
                sesion.paginaJugadores(0);
                luego(() -> efectos.abrirJugadores(staff));
            }
            case GestorEfectos.SLOT_TODOS -> {
                if (!staff.hasPermission("moderacionx.efectos.todos")) {
                    plugin.mensajes().enviar(staff, "general.sin-permiso");
                    return;
                }
                sesion.seleccion(SesionEfectos.Seleccion.TODOS);
                sesion.objetivos().clear();
                siguientePaso(staff, sesion);
            }
            case GestorEfectos.SLOT_OBJETIVOS_VOLVER -> luego(() -> efectos.abrirEfectos(staff));
            default -> {
                // hueco de relleno
            }
        }
    }

    private void pantallaJugadores(Player staff, SesionEfectos sesion, int slot) {
        GestorEfectos efectos = plugin.efectos();

        if (slot < GestorEfectos.POR_PAGINA) {
            List<Player> conectados = efectos.visibles(staff);
            int indice = sesion.paginaJugadores() * GestorEfectos.POR_PAGINA + slot;
            if (indice >= conectados.size()) {
                return;
            }
            Player objetivo = conectados.get(indice);
            if (!objetivo.equals(staff) && !staff.hasPermission("moderacionx.efectos.otros")) {
                plugin.mensajes().enviar(staff, "general.sin-permiso");
                return;
            }

            if (sesion.seleccion() == SesionEfectos.Seleccion.VARIOS) {
                boolean seleccionado = sesion.alternarObjetivo(objetivo.getUniqueId());
                plugin.mensajes().enviar(staff,
                        seleccionado ? "efectos.seleccionado" : "efectos.deseleccionado",
                        Ph.de("jugador", objetivo.getName()));
                luego(() -> efectos.abrirJugadores(staff));
                return;
            }

            sesion.objetivos().clear();
            sesion.objetivos().add(objetivo.getUniqueId());
            siguientePaso(staff, sesion);
            return;
        }

        switch (slot) {
            case GestorEfectos.SLOT_ANTERIOR -> {
                sesion.paginaJugadores(sesion.paginaJugadores() - 1);
                luego(() -> efectos.abrirJugadores(staff));
            }
            case GestorEfectos.SLOT_SIGUIENTE -> {
                sesion.paginaJugadores(sesion.paginaJugadores() + 1);
                luego(() -> efectos.abrirJugadores(staff));
            }
            case GestorEfectos.SLOT_VOLVER -> luego(() -> efectos.abrirObjetivos(staff));
            case GestorEfectos.SLOT_CONFIRMAR -> {
                if (sesion.objetivos().isEmpty()) {
                    plugin.mensajes().enviar(staff, "efectos.sin-objetivos");
                    return;
                }
                siguientePaso(staff, sesion);
            }
            case GestorEfectos.SLOT_CERRAR -> luego(staff::closeInventory);
            default -> {
                // hueco de relleno
            }
        }
    }

    private void pantallaAjustes(Player staff, SesionEfectos sesion, int slot) {
        GestorEfectos efectos = plugin.efectos();
        var configuracion = plugin.ajustes().raiz();
        int corto = configuracion.getInt("efectos.incremento-corto", 10);
        int largo = configuracion.getInt("efectos.incremento-largo", 60);
        int duracionMaxima = configuracion.getInt("efectos.duracion-maxima", 86400);
        int nivelMaximo = configuracion.getInt("efectos.nivel-maximo", 10);

        switch (slot) {
            case GestorEfectos.SLOT_DUR_MENOS_LARGO -> sesion.sumarDuracion(-largo, duracionMaxima);
            case GestorEfectos.SLOT_DUR_MENOS_CORTO -> sesion.sumarDuracion(-corto, duracionMaxima);
            case GestorEfectos.SLOT_DUR_MAS_CORTO -> sesion.sumarDuracion(corto, duracionMaxima);
            case GestorEfectos.SLOT_DUR_MAS_LARGO -> sesion.sumarDuracion(largo, duracionMaxima);
            case GestorEfectos.SLOT_NIVEL_MENOS -> sesion.sumarNivel(-1, nivelMaximo);
            case GestorEfectos.SLOT_NIVEL_MAS -> sesion.sumarNivel(1, nivelMaximo);
            case GestorEfectos.SLOT_PARTICULAS -> sesion.alternarParticulas();
            case GestorEfectos.SLOT_PERMANENTE -> {
                if (!configuracion.getBoolean("efectos.permitir-permanente", true)) {
                    return;
                }
                sesion.alternarPermanente();
            }
            case GestorEfectos.SLOT_APLICAR -> {
                efectos.aplicar(staff);
                return;
            }
            case GestorEfectos.SLOT_AJUSTES_VOLVER -> {
                luego(() -> efectos.abrirObjetivos(staff));
                return;
            }
            case GestorEfectos.SLOT_AJUSTES_CERRAR -> {
                luego(staff::closeInventory);
                return;
            }
            default -> {
                return;
            }
        }
        luego(() -> efectos.abrirAjustes(staff));
    }

    /** Tras elegir los objetivos: al menu de ajustes si hay que aplicar, o directo si es quitar. */
    private void siguientePaso(Player staff, SesionEfectos sesion) {
        if (sesion.modo() == SesionEfectos.Modo.APLICAR) {
            luego(() -> plugin.efectos().abrirAjustes(staff));
            return;
        }
        luego(() -> plugin.efectos().aplicar(staff));
    }

    /** Los cambios de inventario se hacen en el tick siguiente al clic. */
    private void luego(Runnable tarea) {
        Bukkit.getScheduler().runTask(plugin, tarea);
    }
}
