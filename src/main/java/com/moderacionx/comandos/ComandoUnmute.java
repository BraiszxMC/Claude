package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** /unmute - devuelve la voz a un jugador. */
public final class ComandoUnmute extends ComandoBase {

    public ComandoUnmute(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.unmute")) {
            return;
        }
        if (argumentos.length == 0) {
            uso(emisor, "/unmute <jugador>");
            return;
        }

        Optional<Perfil> encontrado = plugin.sanciones().resolver(argumentos[0]);
        if (encontrado.isEmpty()) {
            plugin.mensajes().enviar(emisor, "general.jugador-no-encontrado", Ph.de("jugador", argumentos[0]));
            return;
        }
        Perfil objetivo = encontrado.get();

        Optional<Sancion> mute = plugin.sanciones().muteDe(objetivo.uuid());
        if (mute.isEmpty()) {
            plugin.mensajes().enviar(emisor, "mute.no-silenciado", Ph.de("objetivo", objetivo.nombre()));
            return;
        }

        plugin.sanciones().retirar(mute.get(), GestorSanciones.nombreDe(emisor), unir(argumentos, 1));
        plugin.mensajes().enviar(emisor, "mute.quitado", Ph.de("objetivo", objetivo.nombre()));

        Player conectado = Bukkit.getPlayer(objetivo.uuid());
        if (conectado != null) {
            plugin.mensajes().enviar(conectado, "mute.notificar-quitado");
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? nombresConectados(emisor, argumentos[0]) : List.of();
    }
}
