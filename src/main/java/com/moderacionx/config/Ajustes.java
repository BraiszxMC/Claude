package com.moderacionx.config;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Tiempo;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Acceso tipado a config.yml. */
public final class Ajustes {

    private final ModeracionX plugin;
    private FileConfiguration config;

    public Ajustes(ModeracionX plugin) {
        this.plugin = plugin;
        recargar();
    }

    public void recargar() {
        File archivo = new File(plugin.getDataFolder(), "config.yml");
        if (!archivo.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(archivo);
        try (InputStream recurso = plugin.getResource("config.yml")) {
            if (recurso != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(recurso, StandardCharsets.UTF_8)));
            }
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudieron cargar los valores por defecto de config.yml: " + excepcion.getMessage());
        }
    }

    public FileConfiguration raiz() {
        return config;
    }

    // ---------------------------------------------------------------- general

    public String prefijo() {
        return config.getString("general.prefijo", "");
    }

    public String formatoFecha() {
        return config.getString("general.formato-fecha", "dd/MM/yyyy HH:mm");
    }

    public String zonaHoraria() {
        return config.getString("general.zona-horaria", "UTC");
    }

    public int intervaloRevision() {
        return Math.max(5, config.getInt("general.intervalo-revision-segundos", 30));
    }

    public boolean logConsola() {
        return config.getBoolean("general.log-consola", true);
    }

    public boolean logArchivo() {
        return config.getBoolean("general.log-archivo", true);
    }

    // ----------------------------------------------------------- almacenamiento

    public String tipoAlmacen() {
        return config.getString("almacenamiento.tipo", "SQLITE").toUpperCase(Locale.ROOT);
    }

    public String archivoBaseDatos() {
        return config.getString("almacenamiento.archivo", "datos/moderacionx.db");
    }

    // -------------------------------------------------------------- sanciones

    public String razonPorDefecto() {
        return config.getString("sanciones.razon-por-defecto", "No se especifico una razon");
    }

    public long duracionBanPorDefecto() {
        return duracion("sanciones.duracion-ban-por-defecto", Tiempo.PERMANENTE);
    }

    public long duracionMutePorDefecto() {
        return duracion("sanciones.duracion-mute-por-defecto", 3_600_000L);
    }

    public long duracionMaximaSinPermiso() {
        return duracion("sanciones.duracion-maxima-sin-permiso", 2_592_000_000L);
    }

    public boolean permitirOffline() {
        return config.getBoolean("sanciones.permitir-offline", true);
    }

    public boolean bloquearAutoSancion() {
        return config.getBoolean("sanciones.bloquear-auto-sancion", true);
    }

    public boolean respetarExenciones() {
        return config.getBoolean("sanciones.respetar-exenciones", true);
    }

    public boolean banearIpAlBanear() {
        return config.getBoolean("sanciones.banear-ip-al-banear", false);
    }

    public boolean anunciarATodos() {
        return config.getBoolean("sanciones.anunciar-a-todos", true);
    }

    public String banderaSilenciosa() {
        return config.getString("sanciones.bandera-silenciosa", "-s");
    }

    // ------------------------------------------------------------ advertencias

    public boolean advertenciasActivadas() {
        return config.getBoolean("advertencias.activado", true);
    }

    public long caducidadAdvertencias() {
        return duracion("advertencias.caducidad", Tiempo.PERMANENTE);
    }

    // ----------------------------------------------------------------- espia

    public boolean espiaActivado() {
        return config.getBoolean("espia.activado", true);
    }

    public boolean espiaPersistente() {
        return config.getBoolean("espia.persistente", true);
    }

    public String espiaFormato() {
        return config.getString("espia.formato", "<dark_gray>[<gold>SPY<dark_gray>] <gray>%jugador% <dark_gray>› <white>%comando%");
    }

    public boolean espiaIncluirConsola() {
        return config.getBoolean("espia.incluir-consola", false);
    }

    public boolean espiaVerseAUnoMismo() {
        return config.getBoolean("espia.verse-a-uno-mismo", false);
    }

    public List<String> espiaIgnorados() {
        return config.getStringList("espia.comandos-ignorados");
    }

    public List<String> espiaCensurados() {
        return config.getStringList("espia.comandos-censurados");
    }

    public boolean espiaGuardarArchivo() {
        return config.getBoolean("espia.guardar-en-archivo", true);
    }

    public boolean espiaIncluirChat() {
        return config.getBoolean("espia.incluir-chat", false);
    }

    // ---------------------------------------------------------------- invsee

    public boolean invseeSoloLectura() {
        return config.getBoolean("invsee.solo-lectura", true);
    }

    public boolean invseeAvisarObjetivo() {
        return config.getBoolean("invsee.avisar-al-objetivo", false);
    }

    public String invseeTituloDetallado() {
        return config.getString("invsee.titulo-detallado", "<dark_gray>Inventario de <white>%jugador%");
    }

    public boolean invseeCerrarAlDesconectar() {
        return config.getBoolean("invsee.cerrar-al-desconectar", true);
    }

    // -------------------------------------------------------------- gamemode

    public boolean gamemodeAvisarObjetivo() {
        return config.getBoolean("gamemode.avisar-al-objetivo", true);
    }

    // ---------------------------------------------------------------- ayuda

    public int ayudaLineasPorPagina() {
        return Math.max(1, config.getInt("ayuda.lineas-por-pagina", 8));
    }

    public boolean ayudaSustituirVanilla() {
        return config.getBoolean("ayuda.sustituir-vanilla", true);
    }

    // --------------------------------------------------------------- vanilla

    public boolean desactivarComandosVanilla() {
        return config.getBoolean("vanilla.desactivar-comandos", true);
    }

    public List<String> comandosVanillaADesactivar() {
        return config.getStringList("vanilla.comandos-a-desactivar");
    }

    // --------------------------------------------------------------- utiles

    /** Lee una duracion escrita en texto y la devuelve en milisegundos. */
    public long duracion(String ruta, long porDefecto) {
        String valor = config.getString(ruta);
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return Tiempo.parsear(valor);
        } catch (IllegalArgumentException excepcion) {
            plugin.getLogger().warning("Duracion invalida en config.yml -> " + ruta + ": " + valor);
            return porDefecto;
        }
    }
}
