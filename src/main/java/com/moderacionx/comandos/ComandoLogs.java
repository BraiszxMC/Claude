package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /logsX - lee los logs del servidor y del propio plugin desde el juego.
 * <p>Solo deja abrir ficheros conocidos (el latest.log del servidor y los logs de
 * ModeracionX), nunca una ruta arbitraria, para que nadie pueda leer ficheros sueltos.
 */
public final class ComandoLogs extends ComandoBase {

    public ComandoLogs(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.logs")) {
            return;
        }

        if (argumentos.length == 0 || "lista".equalsIgnoreCase(argumentos[0]) || "list".equalsIgnoreCase(argumentos[0])) {
            listar(emisor);
            return;
        }

        if ("buscar".equalsIgnoreCase(argumentos[0]) || "grep".equalsIgnoreCase(argumentos[0])) {
            if (argumentos.length < 2) {
                uso(emisor, "/logsX buscar <texto> [archivo]");
                return;
            }
            String archivo = argumentos.length >= 3 ? argumentos[2] : "latest";
            buscar(emisor, archivo, argumentos[1]);
            return;
        }

        String archivo = argumentos[0];
        int lineas = plugin.ajustes().raiz().getInt("logs.lineas-por-defecto", 15);
        if (argumentos.length >= 2) {
            try {
                lineas = Integer.parseInt(argumentos[1]);
            } catch (NumberFormatException excepcion) {
                plugin.mensajes().enviar(emisor, "general.numero-invalido", Ph.de("valor", argumentos[1]));
                return;
            }
        }
        mostrar(emisor, archivo, lineas);
    }

    // ------------------------------------------------------------------ acciones

    private void listar(CommandSender emisor) {
        plugin.mensajes().enviar(emisor, "logs.cabecera");
        for (String nombre : disponibles()) {
            plugin.mensajes().enviar(emisor, "logs.linea-lista", Ph.de("archivo", nombre));
        }
        plugin.mensajes().enviar(emisor, "logs.ayuda");
    }

    private void mostrar(CommandSender emisor, String nombre, int lineas) {
        File fichero = resolver(nombre);
        if (fichero == null) {
            plugin.mensajes().enviar(emisor, "logs.no-existe", Ph.de("archivo", nombre));
            return;
        }
        int maximo = Math.max(1, Math.min(plugin.ajustes().raiz().getInt("logs.lineas-maximas", 100), lineas));
        final int cantidad = maximo;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> ultimas = ultimasLineas(fichero, cantidad);
            if (ultimas.isEmpty()) {
                plugin.mensajes().enviar(emisor, "logs.vacio", Ph.de("archivo", nombre));
                return;
            }
            plugin.mensajes().enviar(emisor, "logs.mostrando", Ph.de()
                    .con("archivo", nombre)
                    .con("lineas", ultimas.size()));
            for (String linea : ultimas) {
                emisor.sendMessage(Texto.comp(plugin.ajustes().raiz().getString("logs.formato-linea", "<gray>%linea%"),
                        Ph.de("linea", linea)));
            }
        });
    }

    private void buscar(CommandSender emisor, String nombre, String texto) {
        File fichero = resolver(nombre);
        if (fichero == null) {
            plugin.mensajes().enviar(emisor, "logs.no-existe", Ph.de("archivo", nombre));
            return;
        }
        int maximo = Math.max(1, plugin.ajustes().raiz().getInt("logs.lineas-maximas", 100));
        String buscado = texto.toLowerCase(Locale.ROOT);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> encontradas = new ArrayList<>();
            try {
                for (String linea : Files.readAllLines(fichero.toPath(), StandardCharsets.UTF_8)) {
                    if (linea.toLowerCase(Locale.ROOT).contains(buscado)) {
                        encontradas.add(linea);
                    }
                }
            } catch (Exception excepcion) {
                plugin.mensajes().enviar(emisor, "logs.error", Ph.de("error", excepcion.getMessage()));
                return;
            }
            if (encontradas.isEmpty()) {
                plugin.mensajes().enviar(emisor, "logs.sin-coincidencias", Ph.de()
                        .con("texto", texto).con("archivo", nombre));
                return;
            }
            int desde = Math.max(0, encontradas.size() - maximo);
            List<String> mostradas = encontradas.subList(desde, encontradas.size());
            plugin.mensajes().enviar(emisor, "logs.encontrado", Ph.de()
                    .con("total", encontradas.size())
                    .con("texto", texto)
                    .con("archivo", nombre));
            for (String linea : mostradas) {
                emisor.sendMessage(Texto.comp(plugin.ajustes().raiz().getString("logs.formato-linea", "<gray>%linea%"),
                        Ph.de("linea", linea)));
            }
        });
    }

    // ------------------------------------------------------------------ ficheros

    /** Nombres que se pueden abrir: latest (servidor) + los .log de ModeracionX. */
    private List<String> disponibles() {
        List<String> nombres = new ArrayList<>();
        if (logServidor().isFile()) {
            nombres.add("latest");
        }
        File carpeta = new File(plugin.getDataFolder(), "logs");
        File[] ficheros = carpeta.listFiles((dir, nombre) -> nombre.toLowerCase(Locale.ROOT).endsWith(".log"));
        if (ficheros != null) {
            for (File fichero : ficheros) {
                nombres.add(fichero.getName().replaceFirst("\\.log$", ""));
            }
        }
        return nombres;
    }

    /** Traduce un nombre pedido a un fichero permitido, o null si no vale. */
    private File resolver(String nombre) {
        String limpio = nombre.toLowerCase(Locale.ROOT).replaceFirst("\\.log$", "");
        if (limpio.contains("/") || limpio.contains("\\") || limpio.contains("..")) {
            return null;
        }
        if ("latest".equals(limpio) || "servidor".equals(limpio) || "server".equals(limpio)) {
            File servidor = logServidor();
            return servidor.isFile() ? servidor : null;
        }
        File fichero = new File(new File(plugin.getDataFolder(), "logs"), limpio + ".log");
        return fichero.isFile() ? fichero : null;
    }

    private File logServidor() {
        // plugins/ModeracionX -> plugins -> raiz del servidor -> logs/latest.log
        File raiz = plugin.getDataFolder().getParentFile().getParentFile();
        return new File(new File(raiz, "logs"), "latest.log");
    }

    /** Devuelve las ultimas N lineas de un fichero sin cargarlo entero. */
    private List<String> ultimasLineas(File fichero, int cantidad) {
        try {
            List<String> todas = Files.readAllLines(fichero.toPath(), StandardCharsets.UTF_8);
            int desde = Math.max(0, todas.size() - cantidad);
            return new ArrayList<>(todas.subList(desde, todas.size()));
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo leer " + fichero.getName() + ": " + excepcion.getMessage());
            return List.of();
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            List<String> opciones = new ArrayList<>(disponibles());
            opciones.add("buscar");
            opciones.add("lista");
            return filtrar(opciones, argumentos[0]);
        }
        if (argumentos.length == 3 && "buscar".equalsIgnoreCase(argumentos[0])) {
            return filtrar(disponibles(), argumentos[2]);
        }
        return List.of();
    }
}
