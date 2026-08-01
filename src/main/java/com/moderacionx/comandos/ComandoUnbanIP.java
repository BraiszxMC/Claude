package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.util.Ph;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

/** /unbanip - retira el baneo de una IP. */
public final class ComandoUnbanIP extends ComandoBase {

    public ComandoUnbanIP(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.unbanip")) {
            return;
        }
        if (argumentos.length == 0) {
            uso(emisor, "/unbanip <jugador|ip>");
            return;
        }

        String ip = argumentos[0];
        if (!ComandoBanIP.esIp(ip)) {
            Optional<Perfil> perfil = plugin.sanciones().resolver(argumentos[0]);
            if (perfil.isEmpty() || perfil.get().ip() == null) {
                plugin.mensajes().enviar(emisor, "banip.sin-ip", Ph.de("jugador", argumentos[0]));
                return;
            }
            ip = perfil.get().ip();
        }

        Optional<Sancion> ban = plugin.sanciones().banIpDe(ip);
        if (ban.isEmpty()) {
            plugin.mensajes().enviar(emisor, "banip.no-baneada", Ph.de("ip", ip));
            return;
        }

        plugin.sanciones().retirar(ban.get(), GestorSanciones.nombreDe(emisor), unir(argumentos, 1));
        plugin.mensajes().enviar(emisor, "banip.desbaneada", Ph.de("ip", ip));
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? nombresConectados(emisor, argumentos[0]) : List.of();
    }
}
