package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Optional;

/** Aplica los silencios al chat y a los comandos de comunicacion. */
public final class ListenerChat implements Listener {

    private final ModeracionX plugin;

    public ListenerChat(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alHablar(AsyncChatEvent evento) {
        Player jugador = evento.getPlayer();
        Optional<Sancion> mute = plugin.sanciones().muteDe(jugador.getUniqueId());
        if (mute.isEmpty()) {
            return;
        }
        evento.setCancelled(true);
        avisar(jugador, mute.get());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alUsarComando(PlayerCommandPreprocessEvent evento) {
        Player jugador = evento.getPlayer();
        Optional<Sancion> mute = plugin.sanciones().muteDe(jugador.getUniqueId());
        if (mute.isEmpty()) {
            return;
        }
        String comando = raiz(evento.getMessage());
        for (String bloqueado : plugin.mensajes().raiz().getStringList("mute.comandos-bloqueados")) {
            if (bloqueado.equalsIgnoreCase(comando)) {
                evento.setCancelled(true);
                plugin.mensajes().enviar(jugador, "mute.bloqueado-comando");
                avisar(jugador, mute.get());
                return;
            }
        }
    }

    private void avisar(Player jugador, Sancion mute) {
        plugin.mensajes().enviar(jugador, "mute.bloqueado", Ph.de()
                .con("razon", mute.razon())
                .con("autor", mute.autorNombre())
                .con("restante", Tiempo.restante(mute.expira(), plugin.mensajes())));
    }

    private String raiz(String mensaje) {
        String limpio = mensaje.startsWith("/") ? mensaje.substring(1) : mensaje;
        int espacio = limpio.indexOf(' ');
        String comando = espacio == -1 ? limpio : limpio.substring(0, espacio);
        int puntos = comando.indexOf(':');
        if (puntos != -1) {
            comando = comando.substring(puntos + 1);
        }
        return comando.toLowerCase(Locale.ROOT);
    }
}
