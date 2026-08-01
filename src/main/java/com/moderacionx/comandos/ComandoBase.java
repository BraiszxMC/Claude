package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Base comun de todos los comandos de ModeracionX. */
public abstract class ComandoBase implements TabExecutor {

    protected final ModeracionX plugin;

    protected ComandoBase(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @Override
    public final boolean onCommand(@NotNull CommandSender emisor, @NotNull Command comando,
                                   @NotNull String etiqueta, @NotNull String[] argumentos) {
        try {
            ejecutar(emisor, etiqueta.toLowerCase(Locale.ROOT), argumentos);
        } catch (Exception excepcion) {
            plugin.mensajes().enviar(emisor, "general.error",
                    Ph.de("error", String.valueOf(excepcion.getMessage())));
            plugin.getLogger().severe("Error ejecutando /" + etiqueta + ": " + excepcion);
        }
        return true;
    }

    @Override
    public final List<String> onTabComplete(@NotNull CommandSender emisor, @NotNull Command comando,
                                            @NotNull String etiqueta, @NotNull String[] argumentos) {
        return completar(emisor, etiqueta.toLowerCase(Locale.ROOT), argumentos);
    }

    protected abstract void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos);

    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return List.of();
    }

    // ---------------------------------------------------------------- utiles

    protected boolean puede(CommandSender emisor, String permiso) {
        if (emisor.hasPermission(permiso)) {
            return true;
        }
        plugin.mensajes().enviar(emisor, "general.sin-permiso");
        return false;
    }

    protected Player comoJugador(CommandSender emisor) {
        if (emisor instanceof Player jugador) {
            return jugador;
        }
        plugin.mensajes().enviar(emisor, "general.solo-jugadores");
        return null;
    }

    protected void uso(CommandSender emisor, String texto) {
        plugin.mensajes().enviar(emisor, "general.uso", Ph.de("uso", texto));
    }

    protected String unir(String[] argumentos, int desde) {
        if (desde >= argumentos.length) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(argumentos, desde, argumentos.length)).trim();
    }

    protected List<String> nombresConectados(CommandSender emisor, String prefijo) {
        List<String> nombres = new ArrayList<>();
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            if (emisor instanceof Player mirando && !mirando.canSee(jugador)) {
                continue;
            }
            nombres.add(jugador.getName());
        }
        return filtrar(nombres, prefijo);
    }

    protected List<String> filtrar(List<String> opciones, String prefijo) {
        String buscado = prefijo == null ? "" : prefijo.toLowerCase(Locale.ROOT);
        List<String> resultado = new ArrayList<>();
        for (String opcion : opciones) {
            if (opcion.toLowerCase(Locale.ROOT).startsWith(buscado)) {
                resultado.add(opcion);
            }
        }
        return resultado;
    }

    protected List<String> duracionesSugeridas(String prefijo) {
        return filtrar(List.of("10m", "30m", "1h", "6h", "12h", "1d", "3d", "7d", "14d", "30d", "permanente"), prefijo);
    }
}
