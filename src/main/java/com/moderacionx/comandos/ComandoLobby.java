package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /lobby            - te lleva al lobby
 * /lobby create <nombre> - crea el lobby en tu posicion (solo puede haber uno)
 * /lobby delete     - borra el lobby
 * /lobby info       - datos del lobby
 */
public final class ComandoLobby extends ComandoBase {

    public ComandoLobby(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 0) {
            teletransportar(emisor);
            return;
        }

        switch (argumentos[0].toLowerCase(Locale.ROOT)) {
            case "create", "crear", "set", "establecer" -> crear(emisor, argumentos);
            case "delete", "borrar", "eliminar", "remove" -> borrar(emisor);
            case "info" -> info(emisor);
            default -> teletransportar(emisor);
        }
    }

    private void teletransportar(CommandSender emisor) {
        if (!puede(emisor, "moderacionx.lobby")) {
            return;
        }
        Player jugador = comoJugador(emisor);
        if (jugador == null) {
            return;
        }
        if (!plugin.lobby().existe()) {
            plugin.mensajes().enviar(emisor, "lobby.no-hay");
            return;
        }
        plugin.lobby().teletransportar(jugador);
        plugin.mensajes().enviar(emisor, "lobby.teletransportado", Ph.de("nombre", plugin.lobby().nombre()));
    }

    private void crear(CommandSender emisor, String[] argumentos) {
        if (!puede(emisor, "moderacionx.lobby.admin")) {
            return;
        }
        Player jugador = comoJugador(emisor);
        if (jugador == null) {
            return;
        }
        if (argumentos.length < 2) {
            uso(emisor, "/lobby create <nombre>");
            return;
        }
        String nombre = unir(argumentos, 1);
        boolean reemplaza = plugin.lobby().existe();
        plugin.lobby().crear(nombre, jugador.getLocation());
        plugin.mensajes().enviar(emisor, reemplaza ? "lobby.reemplazado" : "lobby.creado",
                Ph.de("nombre", nombre));
    }

    private void borrar(CommandSender emisor) {
        if (!puede(emisor, "moderacionx.lobby.admin")) {
            return;
        }
        if (!plugin.lobby().existe()) {
            plugin.mensajes().enviar(emisor, "lobby.no-hay");
            return;
        }
        String nombre = plugin.lobby().nombre();
        plugin.lobby().borrar();
        plugin.mensajes().enviar(emisor, "lobby.borrado", Ph.de("nombre", nombre));
    }

    private void info(CommandSender emisor) {
        if (!puede(emisor, "moderacionx.lobby")) {
            return;
        }
        if (!plugin.lobby().existe()) {
            plugin.mensajes().enviar(emisor, "lobby.no-hay");
            return;
        }
        var ubicacion = plugin.lobby().ubicacion();
        plugin.mensajes().enviar(emisor, "lobby.info", Ph.de()
                .con("nombre", plugin.lobby().nombre())
                .con("mundo", ubicacion.getWorld().getName())
                .con("x", ubicacion.getBlockX())
                .con("y", ubicacion.getBlockY())
                .con("z", ubicacion.getBlockZ()));
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            if (emisor.hasPermission("moderacionx.lobby.admin")) {
                return filtrar(List.of("create", "delete", "info"), argumentos[0]);
            }
            return filtrar(List.of("info"), argumentos[0]);
        }
        return List.of();
    }
}
