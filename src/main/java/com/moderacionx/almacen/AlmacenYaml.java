package com.moderacionx.almacen;

import com.moderacionx.ModeracionX;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Persistencia en ficheros YAML. Se usa si SQLite no esta disponible. */
public final class AlmacenYaml implements Almacen {

    private final ModeracionX plugin;
    private final File archivoSanciones;
    private final File archivoJugadores;
    private YamlConfiguration sanciones;
    private YamlConfiguration jugadores;
    private final AtomicLong siguienteId = new AtomicLong(1L);

    public AlmacenYaml(ModeracionX plugin, File carpeta) {
        this.plugin = plugin;
        this.archivoSanciones = new File(carpeta, "sanciones.yml");
        this.archivoJugadores = new File(carpeta, "jugadores.yml");
    }

    @Override
    public synchronized void inicializar() throws Exception {
        File carpeta = archivoSanciones.getParentFile();
        if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
            throw new IllegalStateException("No se pudo crear la carpeta " + carpeta.getAbsolutePath());
        }
        sanciones = YamlConfiguration.loadConfiguration(archivoSanciones);
        jugadores = YamlConfiguration.loadConfiguration(archivoJugadores);
        long maximo = 0L;
        ConfigurationSection raiz = sanciones.getConfigurationSection("sanciones");
        if (raiz != null) {
            for (String clave : raiz.getKeys(false)) {
                try {
                    maximo = Math.max(maximo, Long.parseLong(clave));
                } catch (NumberFormatException ignorado) {
                    // clave corrupta, se ignora
                }
            }
        }
        siguienteId.set(maximo + 1L);
        plugin.getLogger().info("Almacenamiento YAML listo (" + (raiz == null ? 0 : raiz.getKeys(false).size()) + " sanciones).");
    }

    @Override
    public synchronized void cerrar() {
        guardar();
    }

    @Override
    public String nombre() {
        return "YAML";
    }

    @Override
    public synchronized void insertar(Sancion sancion) {
        sancion.id(siguienteId.getAndIncrement());
        escribir(sancion);
        guardar();
    }

    @Override
    public synchronized void actualizar(Sancion sancion) {
        escribir(sancion);
        guardar();
    }

    @Override
    public synchronized List<Sancion> historial(UUID uuid) {
        List<Sancion> resultado = new ArrayList<>();
        for (Sancion sancion : todas()) {
            if (uuid.equals(sancion.objetivoUuid())) {
                resultado.add(sancion);
            }
        }
        return ordenar(resultado);
    }

    @Override
    public synchronized List<Sancion> historialPorNombre(String nombre) {
        List<Sancion> resultado = new ArrayList<>();
        for (Sancion sancion : todas()) {
            if (sancion.objetivoNombre() != null && sancion.objetivoNombre().equalsIgnoreCase(nombre)) {
                resultado.add(sancion);
            }
        }
        return ordenar(resultado);
    }

    @Override
    public synchronized List<Sancion> historialPorIp(String ip) {
        List<Sancion> resultado = new ArrayList<>();
        for (Sancion sancion : todas()) {
            if (ip.equals(sancion.objetivoIp())) {
                resultado.add(sancion);
            }
        }
        return ordenar(resultado);
    }

    @Override
    public synchronized List<Sancion> activas() {
        List<Sancion> resultado = new ArrayList<>();
        for (Sancion sancion : todas()) {
            if (sancion.activa()) {
                resultado.add(sancion);
            }
        }
        return ordenar(resultado);
    }

    @Override
    public synchronized List<Sancion> activasDe(UUID uuid, TipoSancion tipo) {
        List<Sancion> resultado = new ArrayList<>();
        for (Sancion sancion : todas()) {
            if (sancion.activa() && tipo == sancion.tipo() && uuid.equals(sancion.objetivoUuid())) {
                resultado.add(sancion);
            }
        }
        return ordenar(resultado);
    }

    @Override
    public synchronized int total() {
        ConfigurationSection raiz = sanciones.getConfigurationSection("sanciones");
        return raiz == null ? 0 : raiz.getKeys(false).size();
    }

    @Override
    public synchronized void guardarPerfil(Perfil perfil) {
        String base = "jugadores." + perfil.uuid();
        if (!jugadores.contains(base + ".primera")) {
            jugadores.set(base + ".primera", perfil.primeraConexion());
        }
        jugadores.set(base + ".nombre", perfil.nombre());
        jugadores.set(base + ".ip", perfil.ip());
        jugadores.set(base + ".ultima", perfil.ultimaConexion());
        guardarJugadores();
    }

    @Override
    public synchronized Optional<Perfil> perfilPorUuid(UUID uuid) {
        ConfigurationSection seccion = jugadores.getConfigurationSection("jugadores." + uuid);
        return seccion == null ? Optional.empty() : Optional.of(leerPerfil(uuid, seccion));
    }

    @Override
    public synchronized Optional<Perfil> perfilPorNombre(String nombre) {
        ConfigurationSection raiz = jugadores.getConfigurationSection("jugadores");
        if (raiz == null) {
            return Optional.empty();
        }
        for (String clave : raiz.getKeys(false)) {
            ConfigurationSection seccion = raiz.getConfigurationSection(clave);
            if (seccion != null && nombre.equalsIgnoreCase(seccion.getString("nombre", ""))) {
                try {
                    return Optional.of(leerPerfil(UUID.fromString(clave), seccion));
                } catch (IllegalArgumentException ignorado) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- internos

    private Perfil leerPerfil(UUID uuid, ConfigurationSection seccion) {
        return new Perfil(uuid,
                seccion.getString("nombre", uuid.toString()),
                seccion.getString("ip"),
                seccion.getLong("primera"),
                seccion.getLong("ultima"));
    }

    private List<Sancion> todas() {
        List<Sancion> resultado = new ArrayList<>();
        ConfigurationSection raiz = sanciones.getConfigurationSection("sanciones");
        if (raiz == null) {
            return resultado;
        }
        for (String clave : raiz.getKeys(false)) {
            ConfigurationSection seccion = raiz.getConfigurationSection(clave);
            if (seccion == null) {
                continue;
            }
            TipoSancion tipo = TipoSancion.desde(seccion.getString("tipo", "WARN"));
            resultado.add(new Sancion(
                    Long.parseLong(clave),
                    tipo == null ? TipoSancion.WARN : tipo,
                    uuidDe(seccion.getString("objetivo-uuid")),
                    seccion.getString("objetivo-nombre"),
                    seccion.getString("objetivo-ip"),
                    uuidDe(seccion.getString("autor-uuid")),
                    seccion.getString("autor-nombre", "?"),
                    seccion.getString("razon", ""),
                    seccion.getLong("creada"),
                    seccion.getLong("expira"),
                    seccion.getBoolean("activa"),
                    seccion.getString("removida-por"),
                    seccion.getLong("removida-en"),
                    seccion.getString("razon-remocion")));
        }
        return resultado;
    }

    private List<Sancion> ordenar(List<Sancion> lista) {
        lista.sort((a, b) -> Long.compare(b.creada(), a.creada()));
        return lista;
    }

    private UUID uuidDe(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException excepcion) {
            return null;
        }
    }

    private void escribir(Sancion sancion) {
        String base = "sanciones." + sancion.id();
        sanciones.set(base + ".tipo", sancion.tipo().name());
        sanciones.set(base + ".objetivo-uuid", sancion.objetivoUuid() == null ? null : sancion.objetivoUuid().toString());
        sanciones.set(base + ".objetivo-nombre", sancion.objetivoNombre());
        sanciones.set(base + ".objetivo-ip", sancion.objetivoIp());
        sanciones.set(base + ".autor-uuid", sancion.autorUuid() == null ? null : sancion.autorUuid().toString());
        sanciones.set(base + ".autor-nombre", sancion.autorNombre());
        sanciones.set(base + ".razon", sancion.razon());
        sanciones.set(base + ".creada", sancion.creada());
        sanciones.set(base + ".expira", sancion.expira());
        sanciones.set(base + ".activa", sancion.activa());
        sanciones.set(base + ".removida-por", sancion.removidaPor());
        sanciones.set(base + ".removida-en", sancion.removidaEn());
        sanciones.set(base + ".razon-remocion", sancion.razonRemocion());
    }

    private void guardar() {
        try {
            sanciones.save(archivoSanciones);
        } catch (IOException excepcion) {
            plugin.getLogger().severe("No se pudo guardar sanciones.yml: " + excepcion.getMessage());
        }
    }

    private void guardarJugadores() {
        try {
            jugadores.save(archivoJugadores);
        } catch (IOException excepcion) {
            plugin.getLogger().severe("No se pudo guardar jugadores.yml: " + excepcion.getMessage());
        }
    }

    /** Nombre en minusculas, util para busquedas rapidas. */
    static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
    }
}
