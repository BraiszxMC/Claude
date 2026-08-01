package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /invsee y /echest - ver el inventario y el ender chest de un jugador. */
public final class ComandoInvsee extends ComandoBase {

    public ComandoInvsee(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        boolean ender = etiqueta.startsWith("ec") || etiqueta.startsWith("echest")
                || etiqueta.startsWith("verender") || etiqueta.startsWith("enderver");
        if (!puede(emisor, ender ? "moderacionx.echest" : "moderacionx.invsee")) {
            return;
        }
        Player staff = comoJugador(emisor);
        if (staff == null) {
            return;
        }
        if (argumentos.length == 0) {
            uso(emisor, ender ? "/echest <jugador>" : "/invsee <jugador> [completo]");
            return;
        }

        Player objetivo = Bukkit.getPlayerExact(argumentos[0]);
        if (objetivo == null) {
            plugin.mensajes().enviar(emisor, "general.jugador-desconectado", Ph.de("jugador", argumentos[0]));
            return;
        }
        if (objetivo.equals(staff)) {
            plugin.mensajes().enviar(emisor, "invsee.no-puedes-a-ti");
            return;
        }

        if (ender) {
            plugin.invsee().abrirEnder(staff, objetivo);
            return;
        }
        boolean detallado = argumentos.length > 1
                && List.of("completo", "full", "detallado", "todo").contains(argumentos[1].toLowerCase(Locale.ROOT));
        if (detallado) {
            plugin.invsee().abrirDetallada(staff, objetivo);
        } else {
            plugin.invsee().abrir(staff, objetivo);
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            return nombresConectados(emisor, argumentos[0]);
        }
        if (argumentos.length == 2) {
            return filtrar(List.of("completo"), argumentos[1]);
        }
        return List.of();
    }
}
