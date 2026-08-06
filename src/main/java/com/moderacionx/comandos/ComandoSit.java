package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.sit.GestorSit;
import com.moderacionx.sit.ZonaSit;
import com.moderacionx.util.Ph;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /sit - sentarse en los bloques y administrar las zonas prohibidas. */
public final class ComandoSit extends ComandoBase {

    public ComandoSit(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length > 0) {
            String sub = argumentos[0].toLowerCase(Locale.ROOT);
            if (List.of("zona", "zonas", "region").contains(sub)) {
                zonas(emisor, argumentos);
                return;
            }
            if (List.of("importar", "import").contains(sub)) {
                importar(emisor, argumentos);
                return;
            }
        }

        if (!puede(emisor, "moderacionx.sit")) {
            return;
        }
        Player jugador = comoJugador(emisor);
        if (jugador == null) {
            return;
        }

        if (plugin.sit().sentado(jugador.getUniqueId())) {
            plugin.sit().levantar(jugador);
            plugin.mensajes().enviar(jugador, "sit.levantado");
            return;
        }

        Block bloque = bloqueBajoLosPies(jugador);
        String error = plugin.sit().sentar(jugador, bloque);
        if (error != null) {
            plugin.mensajes().enviar(jugador, error);
            return;
        }
        plugin.mensajes().enviar(jugador, "sit.sentado");
    }

    /** El bloque sobre el que esta de pie; si no hay, el que tiene delante. */
    private Block bloqueBajoLosPies(Player jugador) {
        Block bajoLosPies = jugador.getLocation().getBlock().getRelative(0, -1, 0);
        if (!bajoLosPies.isPassable()) {
            return bajoLosPies;
        }
        Block mirado = jugador.getTargetBlockExact((int) Math.max(1,
                plugin.ajustes().raiz().getDouble("sit.distancia-maxima", 3.0D)));
        return mirado == null ? bajoLosPies : mirado;
    }

    // ---------------------------------------------------------------- zonas

    private void zonas(CommandSender emisor, String[] argumentos) {
        if (!puede(emisor, "moderacionx.sit.zonas")) {
            return;
        }
        if (argumentos.length < 2) {
            uso(emisor, "/sit zona <pos1|pos2|crear|borrar|lista|aqui>");
            return;
        }

        switch (argumentos[1].toLowerCase(Locale.ROOT)) {
            case "pos1", "pos2" -> {
                Player jugador = comoJugador(emisor);
                if (jugador == null) {
                    return;
                }
                boolean primera = "pos1".equalsIgnoreCase(argumentos[1]);
                plugin.sit().seleccionar(jugador, primera);
                Location punto = jugador.getLocation().getBlock().getLocation();
                plugin.mensajes().enviar(emisor, primera ? "sit.pos1" : "sit.pos2", Ph.de()
                        .con("x", punto.getBlockX())
                        .con("y", punto.getBlockY())
                        .con("z", punto.getBlockZ()));
            }
            case "crear", "guardar" -> {
                Player jugador = comoJugador(emisor);
                if (jugador == null) {
                    return;
                }
                if (argumentos.length < 3) {
                    uso(emisor, "/sit zona crear <nombre>");
                    return;
                }
                if (!plugin.sit().crearZona(jugador, argumentos[2])) {
                    plugin.mensajes().enviar(emisor, "sit.faltan-puntos");
                    return;
                }
                plugin.sit().zonaDe(jugador.getLocation());
                for (ZonaSit zona : plugin.sit().zonas()) {
                    if (zona.nombre().equalsIgnoreCase(argumentos[2])) {
                        plugin.mensajes().enviar(emisor, "sit.zona-creada", plugin.sit().datosZona(zona));
                        return;
                    }
                }
            }
            case "borrar", "eliminar" -> {
                if (argumentos.length < 3) {
                    uso(emisor, "/sit zona borrar <nombre>");
                    return;
                }
                if (!plugin.sit().borrarZona(argumentos[2])) {
                    plugin.mensajes().enviar(emisor, "sit.zona-no-existe", Ph.de("zona", argumentos[2]));
                    return;
                }
                plugin.mensajes().enviar(emisor, "sit.zona-borrada", Ph.de("zona", argumentos[2]));
            }
            case "lista", "list" -> {
                List<ZonaSit> lista = plugin.sit().zonas();
                if (lista.isEmpty()) {
                    plugin.mensajes().enviar(emisor, "sit.zonas-vacias");
                    return;
                }
                plugin.mensajes().enviar(emisor, "sit.zonas-cabecera", Ph.de("cantidad", lista.size()));
                for (ZonaSit zona : lista) {
                    plugin.mensajes().enviar(emisor, "sit.zonas-linea", plugin.sit().datosZona(zona));
                }
            }
            case "aqui", "here" -> {
                Player jugador = comoJugador(emisor);
                if (jugador == null) {
                    return;
                }
                plugin.sit().zonaDe(jugador.getLocation()).ifPresentOrElse(
                        zona -> plugin.mensajes().enviar(emisor, "sit.aqui-prohibido", plugin.sit().datosZona(zona)),
                        () -> plugin.mensajes().enviar(emisor, "sit.aqui-permitido"));
            }
            default -> uso(emisor, "/sit zona <pos1|pos2|crear|borrar|lista|aqui>");
        }
    }

    private void importar(CommandSender emisor, String[] argumentos) {
        if (!puede(emisor, "moderacionx.sit.zonas")) {
            return;
        }
        String carpeta = argumentos.length > 1 ? argumentos[1] : "BlockBall";
        int margen = plugin.ajustes().raiz().getInt("sit.margen-importacion", 2);

        List<GestorSit.Encontrada> encontradas = plugin.sit().importar(carpeta, margen);
        if (encontradas.isEmpty()) {
            plugin.mensajes().enviar(emisor, "sit.importar-nada", Ph.de("plugin", carpeta));
            return;
        }
        for (GestorSit.Encontrada encontrada : encontradas) {
            plugin.sit().anadirZona(encontrada.zona());
            plugin.mensajes().enviar(emisor, "sit.importar-linea", plugin.sit().datosZona(encontrada.zona()));
        }
        plugin.sit().guardarZonas();
        plugin.mensajes().enviar(emisor, "sit.importar-fin", Ph.de()
                .con("cantidad", encontradas.size())
                .con("plugin", carpeta));
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            List<String> opciones = new ArrayList<>();
            if (emisor.hasPermission("moderacionx.sit.zonas")) {
                opciones.add("zona");
                opciones.add("importar");
            }
            return filtrar(opciones, argumentos[0]);
        }
        if (argumentos.length == 2 && "zona".equalsIgnoreCase(argumentos[0])) {
            return filtrar(List.of("pos1", "pos2", "crear", "borrar", "lista", "aqui"), argumentos[1]);
        }
        if (argumentos.length == 2 && "importar".equalsIgnoreCase(argumentos[0])) {
            return filtrar(List.of("BlockBall"), argumentos[1]);
        }
        if (argumentos.length == 3 && "zona".equalsIgnoreCase(argumentos[0])
                && "borrar".equalsIgnoreCase(argumentos[1])) {
            List<String> nombres = new ArrayList<>();
            for (ZonaSit zona : plugin.sit().zonas()) {
                nombres.add(zona.nombre());
            }
            return filtrar(nombres, argumentos[2]);
        }
        return List.of();
    }
}
