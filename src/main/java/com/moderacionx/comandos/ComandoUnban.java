package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.util.Ph;
import org.bukkit.command.CommandSender;

import java.util.Optional;

/** /unban - retira un baneo activo. */
public final class ComandoUnban extends ComandoBase {

    public ComandoUnban(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.unban")) {
            return;
        }
        if (argumentos.length == 0) {
            uso(emisor, "/unban <jugador> [razon]");
            return;
        }

        Optional<Perfil> encontrado = plugin.sanciones().resolver(argumentos[0]);
        if (encontrado.isEmpty()) {
            plugin.mensajes().enviar(emisor, "general.jugador-no-encontrado", Ph.de("jugador", argumentos[0]));
            return;
        }
        Perfil objetivo = encontrado.get();

        Optional<Sancion> ban = plugin.sanciones().banDe(objetivo.uuid());
        if (ban.isEmpty()) {
            plugin.mensajes().enviar(emisor, "ban.no-baneado", Ph.de("objetivo", objetivo.nombre()));
            return;
        }

        String razon = unir(argumentos, 1);
        plugin.sanciones().retirar(ban.get(), GestorSanciones.nombreDe(emisor),
                razon.isEmpty() ? plugin.ajustes().razonPorDefecto() : razon);

        plugin.mensajes().enviar(emisor, "ban.desbaneado", Ph.de("objetivo", objetivo.nombre()));
        plugin.sanciones().anunciar("ban.anuncio-desban", "ban.anuncio-desban", Ph.de()
                .con("objetivo", objetivo.nombre())
                .con("autor", GestorSanciones.nombreDe(emisor)), false);
    }

    @Override
    protected java.util.List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? nombresConectados(emisor, argumentos[0]) : java.util.List.of();
    }
}
