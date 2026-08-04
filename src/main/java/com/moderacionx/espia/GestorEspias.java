package com.moderacionx.espia;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/** Gestiona quien esta espiando y como se muestran los comandos. */
public final class GestorEspias {

    private final ModeracionX plugin;
    private final Set<UUID> espias = new CopyOnWriteArraySet<>();
    private final File archivo;

    public GestorEspias(ModeracionX plugin) {
        this.plugin = plugin;
        this.archivo = new File(plugin.getDataFolder(), "datos/espias.yml");
        cargar();
    }

    public boolean espiando(UUID uuid) {
        return espias.contains(uuid);
    }

    public boolean alternar(UUID uuid) {
        boolean activado;
        if (espias.contains(uuid)) {
            espias.remove(uuid);
            activado = false;
        } else {
            espias.add(uuid);
            activado = true;
        }
        guardar();
        return activado;
    }

    public void establecer(UUID uuid, boolean activo) {
        if (activo) {
            espias.add(uuid);
        } else {
            espias.remove(uuid);
        }
        guardar();
    }

    public Set<UUID> espias() {
        return Set.copyOf(espias);
    }

    public int cantidad() {
        return espias.size();
    }

    /**
     * Difunde un comando a todos los espias.
     *
     * @param autor    quien lo ejecuto
     * @param uuidAutor uuid del autor, o null si es la consola
     * @param linea    comando completo, con la barra inicial
     */
    public void difundir(String autor, UUID uuidAutor, String linea) {
        if (!plugin.ajustes().espiaActivado()) {
            return;
        }
        String comando = raiz(linea);
        for (String ignorado : plugin.ajustes().espiaIgnorados()) {
            if (ignorado.equalsIgnoreCase(comando)) {
                return;
            }
        }

        String mostrado = linea;
        for (String censurado : plugin.ajustes().espiaCensurados()) {
            if (censurado.equalsIgnoreCase(comando)) {
                mostrado = "/" + comando + " " + Texto.plano(plugin.mensajes().comp("espia.censurado"));
                break;
            }
        }

        Component componente = Texto.comp(plugin.ajustes().espiaFormato(), Ph.de()
                .con("jugador", autor)
                .con("comando", mostrado));

        for (UUID uuid : espias) {
            if (!plugin.ajustes().espiaVerseAUnoMismo() && uuid.equals(uuidAutor)) {
                continue;
            }
            Player espia = Bukkit.getPlayer(uuid);
            if (espia != null && espia.hasPermission("moderacionx.spy")) {
                espia.sendMessage(componente);
            }
        }

        if (plugin.ajustes().espiaGuardarArchivo()) {
            plugin.escribirLog("comandos.log", autor + ": " + mostrado);
        }
    }

    /** ¿Este emisor debe quedar fuera del espia? */
    public boolean exento(CommandSender emisor) {
        return emisor.hasPermission("mx.exento.spy");
    }

    private String raiz(String linea) {
        String limpio = linea.startsWith("/") ? linea.substring(1) : linea;
        int espacio = limpio.indexOf(' ');
        String comando = espacio == -1 ? limpio : limpio.substring(0, espacio);
        int puntos = comando.indexOf(':');
        if (puntos != -1) {
            comando = comando.substring(puntos + 1);
        }
        return comando.toLowerCase(Locale.ROOT);
    }

    private void cargar() {
        if (!plugin.ajustes().espiaPersistente() || !archivo.exists()) {
            return;
        }
        YamlConfiguration configuracion = YamlConfiguration.loadConfiguration(archivo);
        for (String texto : configuracion.getStringList("espias")) {
            try {
                espias.add(UUID.fromString(texto));
            } catch (IllegalArgumentException ignorado) {
                // uuid corrupto
            }
        }
    }

    private void guardar() {
        if (!plugin.ajustes().espiaPersistente()) {
            return;
        }
        YamlConfiguration configuracion = new YamlConfiguration();
        configuracion.set("espias", espias.stream().map(UUID::toString).toList());
        try {
            File carpeta = archivo.getParentFile();
            if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
                return;
            }
            configuracion.save(archivo);
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo guardar espias.yml: " + excepcion.getMessage());
        }
    }
}
