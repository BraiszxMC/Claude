package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Reenvia chat, entradas, salidas y muertes a Discord. */
public final class ListenerDiscord implements Listener {

    private final ModeracionX plugin;

    public ListenerDiscord(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alHablar(AsyncChatEvent evento) {
        Player jugador = evento.getPlayer();
        plugin.discord().evento("chat", Ph.de()
                .con("jugador", jugador.getName())
                .con("mensaje", Texto.plano(evento.message()))
                .con("mundo", jugador.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alEntrar(PlayerJoinEvent evento) {
        Player jugador = evento.getPlayer();
        plugin.discord().evento("entrada", Ph.de()
                .con("jugador", jugador.getName())
                .con("online", org.bukkit.Bukkit.getOnlinePlayers().size()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alSalir(PlayerQuitEvent evento) {
        Player jugador = evento.getPlayer();
        plugin.discord().evento("salida", Ph.de()
                .con("jugador", jugador.getName())
                .con("online", Math.max(0, org.bukkit.Bukkit.getOnlinePlayers().size() - 1)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alMorir(PlayerDeathEvent evento) {
        Player jugador = evento.getEntity();
        String mensaje = evento.deathMessage() == null
                ? jugador.getName() + " ha muerto"
                : Texto.plano(evento.deathMessage());
        plugin.discord().evento("muerte", Ph.de()
                .con("jugador", jugador.getName())
                .con("mensaje", mensaje)
                .con("mundo", jugador.getWorld().getName()));
    }
}
