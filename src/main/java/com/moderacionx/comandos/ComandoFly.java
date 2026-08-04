package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /fly - activa o desactiva el vuelo. */
public final class ComandoFly extends ComandoBase {

    public ComandoFly(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.fly")) {
            return;
        }

        List<String> encender = List.of("on", "si", "true", "activar");
        List<String> apagar = List.of("off", "no", "false", "desactivar");

        // /fly on|off se refiere a uno mismo, no a un jugador llamado "on"
        boolean estadoPrimero = argumentos.length > 0
                && (encender.contains(argumentos[0].toLowerCase(Locale.ROOT))
                || apagar.contains(argumentos[0].toLowerCase(Locale.ROOT)));

        Player objetivo;
        boolean esOtro = false;
        if (argumentos.length > 0 && !estadoPrimero) {
            if (!puede(emisor, "moderacionx.fly.otros")) {
                return;
            }
            objetivo = Bukkit.getPlayerExact(argumentos[0]);
            if (objetivo == null) {
                plugin.mensajes().enviar(emisor, "general.jugador-desconectado", Ph.de("jugador", argumentos[0]));
                return;
            }
            esOtro = !objetivo.equals(emisor);
        } else {
            objetivo = comoJugador(emisor);
            if (objetivo == null) {
                return;
            }
        }

        if (!plugin.fly().mundoPermitido(objetivo)) {
            plugin.mensajes().enviar(emisor, "fly.mundo-prohibido",
                    Ph.de("mundo", objetivo.getWorld().getName()));
            return;
        }

        boolean activo;
        int indiceEstado = estadoPrimero ? 0 : 1;
        String forzado = argumentos.length > indiceEstado
                ? argumentos[indiceEstado].toLowerCase(Locale.ROOT) : "";
        if (encender.contains(forzado)) {
            activo = plugin.fly().establecer(objetivo, true);
        } else if (apagar.contains(forzado)) {
            activo = plugin.fly().establecer(objetivo, false);
        } else {
            activo = plugin.fly().alternar(objetivo);
        }

        plugin.mensajes().enviar(objetivo, activo ? "fly.activado" : "fly.desactivado");
        if (esOtro) {
            plugin.mensajes().enviar(emisor, activo ? "fly.activado-otro" : "fly.desactivado-otro",
                    Ph.de("jugador", objetivo.getName()));
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            List<String> opciones = new java.util.ArrayList<>(nombresConectados(emisor, argumentos[0]));
            opciones.addAll(filtrar(List.of("on", "off"), argumentos[0]));
            return opciones;
        }
        if (argumentos.length == 2) {
            return filtrar(List.of("on", "off"), argumentos[1]);
        }
        return List.of();
    }
}
