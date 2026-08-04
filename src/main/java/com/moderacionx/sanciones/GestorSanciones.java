package com.moderacionx.sanciones;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Almacen;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cerebro de las sanciones: cache en memoria, persistencia y avisos. */
public final class GestorSanciones {

    public static final String PERMISO_NOTIFICACIONES = "moderacionx.notificaciones";

    private final ModeracionX plugin;
    private final Almacen almacen;

    private final Map<UUID, Sancion> banes = new ConcurrentHashMap<>();
    private final Map<UUID, Sancion> mutes = new ConcurrentHashMap<>();
    private final Map<String, Sancion> banesIp = new ConcurrentHashMap<>();
    private final Map<UUID, List<Sancion>> advertencias = new ConcurrentHashMap<>();

    public GestorSanciones(ModeracionX plugin, Almacen almacen) {
        this.plugin = plugin;
        this.almacen = almacen;
    }

    public Almacen almacen() {
        return almacen;
    }

    /** Carga en memoria todas las sanciones activas. */
    public void cargar() {
        banes.clear();
        mutes.clear();
        banesIp.clear();
        advertencias.clear();
        try {
            for (Sancion sancion : almacen.activas()) {
                indexar(sancion);
            }
        } catch (Exception excepcion) {
            plugin.getLogger().severe("No se pudieron cargar las sanciones activas: " + excepcion.getMessage());
        }
        plugin.getLogger().info("Sanciones activas cargadas: " + (banes.size() + mutes.size() + banesIp.size()));
    }

    private void indexar(Sancion sancion) {
        switch (sancion.tipo()) {
            case BAN -> {
                if (sancion.objetivoUuid() != null) {
                    banes.put(sancion.objetivoUuid(), sancion);
                }
            }
            case MUTE -> {
                if (sancion.objetivoUuid() != null) {
                    mutes.put(sancion.objetivoUuid(), sancion);
                }
            }
            case BANIP -> {
                if (sancion.objetivoIp() != null) {
                    banesIp.put(sancion.objetivoIp(), sancion);
                }
            }
            case WARN -> {
                if (sancion.objetivoUuid() != null) {
                    advertencias.computeIfAbsent(sancion.objetivoUuid(), clave -> new ArrayList<>()).add(sancion);
                }
            }
            default -> {
                // los kicks no se cachean
            }
        }
    }

    // ------------------------------------------------------------- consultas

    public Optional<Sancion> banDe(UUID uuid) {
        return vigente(banes.get(uuid), () -> banes.remove(uuid));
    }

    public Optional<Sancion> muteDe(UUID uuid) {
        return vigente(mutes.get(uuid), () -> mutes.remove(uuid));
    }

    public Optional<Sancion> banIpDe(String ip) {
        if (ip == null) {
            return Optional.empty();
        }
        return vigente(banesIp.get(ip), () -> banesIp.remove(ip));
    }

    private Optional<Sancion> vigente(Sancion sancion, Runnable alCaducar) {
        if (sancion == null) {
            return Optional.empty();
        }
        if (sancion.caducada()) {
            alCaducar.run();
            sancion.activa(false);
            persistirAsync(sancion);
            return Optional.empty();
        }
        return sancion.activa() ? Optional.of(sancion) : Optional.empty();
    }

    public int advertenciasActivas(UUID uuid) {
        List<Sancion> lista = advertencias.get(uuid);
        if (lista == null) {
            return 0;
        }
        long caducidad = plugin.ajustes().caducidadAdvertencias();
        long ahora = System.currentTimeMillis();
        lista.removeIf(sancion -> {
            boolean fuera = !sancion.activa()
                    || (caducidad != Tiempo.PERMANENTE && ahora - sancion.creada() > caducidad);
            if (fuera && sancion.activa()) {
                sancion.activa(false);
                persistirAsync(sancion);
            }
            return fuera;
        });
        return lista.size();
    }

    public int baneosActivos() {
        return banes.size();
    }

    public int silenciosActivos() {
        return mutes.size();
    }

    public int baneosIpActivos() {
        return banesIp.size();
    }

    // -------------------------------------------------------------- registro

    /** Guarda la sancion, la indexa y devuelve la misma instancia ya con id. */
    public Sancion registrar(Sancion sancion) {
        try {
            almacen.insertar(sancion);
        } catch (Exception excepcion) {
            plugin.getLogger().severe("No se pudo guardar la sancion: " + excepcion.getMessage());
        }
        indexar(sancion);
        registrarEnLog(sancion);
        return sancion;
    }

    /** Retira una sancion activa. */
    public void retirar(Sancion sancion, String autor, String razon) {
        sancion.retirar(autor, razon);
        switch (sancion.tipo()) {
            case BAN -> {
                if (sancion.objetivoUuid() != null) {
                    banes.remove(sancion.objetivoUuid());
                }
            }
            case MUTE -> {
                if (sancion.objetivoUuid() != null) {
                    mutes.remove(sancion.objetivoUuid());
                }
            }
            case BANIP -> {
                if (sancion.objetivoIp() != null) {
                    banesIp.remove(sancion.objetivoIp());
                }
            }
            case WARN -> {
                List<Sancion> lista = sancion.objetivoUuid() == null
                        ? null : advertencias.get(sancion.objetivoUuid());
                if (lista != null) {
                    lista.remove(sancion);
                }
            }
            default -> {
                // nada que descachear
            }
        }
        persistirAsync(sancion);
    }

    private void persistirAsync(Sancion sancion) {
        Runnable tarea = () -> {
            try {
                almacen.actualizar(sancion);
            } catch (Exception excepcion) {
                plugin.getLogger().severe("No se pudo actualizar la sancion #" + sancion.id() + ": " + excepcion.getMessage());
            }
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, tarea);
        } else {
            tarea.run();
        }
    }

    /** Tarea periodica: limpia de la cache lo que ya ha caducado. */
    public void revisarCaducadas() {
        for (Map.Entry<UUID, Sancion> entrada : Map.copyOf(banes).entrySet()) {
            banDe(entrada.getKey());
        }
        for (Map.Entry<UUID, Sancion> entrada : Map.copyOf(mutes).entrySet()) {
            UUID uuid = entrada.getKey();
            Sancion sancion = entrada.getValue();
            if (sancion.caducada() && muteDe(uuid).isEmpty()) {
                Player jugador = Bukkit.getPlayer(uuid);
                if (jugador != null) {
                    plugin.mensajes().enviar(jugador, "mute.notificar-quitado");
                }
            }
        }
        for (Map.Entry<String, Sancion> entrada : Map.copyOf(banesIp).entrySet()) {
            banIpDe(entrada.getKey());
        }
    }

    // ---------------------------------------------------------------- avisos

    /**
     * Manda el anuncio de una sancion.
     *
     * @param rutaPublica  mensaje que ve todo el servidor
     * @param rutaStaff    mensaje que solo ve el staff cuando es silenciosa
     * @param silenciosa   fuerza que solo lo vea el staff
     */
    public void anunciar(String rutaPublica, String rutaStaff, Ph placeholders, boolean silenciosa) {
        boolean publico = !silenciosa && plugin.ajustes().anunciarATodos();
        if (publico) {
            Bukkit.getServer().sendMessage(plugin.mensajes().comp(rutaPublica, placeholders));
            return;
        }
        var componente = plugin.mensajes().comp(rutaStaff, placeholders);
        Bukkit.getConsoleSender().sendMessage(componente);
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            if (jugador.hasPermission(PERMISO_NOTIFICACIONES)) {
                jugador.sendMessage(componente);
            }
        }
    }

    public void avisarStaff(String ruta, Ph placeholders) {
        var componente = plugin.mensajes().comp(ruta, placeholders);
        Bukkit.getConsoleSender().sendMessage(componente);
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            if (jugador.hasPermission(PERMISO_NOTIFICACIONES)) {
                jugador.sendMessage(componente);
            }
        }
    }

    private void registrarEnLog(Sancion sancion) {
        String linea = "#" + sancion.id() + " " + sancion.tipo().name()
                + " objetivo=" + (sancion.objetivoNombre() == null ? sancion.objetivoIp() : sancion.objetivoNombre())
                + " autor=" + sancion.autorNombre()
                + " duracion=" + (sancion.permanente() ? "permanente" : sancion.duracion() + "ms")
                + " razon=" + sancion.razon();
        if (plugin.ajustes().logConsola()) {
            plugin.getLogger().info(linea);
        }
        if (plugin.ajustes().logArchivo()) {
            plugin.escribirLog("sanciones.log", linea);
        }
    }

    // ------------------------------------------------------------ automatico

    /** Ejecuta las acciones configuradas al llegar a X advertencias. */
    public void aplicarAccionesDeAdvertencia(Perfil objetivo, int cantidad) {
        if (!plugin.ajustes().advertenciasActivadas()) {
            return;
        }
        ConfigurationSection acciones = plugin.ajustes().raiz().getConfigurationSection("advertencias.acciones");
        if (acciones == null) {
            return;
        }
        String comando = acciones.getString(String.valueOf(cantidad));
        if (comando == null || comando.isBlank()) {
            return;
        }
        String preparado = comando
                .replace("%jugador%", objetivo.nombre())
                .replace("%uuid%", String.valueOf(objetivo.uuid()))
                .replace("%ip%", objetivo.ip() == null ? "?" : objetivo.ip())
                .replace("%cantidad%", String.valueOf(cantidad));
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), preparado));
    }

    // -------------------------------------------------------------- perfiles

    /** Busca un jugador entre los conectados, la base de datos y la cache del servidor. */
    public Optional<Perfil> resolver(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        Player conectado = Bukkit.getPlayerExact(nombre);
        if (conectado != null) {
            return Optional.of(new Perfil(conectado.getUniqueId(), conectado.getName(),
                    ipDe(conectado), System.currentTimeMillis(), System.currentTimeMillis()));
        }
        try {
            Optional<Perfil> guardado = almacen.perfilPorNombre(nombre);
            if (guardado.isPresent()) {
                return guardado;
            }
        } catch (Exception excepcion) {
            plugin.getLogger().warning("Error buscando el perfil de " + nombre + ": " + excepcion.getMessage());
        }
        var enCache = Bukkit.getOfflinePlayerIfCached(nombre);
        if (enCache != null && enCache.getUniqueId() != null) {
            return Optional.of(new Perfil(enCache.getUniqueId(),
                    enCache.getName() == null ? nombre : enCache.getName(), null, 0L, enCache.getLastSeen()));
        }
        return Optional.empty();
    }

    public void guardarPerfil(Perfil perfil) {
        try {
            almacen.guardarPerfil(perfil);
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo guardar el perfil de " + perfil.nombre() + ": " + excepcion.getMessage());
        }
    }

    public static String ipDe(Player jugador) {
        var direccion = jugador.getAddress();
        return direccion == null || direccion.getAddress() == null ? null : direccion.getAddress().getHostAddress();
    }

    public static String nombreDe(CommandSender emisor) {
        return emisor instanceof Player jugador ? jugador.getName() : "Consola";
    }

    public static UUID uuidDe(CommandSender emisor) {
        return emisor instanceof Player jugador ? jugador.getUniqueId() : null;
    }

    /** Historial completo ordenado de mas nuevo a mas viejo. */
    public List<Sancion> historial(Perfil perfil) {
        List<Sancion> resultado = new ArrayList<>();
        try {
            resultado.addAll(almacen.historial(perfil.uuid()));
            if (perfil.ip() != null) {
                for (Sancion sancion : almacen.historialPorIp(perfil.ip())) {
                    if (sancion.objetivoUuid() == null) {
                        resultado.add(sancion);
                    }
                }
            }
        } catch (Exception excepcion) {
            plugin.getLogger().warning("Error leyendo el historial: " + excepcion.getMessage());
        }
        resultado.sort(Comparator.comparingLong(Sancion::creada).reversed());
        return resultado;
    }

    // ------------------------------------------------------------ pantallas

    /** Placeholders comunes de una sancion, listos para cualquier mensaje. */
    public Ph datos(Sancion sancion) {
        String formato = plugin.ajustes().formatoFecha();
        String zona = plugin.ajustes().zonaHoraria();
        return Ph.de()
                .con("id", sancion.id())
                .con("tipo", sancion.tipo().nombre())
                .con("razon", sancion.razon())
                .con("autor", sancion.autorNombre())
                .con("objetivo", sancion.objetivoNombre() == null ? sancion.objetivoIp() : sancion.objetivoNombre())
                .con("ip", sancion.objetivoIp() == null ? "-" : sancion.objetivoIp())
                .con("duracion", Tiempo.formatear(sancion.duracion(), plugin.mensajes()))
                .con("restante", Tiempo.restante(sancion.expira(), plugin.mensajes()))
                .con("expira", sancion.permanente()
                        ? plugin.mensajes().palabra("general.nunca")
                        : Tiempo.fecha(sancion.expira(), formato, zona))
                .con("fecha", Tiempo.fecha(sancion.creada(), formato, zona));
    }

    /** Pantalla que ve el jugador al ser rechazado o expulsado. */
    public net.kyori.adventure.text.Component pantalla(Sancion sancion) {
        String ruta = switch (sancion.tipo()) {
            case BANIP -> "banip.pantalla";
            case KICK -> "kick.pantalla";
            default -> sancion.permanente() ? "ban.pantalla-permanente" : "ban.pantalla-temporal";
        };
        return plugin.mensajes().bloque(ruta, datos(sancion));
    }

    public boolean exento(Perfil objetivo, TipoSancion tipo) {
        if (!plugin.ajustes().respetarExenciones()) {
            return false;
        }
        Player jugador = Bukkit.getPlayer(objetivo.uuid());
        return jugador != null
                && jugador.hasPermission("mx.exento." + tipo.name().toLowerCase(Locale.ROOT));
    }
}
