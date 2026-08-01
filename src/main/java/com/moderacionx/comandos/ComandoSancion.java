package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.sanciones.TipoSancion;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Base de los comandos que aplican sanciones: analiza tiempo, razon y bandera silenciosa. */
public abstract class ComandoSancion extends ComandoBase {

    /** Resultado de analizar los argumentos de una sancion. */
    protected record Analisis(long duracion, String razon, boolean silenciosa) {
    }

    protected ComandoSancion(ModeracionX plugin) {
        super(plugin);
    }

    /**
     * @param inicio              indice del primer argumento tras el nombre del jugador
     * @param duracionPorDefecto  duracion usada si no se escribe ninguna
     * @param duracionObligatoria si true, el primer argumento tiene que ser un tiempo
     * @return null si algo era invalido (ya se ha avisado al emisor)
     */
    protected Analisis analizar(CommandSender emisor, String[] argumentos, int inicio,
                                long duracionPorDefecto, boolean duracionObligatoria) {
        List<String> restantes = new ArrayList<>();
        boolean silenciosa = false;
        String bandera = plugin.ajustes().banderaSilenciosa();

        for (int i = inicio; i < argumentos.length; i++) {
            if (!bandera.isBlank() && bandera.equalsIgnoreCase(argumentos[i])) {
                silenciosa = true;
                continue;
            }
            restantes.add(argumentos[i]);
        }
        if (silenciosa && !emisor.hasPermission("moderacionx.silencioso")) {
            silenciosa = false;
        }

        long duracion = duracionPorDefecto;
        if (!restantes.isEmpty() && Tiempo.esDuracion(restantes.get(0))) {
            duracion = Tiempo.parsear(restantes.remove(0));
        } else if (duracionObligatoria) {
            plugin.mensajes().enviar(emisor, "general.tiempo-invalido",
                    Ph.de("valor", restantes.isEmpty() ? "-" : restantes.get(0)));
            return null;
        }

        if (!duracionPermitida(emisor, duracion)) {
            plugin.mensajes().enviar(emisor, "general.duracion-maxima", Ph.de("tiempo",
                    Tiempo.formatear(plugin.ajustes().duracionMaximaSinPermiso(), plugin.mensajes())));
            return null;
        }

        String razon = String.join(" ", restantes).trim();
        if (razon.isEmpty()) {
            razon = plugin.ajustes().razonPorDefecto();
        }
        return new Analisis(duracion, razon, silenciosa);
    }

    /** Igual que {@link #analizar}, pero para sanciones sin duracion (kick, warn). */
    protected Analisis analizarRazon(CommandSender emisor, String[] argumentos, int inicio) {
        List<String> restantes = new ArrayList<>();
        boolean silenciosa = false;
        String bandera = plugin.ajustes().banderaSilenciosa();

        for (int i = inicio; i < argumentos.length; i++) {
            if (!bandera.isBlank() && bandera.equalsIgnoreCase(argumentos[i])) {
                silenciosa = true;
                continue;
            }
            restantes.add(argumentos[i]);
        }
        if (silenciosa && !emisor.hasPermission("moderacionx.silencioso")) {
            silenciosa = false;
        }
        String razon = String.join(" ", restantes).trim();
        if (razon.isEmpty()) {
            razon = plugin.ajustes().razonPorDefecto();
        }
        return new Analisis(Tiempo.PERMANENTE, razon, silenciosa);
    }

    private boolean duracionPermitida(CommandSender emisor, long duracion) {
        if (emisor.hasPermission("moderacionx.ban.permanente")) {
            return true;
        }
        long maxima = plugin.ajustes().duracionMaximaSinPermiso();
        if (maxima == Tiempo.PERMANENTE) {
            return true;
        }
        return duracion != Tiempo.PERMANENTE && duracion <= maxima;
    }

    /** Busca al objetivo y comprueba auto-sancion, exenciones y jugadores desconocidos. */
    protected Perfil objetivo(CommandSender emisor, String nombre, TipoSancion tipo) {
        Optional<Perfil> encontrado = plugin.sanciones().resolver(nombre);
        if (encontrado.isEmpty()) {
            plugin.mensajes().enviar(emisor, "general.jugador-no-encontrado", Ph.de("jugador", nombre));
            return null;
        }
        Perfil perfil = encontrado.get();

        if (!plugin.ajustes().permitirOffline() && org.bukkit.Bukkit.getPlayer(perfil.uuid()) == null) {
            plugin.mensajes().enviar(emisor, "general.jugador-desconectado", Ph.de("jugador", perfil.nombre()));
            return null;
        }
        if (plugin.ajustes().bloquearAutoSancion() && emisor instanceof Player jugador
                && jugador.getUniqueId().equals(perfil.uuid())) {
            plugin.mensajes().enviar(emisor, "general.auto-sancion");
            return null;
        }
        if (plugin.sanciones().exento(perfil, tipo)) {
            plugin.mensajes().enviar(emisor, "general.exento", Ph.de("jugador", perfil.nombre()));
            return null;
        }
        return perfil;
    }
}
