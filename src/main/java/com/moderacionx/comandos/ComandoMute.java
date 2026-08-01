package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /mute y /tempmute. */
public final class ComandoMute extends ComandoSancion {

    public ComandoMute(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        boolean temporal = etiqueta.startsWith("tempmute");
        if (!puede(emisor, temporal ? "moderacionx.tempmute" : "moderacionx.mute")) {
            return;
        }
        if (argumentos.length == 0 || (temporal && argumentos.length < 2)) {
            uso(emisor, temporal ? "/tempmute <jugador> <tiempo> [razon]" : "/mute <jugador> [tiempo] [razon]");
            return;
        }

        Perfil objetivo = objetivo(emisor, argumentos[0], TipoSancion.MUTE);
        if (objetivo == null) {
            return;
        }
        if (plugin.sanciones().muteDe(objetivo.uuid()).isPresent()) {
            plugin.mensajes().enviar(emisor, "mute.ya-silenciado", Ph.de("objetivo", objetivo.nombre()));
            return;
        }

        Analisis analisis = analizar(emisor, argumentos, 1, plugin.ajustes().duracionMutePorDefecto(), temporal);
        if (analisis == null) {
            return;
        }

        Sancion sancion = plugin.sanciones().registrar(Sancion.nueva(TipoSancion.MUTE,
                objetivo.uuid(), objetivo.nombre(), objetivo.ip(),
                GestorSanciones.uuidDe(emisor), GestorSanciones.nombreDe(emisor),
                analisis.razon(), analisis.duracion()));

        Player conectado = Bukkit.getPlayer(objetivo.uuid());
        if (conectado != null) {
            plugin.mensajes().enviar(conectado, "mute.notificar", Ph.de()
                    .con("razon", analisis.razon())
                    .con("duracion", Tiempo.formatear(analisis.duracion(), plugin.mensajes())));
        }

        plugin.mensajes().enviar(emisor, "mute.exito", Ph.de()
                .con("objetivo", objetivo.nombre())
                .con("duracion", Tiempo.formatear(analisis.duracion(), plugin.mensajes())));
        plugin.sanciones().anunciar("mute.anuncio", "mute.anuncio",
                plugin.sanciones().datos(sancion), analisis.silenciosa());
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            return nombresConectados(emisor, argumentos[0]);
        }
        if (argumentos.length == 2) {
            return duracionesSugeridas(argumentos[1]);
        }
        return List.of();
    }
}
