package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /clearX - limpia el chat del servidor. */
public final class ComandoClear extends ComandoBase {

    public ComandoClear(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.clearx")) {
            return;
        }

        var configuracion = plugin.ajustes().raiz();
        int lineas = Math.max(1, configuracion.getInt("chat.lineas", 100));
        Component vacia = Component.empty();

        if (argumentos.length > 0) {
            Player objetivo = Bukkit.getPlayerExact(argumentos[0]);
            if (objetivo == null) {
                plugin.mensajes().enviar(emisor, "general.jugador-desconectado", Ph.de("jugador", argumentos[0]));
                return;
            }
            limpiar(objetivo, vacia, lineas);
            plugin.mensajes().enviar(emisor, "chat.limpiado-jugador", Ph.de("jugador", objetivo.getName()));
            return;
        }

        int limpiados = 0;
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            if (configuracion.getBoolean("chat.respetar-exentos", true)
                    && jugador.hasPermission("moderacionx.clearx.exento")) {
                continue;
            }
            limpiar(jugador, vacia, lineas);
            limpiados++;
        }

        if (configuracion.getBoolean("chat.anunciar", true)) {
            Bukkit.getServer().sendMessage(plugin.mensajes().comp("chat.anuncio",
                    Ph.de("autor", emisor.getName())));
        }
        plugin.mensajes().enviar(emisor, "chat.limpiado", Ph.de("cantidad", limpiados));
    }

    private void limpiar(Player jugador, Component vacia, int lineas) {
        for (int i = 0; i < lineas; i++) {
            jugador.sendMessage(vacia);
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? nombresConectados(emisor, argumentos[0]) : List.of();
    }
}
