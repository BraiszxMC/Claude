package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Texto;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** SpyCommands: reenvia a los espias todo lo que se ejecuta en el servidor. */
public final class ListenerEspia implements Listener {

    private final ModeracionX plugin;

    public ListenerEspia(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alUsarComando(PlayerCommandPreprocessEvent evento) {
        if (plugin.espias().exento(evento.getPlayer())) {
            return;
        }
        plugin.espias().difundir(evento.getPlayer().getName(), evento.getPlayer().getUniqueId(), evento.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alUsarComandoConsola(ServerCommandEvent evento) {
        if (!plugin.ajustes().espiaIncluirConsola()) {
            return;
        }
        plugin.espias().difundir(evento.getSender().getName(), null, "/" + evento.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alHablar(AsyncChatEvent evento) {
        if (!plugin.ajustes().espiaIncluirChat() || plugin.espias().exento(evento.getPlayer())) {
            return;
        }
        plugin.espias().difundir(evento.getPlayer().getName(), evento.getPlayer().getUniqueId(),
                Texto.plano(evento.message()));
    }
}
