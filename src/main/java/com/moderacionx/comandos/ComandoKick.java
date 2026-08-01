package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /kick - expulsion con pantalla propia y registro en el historial. */
public final class ComandoKick extends ComandoSancion {

    public ComandoKick(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.kick")) {
            return;
        }
        if (argumentos.length == 0) {
            uso(emisor, "/kick <jugador> [razon]");
            return;
        }

        Player objetivo = Bukkit.getPlayerExact(argumentos[0]);
        if (objetivo == null) {
            plugin.mensajes().enviar(emisor, "general.jugador-desconectado", Ph.de("jugador", argumentos[0]));
            return;
        }
        if (plugin.ajustes().bloquearAutoSancion() && emisor instanceof Player jugador
                && jugador.getUniqueId().equals(objetivo.getUniqueId())) {
            plugin.mensajes().enviar(emisor, "general.auto-sancion");
            return;
        }
        if (plugin.ajustes().respetarExenciones() && objetivo.hasPermission("moderacionx.exento.kick")) {
            plugin.mensajes().enviar(emisor, "general.exento", Ph.de("jugador", objetivo.getName()));
            return;
        }

        Analisis analisis = analizarRazon(emisor, argumentos, 1);

        Sancion sancion = plugin.sanciones().registrar(Sancion.nueva(TipoSancion.KICK,
                objetivo.getUniqueId(), objetivo.getName(), GestorSanciones.ipDe(objetivo),
                GestorSanciones.uuidDe(emisor), GestorSanciones.nombreDe(emisor),
                analisis.razon(), Tiempo.PERMANENTE));

        objetivo.kick(plugin.sanciones().pantalla(sancion));

        plugin.mensajes().enviar(emisor, "kick.exito", Ph.de("objetivo", objetivo.getName()));
        plugin.sanciones().anunciar("kick.anuncio", "kick.anuncio",
                plugin.sanciones().datos(sancion), analisis.silenciosa());
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? nombresConectados(emisor, argumentos[0]) : List.of();
    }
}
