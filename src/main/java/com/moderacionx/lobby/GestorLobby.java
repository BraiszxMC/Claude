package com.moderacionx.lobby;

import com.moderacionx.ModeracionX;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Gestiona el lobby del servidor. Solo puede haber uno: crearlo otra vez lo reemplaza.
 * Se guarda en datos/lobby.yml para que sobreviva a los reinicios.
 */
public final class GestorLobby {

    private final ModeracionX plugin;
    private final File archivo;

    private String nombre;
    private Location ubicacion;

    public GestorLobby(ModeracionX plugin) {
        this.plugin = plugin;
        this.archivo = new File(plugin.getDataFolder(), "datos/lobby.yml");
        cargar();
    }

    public boolean existe() {
        return ubicacion != null && ubicacion.getWorld() != null;
    }

    public String nombre() {
        return nombre == null ? "" : nombre;
    }

    public Location ubicacion() {
        return ubicacion == null ? null : ubicacion.clone();
    }

    /** Crea (o reemplaza) el lobby en la posicion indicada. */
    public void crear(String nombre, Location ubicacion) {
        this.nombre = nombre;
        this.ubicacion = ubicacion.clone();
        guardar();
    }

    public void borrar() {
        this.nombre = null;
        this.ubicacion = null;
        guardar();
    }

    /**
     * Teletransporta al jugador al lobby.
     *
     * @return true si se ha teletransportado, false si no hay lobby
     */
    public boolean teletransportar(Player jugador) {
        if (!existe()) {
            return false;
        }
        jugador.teleport(ubicacion.clone());
        String clave = plugin.ajustes().raiz().getString("lobby.sonido", "minecraft:entity.enderman.teleport");
        if (clave != null && !clave.isBlank()) {
            try {
                jugador.playSound(net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key(clave),
                        net.kyori.adventure.sound.Sound.Source.MASTER, 1.0f, 1.0f));
            } catch (Exception ignorado) {
                // clave de sonido invalida
            }
        }
        return true;
    }

    // --------------------------------------------------------------- ficheros

    private void cargar() {
        if (!archivo.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(archivo);
        if (!cfg.contains("lobby.mundo")) {
            return;
        }
        String mundoNombre = cfg.getString("lobby.mundo", "");
        World mundo = Bukkit.getWorld(mundoNombre);
        if (mundo == null) {
            plugin.getLogger().warning("El lobby apunta al mundo '" + mundoNombre
                    + "', que no existe todavia. Se conservara por si carga mas tarde.");
            // se guarda el nombre para no perderlo, pero no hay ubicacion utilizable aun
            this.nombre = cfg.getString("lobby.nombre", "Lobby");
            return;
        }
        this.nombre = cfg.getString("lobby.nombre", "Lobby");
        this.ubicacion = new Location(mundo,
                cfg.getDouble("lobby.x"), cfg.getDouble("lobby.y"), cfg.getDouble("lobby.z"),
                (float) cfg.getDouble("lobby.yaw"), (float) cfg.getDouble("lobby.pitch"));
    }

    private void guardar() {
        YamlConfiguration cfg = new YamlConfiguration();
        if (existe()) {
            cfg.set("lobby.nombre", nombre);
            cfg.set("lobby.mundo", ubicacion.getWorld().getName());
            cfg.set("lobby.x", ubicacion.getX());
            cfg.set("lobby.y", ubicacion.getY());
            cfg.set("lobby.z", ubicacion.getZ());
            cfg.set("lobby.yaw", ubicacion.getYaw());
            cfg.set("lobby.pitch", ubicacion.getPitch());
        }
        try {
            File carpeta = archivo.getParentFile();
            if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
                return;
            }
            cfg.save(archivo);
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo guardar lobby.yml: " + excepcion.getMessage());
        }
    }
}
