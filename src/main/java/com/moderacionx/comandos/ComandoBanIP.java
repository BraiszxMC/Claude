package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** /banip - banea una IP suelta o la ultima IP conocida de un jugador. */
public final class ComandoBanIP extends ComandoSancion {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

    public ComandoBanIP(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.banip")) {
            return;
        }
        if (argumentos.length == 0) {
            uso(emisor, "/banip <jugador|ip> [tiempo] [razon]");
            return;
        }

        String entrada = argumentos[0];
        String ip;
        String nombre = null;
        java.util.UUID uuid = null;

        if (esIp(entrada)) {
            ip = entrada;
        } else {
            Perfil objetivo = objetivo(emisor, entrada, TipoSancion.BANIP);
            if (objetivo == null) {
                return;
            }
            if (objetivo.ip() == null) {
                plugin.mensajes().enviar(emisor, "banip.sin-ip", Ph.de("jugador", objetivo.nombre()));
                return;
            }
            ip = objetivo.ip();
            nombre = objetivo.nombre();
            uuid = objetivo.uuid();
        }

        Optional<Sancion> existente = plugin.sanciones().banIpDe(ip);
        if (existente.isPresent()) {
            plugin.mensajes().enviar(emisor, "banip.ya-baneada", Ph.de("ip", ip));
            return;
        }

        Analisis analisis = analizar(emisor, argumentos, 1, plugin.ajustes().duracionBanPorDefecto(), false);
        if (analisis == null) {
            return;
        }

        Sancion sancion = plugin.sanciones().registrar(Sancion.nueva(TipoSancion.BANIP,
                uuid, nombre, ip, GestorSanciones.uuidDe(emisor), GestorSanciones.nombreDe(emisor),
                analisis.razon(), analisis.duracion()));

        for (Player conectado : Bukkit.getOnlinePlayers()) {
            if (ip.equals(GestorSanciones.ipDe(conectado))) {
                conectado.kick(plugin.sanciones().pantalla(sancion));
            }
        }

        plugin.mensajes().enviar(emisor, "banip.exito", Ph.de()
                .con("ip", ip)
                .con("duracion", Tiempo.formatear(analisis.duracion(), plugin.mensajes())));
        plugin.sanciones().anunciar("banip.anuncio", "banip.anuncio", Ph.de()
                .con("objetivo", nombre == null ? ip : nombre)
                .con("ip", ip)
                .con("autor", GestorSanciones.nombreDe(emisor))
                .con("razon", analisis.razon()), analisis.silenciosa());
    }

    static boolean esIp(String texto) {
        if (!IPV4.matcher(texto).matches()) {
            return texto.contains(":") && texto.length() > 6;
        }
        for (String parte : texto.split("\\.")) {
            if (Integer.parseInt(parte) > 255) {
                return false;
            }
        }
        return true;
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
