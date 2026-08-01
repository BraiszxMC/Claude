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

/** /ban y /tempban - sustituyen por completo al baneo de Minecraft. */
public final class ComandoBan extends ComandoSancion {

    public ComandoBan(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        boolean temporal = etiqueta.startsWith("tempban") || etiqueta.startsWith("tempbanear")
                || etiqueta.startsWith("banearteemp");
        String permiso = temporal ? "moderacionx.tempban" : "moderacionx.ban";
        if (!puede(emisor, permiso)) {
            return;
        }
        if (argumentos.length == 0 || (temporal && argumentos.length < 2)) {
            uso(emisor, temporal ? "/tempban <jugador> <tiempo> [razon]" : "/ban <jugador> [tiempo] [razon]");
            return;
        }

        Perfil objetivo = objetivo(emisor, argumentos[0], TipoSancion.BAN);
        if (objetivo == null) {
            return;
        }
        if (plugin.sanciones().banDe(objetivo.uuid()).isPresent()) {
            plugin.mensajes().enviar(emisor, "ban.ya-baneado", Ph.de("objetivo", objetivo.nombre()));
            return;
        }

        Analisis analisis = analizar(emisor, argumentos, 1, plugin.ajustes().duracionBanPorDefecto(), temporal);
        if (analisis == null) {
            return;
        }

        Sancion sancion = plugin.sanciones().registrar(Sancion.nueva(TipoSancion.BAN,
                objetivo.uuid(), objetivo.nombre(), objetivo.ip(),
                GestorSanciones.uuidDe(emisor), GestorSanciones.nombreDe(emisor),
                analisis.razon(), analisis.duracion()));

        if (plugin.ajustes().banearIpAlBanear() && objetivo.ip() != null) {
            plugin.sanciones().registrar(Sancion.nueva(TipoSancion.BANIP,
                    objetivo.uuid(), objetivo.nombre(), objetivo.ip(),
                    GestorSanciones.uuidDe(emisor), GestorSanciones.nombreDe(emisor),
                    analisis.razon(), analisis.duracion()));
        }

        Player conectado = Bukkit.getPlayer(objetivo.uuid());
        if (conectado != null) {
            conectado.kick(plugin.sanciones().pantalla(sancion));
        }

        Ph datos = plugin.sanciones().datos(sancion);
        plugin.sanciones().anunciar("ban.anuncio", "ban.aviso-staff", datos, analisis.silenciosa());
        plugin.mensajes().enviar(emisor, "ban.exito", Ph.de()
                .con("objetivo", objetivo.nombre())
                .con("duracion", Tiempo.formatear(analisis.duracion(), plugin.mensajes())));
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
