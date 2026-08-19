package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import com.moderacionx.bloques.RegistroBloque;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/** Registra roturas y colocaciones, y muestra el historial cuando el inspector esta activado. */
public final class ListenerBloques implements Listener {

    private final ModeracionX plugin;

    public ListenerBloques(ModeracionX plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------- inspector

    @EventHandler(priority = EventPriority.LOWEST)
    public void alInteractuar(PlayerInteractEvent evento) {
        if (evento.getClickedBlock() == null) {
            return;
        }
        Player jugador = evento.getPlayer();
        if (!plugin.bloques().inspecciona(jugador.getUniqueId())) {
            return;
        }
        if (evento.getAction() != Action.LEFT_CLICK_BLOCK && evento.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // en modo inspector no se interactua: solo se mira
        evento.setCancelled(true);
        mostrarHistorial(jugador, evento.getClickedBlock());
    }

    // en modo inspector, impedir que se rompa o ponga por cualquier via (creativo insta-break...)
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void alRomperInspector(BlockBreakEvent evento) {
        if (plugin.bloques().inspecciona(evento.getPlayer().getUniqueId())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void alColocarInspector(BlockPlaceEvent evento) {
        if (plugin.bloques().inspecciona(evento.getPlayer().getUniqueId())) {
            evento.setCancelled(true);
        }
    }

    // ------------------------------------------------------------- registro

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alRomper(BlockBreakEvent evento) {
        Player jugador = evento.getPlayer();
        if (plugin.bloques().inspecciona(jugador.getUniqueId())) {
            return;
        }
        plugin.bloques().registrar(jugador, RegistroBloque.ROTURA,
                evento.getBlock().getType(), evento.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alColocar(BlockPlaceEvent evento) {
        Player jugador = evento.getPlayer();
        if (plugin.bloques().inspecciona(jugador.getUniqueId())) {
            return;
        }
        plugin.bloques().registrar(jugador, RegistroBloque.COLOCACION,
                evento.getBlockPlaced().getType(), evento.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alSalir(PlayerQuitEvent evento) {
        plugin.bloques().olvidar(evento.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------- mostrar

    private void mostrarHistorial(Player jugador, Block bloque) {
        int limite = Math.max(1, plugin.ajustes().raiz().getInt("bloques.lineas-por-consulta", 10));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.bloques().volcarAhora();
            List<RegistroBloque> historial = plugin.bloques().historialEn(
                    bloque.getWorld().getName(), bloque.getX(), bloque.getY(), bloque.getZ(), limite);

            plugin.mensajes().enviar(jugador, "bloques.cabecera", Ph.de()
                    .con("mundo", bloque.getWorld().getName())
                    .con("x", bloque.getX())
                    .con("y", bloque.getY())
                    .con("z", bloque.getZ()));

            if (historial.isEmpty()) {
                plugin.mensajes().enviar(jugador, "bloques.sin-datos");
                return;
            }
            for (RegistroBloque r : historial) {
                plugin.mensajes().enviar(jugador, "bloques.linea", Ph.de()
                        .con("hace", Tiempo.formatear(System.currentTimeMillis() - r.fecha(), plugin.mensajes()))
                        .con("jugador", r.nombre())
                        .crudo("accion", plugin.mensajes().bruto(r.rotura() ? "bloques.rotura" : "bloques.colocacion"))
                        .con("material", r.material()));
            }
        });
    }
}
