package com.moderacionx;

import com.moderacionx.almacen.Almacen;
import com.moderacionx.almacen.AlmacenSQLite;
import com.moderacionx.almacen.AlmacenYaml;
import com.moderacionx.antivpn.AntiVPN;
import com.moderacionx.comandos.ComandoAnuncio;
import com.moderacionx.comandos.ComandoAyuda;
import com.moderacionx.comandos.ComandoBan;
import com.moderacionx.comandos.ComandoBanIP;
import com.moderacionx.comandos.ComandoClear;
import com.moderacionx.comandos.ComandoEfectos;
import com.moderacionx.comandos.ComandoFly;
import com.moderacionx.comandos.ComandoGM;
import com.moderacionx.comandos.ComandoHistorial;
import com.moderacionx.comandos.ComandoInvsee;
import com.moderacionx.comandos.ComandoItems;
import com.moderacionx.comandos.ComandoKick;
import com.moderacionx.comandos.ComandoMute;
import com.moderacionx.comandos.ComandoPrincipal;
import com.moderacionx.comandos.ComandoSit;
import com.moderacionx.comandos.ComandoSpy;
import com.moderacionx.comandos.ComandoUnban;
import com.moderacionx.comandos.ComandoUnbanIP;
import com.moderacionx.comandos.ComandoUnmute;
import com.moderacionx.comandos.ComandoWarn;
import com.moderacionx.config.Ajustes;
import com.moderacionx.config.Mensajes;
import com.moderacionx.efectos.GestorEfectos;
import com.moderacionx.espia.GestorEspias;
import com.moderacionx.fly.GestorFly;
import com.moderacionx.items.GestorItems;
import com.moderacionx.invsee.GestorInvsee;
import com.moderacionx.listeners.ListenerChat;
import com.moderacionx.listeners.ListenerComandos;
import com.moderacionx.listeners.ListenerConexion;
import com.moderacionx.listeners.ListenerEfectos;
import com.moderacionx.listeners.ListenerEspia;
import com.moderacionx.listeners.ListenerFly;
import com.moderacionx.listeners.ListenerItems;
import com.moderacionx.listeners.ListenerInventario;
import com.moderacionx.listeners.ListenerSecretos;
import com.moderacionx.listeners.ListenerSit;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.sit.GestorSit;
import com.moderacionx.secretos.GestorSecretos;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Plugin de moderacion total para Minecraft 1.21.7. */
public final class ModeracionX extends JavaPlugin {

    private Ajustes ajustes;
    private Mensajes mensajes;
    private Almacen almacen;
    private GestorSanciones sanciones;
    private AntiVPN antivpn;
    private GestorEspias espias;
    private GestorSecretos secretos;
    private GestorInvsee invsee;
    private GestorEfectos efectos;
    private GestorFly fly;
    private GestorItems items;
    private GestorSit sit;
    private BukkitTask tareaRevision;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ajustes = new Ajustes(this);
        mensajes = new Mensajes(this);

        almacen = crearAlmacen();
        sanciones = new GestorSanciones(this, almacen);
        sanciones.cargar();

        antivpn = new AntiVPN(this);
        espias = new GestorEspias(this);
        secretos = new GestorSecretos(this);
        invsee = new GestorInvsee(this);
        efectos = new GestorEfectos(this);
        fly = new GestorFly(this);
        items = new GestorItems(this);
        sit = new GestorSit(this);

        registrarComandos();
        registrarListeners();
        programarRevision();
        desactivarComandosVanilla();

        getLogger().info("ModeracionX v" + getPluginMeta().getVersion() + " activado.");
    }

    @Override
    public void onDisable() {
        if (tareaRevision != null) {
            tareaRevision.cancel();
        }
        if (secretos != null) {
            secretos.limpiarTodo();
        }
        if (sit != null) {
            sit.limpiarTodo();
        }
        if (almacen != null) {
            almacen.cerrar();
        }
        getLogger().info("ModeracionX desactivado.");
    }

    // --------------------------------------------------------------- arranque

    private Almacen crearAlmacen() {
        String tipo = ajustes.tipoAlmacen();
        if (!"YAML".equals(tipo)) {
            Almacen sqlite = new AlmacenSQLite(this, new File(getDataFolder(), ajustes.archivoBaseDatos()));
            try {
                sqlite.inicializar();
                return sqlite;
            } catch (Exception excepcion) {
                getLogger().severe("No se pudo iniciar SQLite (" + excepcion.getMessage()
                        + "). Se usara almacenamiento YAML.");
            }
        }
        Almacen yaml = new AlmacenYaml(this, new File(getDataFolder(), "datos"));
        try {
            yaml.inicializar();
        } catch (Exception excepcion) {
            getLogger().severe("No se pudo iniciar el almacenamiento YAML: " + excepcion.getMessage());
        }
        return yaml;
    }

    private void registrarComandos() {
        registrar("moderacionx", new ComandoPrincipal(this));

        ComandoBan ban = new ComandoBan(this);
        registrar("ban", ban);
        registrar("tempban", ban);
        registrar("unban", new ComandoUnban(this));
        registrar("banip", new ComandoBanIP(this));
        registrar("unbanip", new ComandoUnbanIP(this));
        registrar("kick", new ComandoKick(this));

        ComandoMute mute = new ComandoMute(this);
        registrar("mute", mute);
        registrar("tempmute", mute);
        registrar("unmute", new ComandoUnmute(this));
        registrar("warn", new ComandoWarn(this));
        registrar("historial", new ComandoHistorial(this));

        ComandoInvsee inventario = new ComandoInvsee(this);
        registrar("invsee", inventario);
        registrar("echest", inventario);

        registrar("spy", new ComandoSpy(this));
        registrar("gm", new ComandoGM(this));
        registrar("anuncio", new ComandoAnuncio(this));
        registrar("clearx", new ComandoClear(this));
        registrar("efectos", new ComandoEfectos(this));
        registrar("fly", new ComandoFly(this));
        registrar("customitem", new ComandoItems(this));
        registrar("sit", new ComandoSit(this));
        registrar("help", new ComandoAyuda(this));
    }

    private void registrar(String nombre, TabExecutor ejecutor) {
        PluginCommand comando = getCommand(nombre);
        if (comando == null) {
            getLogger().warning("El comando /" + nombre + " no esta declarado en plugin.yml");
            return;
        }
        comando.setExecutor(ejecutor);
        comando.setTabCompleter(ejecutor);
    }

    private void registrarListeners() {
        var gestor = Bukkit.getPluginManager();
        gestor.registerEvents(new ListenerComandos(this), this);
        gestor.registerEvents(new ListenerSecretos(this), this);
        gestor.registerEvents(new ListenerConexion(this), this);
        gestor.registerEvents(new ListenerChat(this), this);
        gestor.registerEvents(new ListenerEspia(this), this);
        gestor.registerEvents(new ListenerInventario(this), this);
        gestor.registerEvents(new ListenerEfectos(this), this);
        gestor.registerEvents(new ListenerItems(this), this);
        gestor.registerEvents(new ListenerFly(this), this);
        gestor.registerEvents(new ListenerSit(this), this);
    }

    private void programarRevision() {
        if (tareaRevision != null) {
            tareaRevision.cancel();
        }
        long periodo = ajustes.intervaloRevision() * 20L;
        tareaRevision = Bukkit.getScheduler().runTaskTimer(this, () -> {
            sanciones.revisarCaducadas();
            sit.revisar();
        }, periodo, periodo);
    }

    /**
     * Esconde los comandos originales de Minecraft para que los de ModeracionX manden.
     * Siguen accesibles escribiendo el namespace completo si el servidor lo permite.
     */
    @SuppressWarnings("unchecked")
    private void desactivarComandosVanilla() {
        if (!ajustes.desactivarComandosVanilla()) {
            return;
        }
        try {
            Object servidor = Bukkit.getServer();
            Object mapa = servidor.getClass().getMethod("getCommandMap").invoke(servidor);
            Map<String, org.bukkit.command.Command> conocidos =
                    (Map<String, org.bukkit.command.Command>) mapa.getClass()
                            .getMethod("getKnownCommands").invoke(mapa);

            int quitados = 0;
            for (String nombre : ajustes.comandosVanillaADesactivar()) {
                if (conocidos.remove(nombre.toLowerCase()) != null) {
                    quitados++;
                }
            }
            try {
                servidor.getClass().getMethod("syncCommands").invoke(servidor);
            } catch (Exception ignorado) {
                // no todos los servidores lo exponen
            }
            if (quitados > 0) {
                getLogger().info("Comandos vanilla ocultados: " + quitados);
            }
        } catch (Exception excepcion) {
            getLogger().warning("No se pudieron ocultar los comandos vanilla: " + excepcion.getMessage());
        }
    }

    // -------------------------------------------------------------- recargar

    /** Recarga configuracion, mensajes, secretos y anti-VPN sin reiniciar el servidor. */
    public void recargarTodo() {
        reloadConfig();
        ajustes.recargar();
        mensajes.recargar();
        secretos.recargar();
        sit.cargarZonas();
        antivpn.recargar();
        sanciones.cargar();
        programarRevision();
    }

    /** Escribe una linea en plugins/ModeracionX/logs/&lt;archivo&gt;. */
    public void escribirLog(String archivo, String linea) {
        Runnable tarea = () -> {
            try {
                Path carpeta = getDataFolder().toPath().resolve("logs");
                Files.createDirectories(carpeta);
                String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                Files.writeString(carpeta.resolve(archivo), "[" + marca + "] " + linea + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception excepcion) {
                getLogger().warning("No se pudo escribir en " + archivo + ": " + excepcion.getMessage());
            }
        };
        if (isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, tarea);
        } else {
            tarea.run();
        }
    }

    // ------------------------------------------------------------- accesores

    public Ajustes ajustes() {
        return ajustes;
    }

    public Mensajes mensajes() {
        return mensajes;
    }

    public GestorSanciones sanciones() {
        return sanciones;
    }

    public AntiVPN antivpn() {
        return antivpn;
    }

    public GestorEspias espias() {
        return espias;
    }

    public GestorSecretos secretos() {
        return secretos;
    }

    public GestorInvsee invsee() {
        return invsee;
    }

    public GestorEfectos efectos() {
        return efectos;
    }

    public GestorFly fly() {
        return fly;
    }

    public GestorItems items() {
        return items;
    }

    public GestorSit sit() {
        return sit;
    }
}
