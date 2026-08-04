package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.efectos.SesionEfectos;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /efectos - abre el menu de efectos de pociones. */
public final class ComandoEfectos extends ComandoBase {

    public ComandoEfectos(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.efectos")) {
            return;
        }
        Player staff = comoJugador(emisor);
        if (staff == null) {
            return;
        }

        SesionEfectos sesion = plugin.efectos().sesion(staff);
        sesion.paginaEfectos(0);
        sesion.paginaJugadores(0);
        sesion.modo(SesionEfectos.Modo.APLICAR);

        // /efectos <jugador> deja preseleccionado a ese jugador
        if (argumentos.length > 0) {
            Player objetivo = Bukkit.getPlayerExact(argumentos[0]);
            if (objetivo == null) {
                plugin.mensajes().enviar(emisor, "general.jugador-desconectado",
                        com.moderacionx.util.Ph.de("jugador", argumentos[0]));
                return;
            }
            if (!objetivo.equals(staff) && !puede(emisor, "moderacionx.efectos.otros")) {
                return;
            }
            sesion.seleccion(SesionEfectos.Seleccion.UNO);
            sesion.objetivos().clear();
            sesion.objetivos().add(objetivo.getUniqueId());
        }

        plugin.efectos().abrirEfectos(staff);
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? nombresConectados(emisor, argumentos[0]) : List.of();
    }
}
