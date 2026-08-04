package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Devuelve el vuelo tras entrar, morir o cambiar de mundo. */
public final class ListenerFly implements Listener {

    private final ModeracionX plugin;

    public ListenerFly(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alEntrar(PlayerJoinEvent evento) {
        restaurarLuego(evento.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alCambiarDeMundo(PlayerChangedWorldEvent evento) {
        restaurarLuego(evento.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alReaparecer(PlayerRespawnEvent evento) {
        restaurarLuego(evento.getPlayer());
    }

    /** El servidor reajusta el vuelo despues del evento, asi que se aplica un tick mas tarde. */
    private void restaurarLuego(Player jugador) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (jugador.isOnline()) {
                plugin.fly().restaurar(jugador);
            }
        });
    }
}
