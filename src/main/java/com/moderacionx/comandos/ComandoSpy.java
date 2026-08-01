package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /spy - SpyCommands: ver en directo lo que ejecutan los jugadores. */
public final class ComandoSpy extends ComandoBase {

    public ComandoSpy(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.spy")) {
            return;
        }

        if (argumentos.length > 0 && "lista".equalsIgnoreCase(argumentos[0])) {
            List<String> nombres = new ArrayList<>();
            for (UUID uuid : plugin.espias().espias()) {
                Player jugador = Bukkit.getPlayer(uuid);
                if (jugador != null) {
                    nombres.add(jugador.getName());
                }
            }
            if (nombres.isEmpty()) {
                plugin.mensajes().enviar(emisor, "espia.lista-vacia");
            } else {
                plugin.mensajes().enviar(emisor, "espia.lista", Ph.de("jugadores", String.join(", ", nombres)));
            }
            return;
        }

        Player jugador = comoJugador(emisor);
        if (jugador == null) {
            return;
        }

        boolean activo;
        if (argumentos.length > 0) {
            String valor = argumentos[0].toLowerCase(Locale.ROOT);
            if (List.of("on", "si", "true", "activar").contains(valor)) {
                activo = true;
            } else if (List.of("off", "no", "false", "desactivar").contains(valor)) {
                activo = false;
            } else {
                uso(emisor, "/spy [on|off|lista]");
                return;
            }
            plugin.espias().establecer(jugador.getUniqueId(), activo);
        } else {
            activo = plugin.espias().alternar(jugador.getUniqueId());
        }

        plugin.mensajes().enviar(emisor, activo ? "espia.activado" : "espia.desactivado");
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? filtrar(List.of("on", "off", "lista"), argumentos[0]) : List.of();
    }
}
