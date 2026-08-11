package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/**
 * Hace que los comandos de ModeracionX funcionen escritos con mayusculas.
 * <p>Minecraft distingue mayusculas al ejecutar comandos, asi que {@code /clearX}
 * no encontraria a {@code /clearx}. Aqui se normaliza el nombre antes de que el
 * servidor lo busque, y solo si el comando es de este plugin.
 */
public final class ListenerComandos implements Listener {

    private final ModeracionX plugin;

    public ListenerComandos(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alUsarComando(PlayerCommandPreprocessEvent evento) {
        String mensaje = evento.getMessage();
        if (mensaje.length() < 2 || mensaje.charAt(0) != '/') {
            return;
        }
        int espacio = mensaje.indexOf(' ');
        String raiz = espacio == -1 ? mensaje.substring(1) : mensaje.substring(1, espacio);
        String enMinusculas = raiz.toLowerCase(Locale.ROOT);
        if (raiz.equals(enMinusculas) || !esNuestro(enMinusculas)) {
            return;
        }
        evento.setMessage("/" + enMinusculas + (espacio == -1 ? "" : mensaje.substring(espacio)));
    }

    private boolean esNuestro(String nombre) {
        PluginCommand comando = Bukkit.getPluginCommand(nombre);
        return comando != null && plugin.equals(comando.getPlugin());
    }
}
