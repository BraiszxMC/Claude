package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /gm 0|1|2|3 [jugador] con atajos /gms /gmc /gma /gmsp. */
public final class ComandoGM extends ComandoBase {

    public ComandoGM(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.gm")) {
            return;
        }

        GameMode modo = porAtajo(etiqueta);
        int indiceJugador = modo == null ? 1 : 0;

        if (modo == null) {
            if (argumentos.length == 0) {
                uso(emisor, "/gm <0|1|2|3> [jugador]");
                return;
            }
            modo = modoDe(argumentos[0]);
            if (modo == null) {
                plugin.mensajes().enviar(emisor, "gamemode.modo-invalido", Ph.de("valor", argumentos[0]));
                return;
            }
        }

        if (!puede(emisor, "moderacionx.gm." + indiceDe(modo))) {
            return;
        }

        Player objetivo;
        if (argumentos.length > indiceJugador) {
            if (!puede(emisor, "moderacionx.gm.otros")) {
                return;
            }
            objetivo = Bukkit.getPlayerExact(argumentos[indiceJugador]);
            if (objetivo == null) {
                plugin.mensajes().enviar(emisor, "general.jugador-desconectado",
                        Ph.de("jugador", argumentos[indiceJugador]));
                return;
            }
        } else {
            objetivo = comoJugador(emisor);
            if (objetivo == null) {
                return;
            }
        }

        objetivo.setGameMode(modo);
        String nombreModo = plugin.mensajes().palabra("gamemode.nombres." + modo.name());

        if (objetivo.equals(emisor)) {
            plugin.mensajes().enviar(emisor, "gamemode.cambiado", Ph.de("modo", nombreModo));
            return;
        }
        plugin.mensajes().enviar(emisor, "gamemode.cambiado-otro", Ph.de()
                .con("jugador", objetivo.getName())
                .con("modo", nombreModo));
        if (plugin.ajustes().gamemodeAvisarObjetivo()) {
            plugin.mensajes().enviar(objetivo, "gamemode.avisado", Ph.de()
                    .con("autor", emisor.getName())
                    .con("modo", nombreModo));
        }
    }

    private GameMode porAtajo(String etiqueta) {
        return switch (etiqueta) {
            case "gms" -> GameMode.SURVIVAL;
            case "gmc" -> GameMode.CREATIVE;
            case "gma" -> GameMode.ADVENTURE;
            case "gmsp" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private GameMode modoDe(String valor) {
        String limpio = valor.toLowerCase(Locale.ROOT);
        switch (limpio) {
            case "0" -> {
                return GameMode.SURVIVAL;
            }
            case "1" -> {
                return GameMode.CREATIVE;
            }
            case "2" -> {
                return GameMode.ADVENTURE;
            }
            case "3" -> {
                return GameMode.SPECTATOR;
            }
            default -> {
                // se buscan los alias configurables
            }
        }
        if (contiene("survival", limpio)) {
            return GameMode.SURVIVAL;
        }
        if (contiene("creative", limpio)) {
            return GameMode.CREATIVE;
        }
        if (contiene("adventure", limpio)) {
            return GameMode.ADVENTURE;
        }
        if (contiene("spectator", limpio)) {
            return GameMode.SPECTATOR;
        }
        return null;
    }

    private boolean contiene(String clave, String valor) {
        for (String alias : plugin.ajustes().raiz().getStringList("gamemode.alias." + clave)) {
            if (alias.equalsIgnoreCase(valor)) {
                return true;
            }
        }
        return false;
    }

    private int indiceDe(GameMode modo) {
        return switch (modo) {
            case SURVIVAL -> 0;
            case CREATIVE -> 1;
            case ADVENTURE -> 2;
            case SPECTATOR -> 3;
        };
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        boolean atajo = porAtajo(etiqueta) != null;
        if (!atajo && argumentos.length == 1) {
            List<String> modos = new ArrayList<>(List.of("0", "1", "2", "3"));
            return filtrar(modos, argumentos[0]);
        }
        int indiceJugador = atajo ? 1 : 2;
        if (argumentos.length == indiceJugador) {
            return nombresConectados(emisor, argumentos[argumentos.length - 1]);
        }
        return List.of();
    }
}
