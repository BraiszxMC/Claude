package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Intercepta los comandos secretos antes que nadie: nunca llegan al servidor,
 * asi que no existen para el resto del mundo.
 */
public final class ListenerSecretos implements Listener {

    private final ModeracionX plugin;

    public ListenerSecretos(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alUsarComando(PlayerCommandPreprocessEvent evento) {
        String linea = evento.getMessage().startsWith("/")
                ? evento.getMessage().substring(1)
                : evento.getMessage();
        if (plugin.secretos().intentar(evento.getPlayer(), linea)) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alUsarComandoConsola(ServerCommandEvent evento) {
        if (plugin.secretos().intentar(evento.getSender(), evento.getCommand())) {
            evento.setCancelled(true);
        }
    }
}
