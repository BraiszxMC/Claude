package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /warn - advertencias con acciones automaticas configurables. */
public final class ComandoWarn extends ComandoSancion {

    public ComandoWarn(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.warn")) {
            return;
        }
        if (argumentos.length == 0) {
            uso(emisor, "/warn <jugador> [razon]");
            return;
        }

        Perfil objetivo = objetivo(emisor, argumentos[0], TipoSancion.WARN);
        if (objetivo == null) {
            return;
        }

        Analisis analisis = analizarRazon(emisor, argumentos, 1);
        Sancion sancion = plugin.sanciones().registrar(Sancion.nueva(TipoSancion.WARN,
                objetivo.uuid(), objetivo.nombre(), objetivo.ip(),
                GestorSanciones.uuidDe(emisor), GestorSanciones.nombreDe(emisor),
                analisis.razon(), com.moderacionx.util.Tiempo.PERMANENTE));

        int cantidad = plugin.sanciones().advertenciasActivas(objetivo.uuid());

        Player conectado = Bukkit.getPlayer(objetivo.uuid());
        if (conectado != null) {
            plugin.mensajes().enviar(conectado, "warn.notificar", Ph.de()
                    .con("razon", analisis.razon())
                    .con("cantidad", cantidad));
        }

        plugin.mensajes().enviar(emisor, "warn.exito", Ph.de()
                .con("objetivo", objetivo.nombre())
                .con("cantidad", cantidad));
        plugin.sanciones().anunciar("warn.anuncio", "warn.anuncio",
                plugin.sanciones().datos(sancion).con("cantidad", cantidad), analisis.silenciosa());

        plugin.sanciones().aplicarAccionesDeAdvertencia(objetivo, cantidad);
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? nombresConectados(emisor, argumentos[0]) : List.of();
    }
}
