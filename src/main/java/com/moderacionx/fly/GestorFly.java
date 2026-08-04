package com.moderacionx.fly;

import com.moderacionx.ModeracionX;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/** Recuerda quien tiene el vuelo activado y se lo devuelve al reconectar. */
public final class GestorFly {

    private final ModeracionX plugin;
    private final Set<UUID> volando = new CopyOnWriteArraySet<>();
    private final File archivo;

    public GestorFly(ModeracionX plugin) {
        this.plugin = plugin;
        this.archivo = new File(plugin.getDataFolder(), "datos/fly.yml");
        cargar();
    }

    public boolean activo(UUID uuid) {
        return volando.contains(uuid);
    }

    /** Aplica el vuelo y devuelve el estado resultante. */
    public boolean establecer(Player jugador, boolean activar) {
        jugador.setAllowFlight(activar);
        if (!activar) {
            jugador.setFlying(false);
        } else if (plugin.ajustes().raiz().getBoolean("fly.despegar-al-activar", false)) {
            jugador.setFlying(true);
        }
        aplicarVelocidad(jugador, activar);

        if (activar) {
            volando.add(jugador.getUniqueId());
        } else {
            volando.remove(jugador.getUniqueId());
        }
        guardar();
        return activar;
    }

    public boolean alternar(Player jugador) {
        return establecer(jugador, !jugador.getAllowFlight());
    }

    /** Devuelve el vuelo tras reconectar o cambiar de mundo. */
    public void restaurar(Player jugador) {
        if (!plugin.ajustes().raiz().getBoolean("fly.recordar", true)) {
            return;
        }
        if (!volando.contains(jugador.getUniqueId())) {
            return;
        }
        if (!jugador.hasPermission("moderacionx.fly")) {
            volando.remove(jugador.getUniqueId());
            guardar();
            return;
        }
        for (String mundo : plugin.ajustes().raiz().getStringList("fly.mundos-prohibidos")) {
            if (mundo.equalsIgnoreCase(jugador.getWorld().getName())) {
                jugador.setAllowFlight(false);
                jugador.setFlying(false);
                return;
            }
        }
        jugador.setAllowFlight(true);
        aplicarVelocidad(jugador, true);
    }

    private void aplicarVelocidad(Player jugador, boolean activar) {
        double velocidad = plugin.ajustes().raiz().getDouble("fly.velocidad", 0.1D);
        float valor = (float) Math.max(0.0001D, Math.min(1.0D, activar ? velocidad : 0.1D));
        jugador.setFlySpeed(valor);
    }

    /** ¿Se puede volar en este mundo? */
    public boolean mundoPermitido(Player jugador) {
        for (String mundo : plugin.ajustes().raiz().getStringList("fly.mundos-prohibidos")) {
            if (mundo.equalsIgnoreCase(jugador.getWorld().getName())) {
                return false;
            }
        }
        return true;
    }

    public int cantidad() {
        return volando.size();
    }

    private void cargar() {
        if (!archivo.exists()) {
            return;
        }
        YamlConfiguration configuracion = YamlConfiguration.loadConfiguration(archivo);
        for (String texto : configuracion.getStringList("volando")) {
            try {
                volando.add(UUID.fromString(texto));
            } catch (IllegalArgumentException ignorado) {
                // uuid corrupto
            }
        }
    }

    private void guardar() {
        if (!plugin.ajustes().raiz().getBoolean("fly.recordar", true)) {
            return;
        }
        YamlConfiguration configuracion = new YamlConfiguration();
        configuracion.set("volando", volando.stream().map(UUID::toString).toList());
        try {
            File carpeta = archivo.getParentFile();
            if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
                return;
            }
            configuracion.save(archivo);
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo guardar fly.yml: " + excepcion.getMessage());
        }
    }
}
