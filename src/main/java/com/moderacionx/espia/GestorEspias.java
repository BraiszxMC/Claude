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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

/** Gestiona quien esta espiando y como se muestran los comandos. */
public final class GestorEspias {

    private final ModeracionX plugin;
    private final Set<UUID> espias = new CopyOnWriteArraySet<>();
    private final File archivo;
    /** Comandos pendientes de escribir en la base de datos. */
    private final ConcurrentLinkedQueue<RegistroComando> pendientes = new ConcurrentLinkedQueue<>();

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
        String comando = raiz(linea);
        boolean consola = uuidAutor == null;

        // las contrasenas nunca se muestran ni se guardan
        String mostrado = linea;
        for (String censurado : plugin.ajustes().espiaCensurados()) {
            if (censurado.equalsIgnoreCase(comando)) {
                mostrado = "/" + comando + " " + Texto.plano(plugin.mensajes().comp("espia.censurado"));
                break;
            }
        }

        boolean ignorado = false;
        for (String nombre : plugin.ajustes().espiaIgnorados()) {
            if (nombre.equalsIgnoreCase(comando)) {
                ignorado = true;
                break;
            }
        }

        // 1) el espia en directo
        boolean enDirecto = plugin.ajustes().espiaActivado() && !ignorado
                && (!consola || plugin.ajustes().espiaIncluirConsola());
        if (enDirecto) {
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

        // 2) el registro que luego se consulta con /spyX
        anotar(autor, uuidAutor, comando, mostrado);
    }

    // ------------------------------------------------- registro consultable

    /** Mete el comando en la cola para guardarlo en la base de datos. */
    private void anotar(String autor, UUID uuidAutor, String comando, String linea) {
        if (!plugin.ajustes().raiz().getBoolean("espia.registro.activado", true)) {
            return;
        }
        if (uuidAutor == null && !plugin.ajustes().raiz().getBoolean("espia.registro.incluir-consola", true)) {
            return;
        }
        for (String ignorado : plugin.ajustes().raiz().getStringList("espia.registro.no-guardar")) {
            if (ignorado.equalsIgnoreCase(comando)) {
                return;
            }
        }
        String mundo = null;
        if (uuidAutor != null) {
            Player jugador = Bukkit.getPlayer(uuidAutor);
            if (jugador != null) {
                mundo = jugador.getWorld().getName();
            }
        }
        pendientes.add(RegistroComando.nuevo(uuidAutor, autor, comando, linea, mundo));

        int tope = Math.max(1, plugin.ajustes().raiz().getInt("espia.registro.guardar-cada", 25));
        if (pendientes.size() >= tope) {
            volcar();
        }
    }

    /** Manda a escribir la cola en segundo plano. */
    public void volcar() {
        if (pendientes.isEmpty()) {
            return;
        }
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::volcarAhora);
        } else {
            volcarAhora();
        }
    }

    /**
     * Escribe la cola en la base de datos en el hilo actual.
     * Hay que llamarlo antes de consultar el registro para no perderse lo ultimo,
     * y siempre desde un hilo asincrono.
     */
    public void volcarAhora() {
        List<RegistroComando> lote = new ArrayList<>();
        RegistroComando registro;
        while ((registro = pendientes.poll()) != null) {
            lote.add(registro);
        }
        if (lote.isEmpty()) {
            return;
        }
        try {
            plugin.sanciones().almacen().registrarComandos(lote);
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo guardar el registro de comandos: " + excepcion.getMessage());
        }
    }

    /** Borra del registro lo que sea mas viejo que los dias configurados. */
    public void purgar() {
        int dias = plugin.ajustes().raiz().getInt("espia.registro.dias", 30);
        if (dias <= 0) {
            return;
        }
        long limite = System.currentTimeMillis() - (long) dias * 86_400_000L;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int quitados = plugin.sanciones().almacen().limpiarComandos(limite);
                if (quitados > 0) {
                    plugin.getLogger().info("Registro de comandos: " + quitados + " entradas antiguas borradas.");
                }
            } catch (Exception excepcion) {
                plugin.getLogger().warning("No se pudo limpiar el registro de comandos: " + excepcion.getMessage());
            }
        });
    }

    public int enCola() {
        return pendientes.size();
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
