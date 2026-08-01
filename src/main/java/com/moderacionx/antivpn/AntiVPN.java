package com.moderacionx.antivpn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moderacionx.ModeracionX;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Comprobacion de VPN / proxy contra varios proveedores, con cache y whitelist. */
public final class AntiVPN {

    /** Un proveedor de la API leido de config.yml. */
    public record Proveedor(String nombre, String url, String ruta, List<String> positivos,
                            String rutaHosting, List<String> hostings, Map<String, String> cabeceras) {
    }

    private record Entrada(ResultadoVPN resultado, long expira) {
    }

    private final ModeracionX plugin;
    private final HttpClient http;
    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();
    private final Set<UUID> bypass = new CopyOnWriteArraySet<>();
    private final File archivoBypass;
    private final File archivoWhitelist;

    private List<Proveedor> proveedores = List.of();
    private List<String> ipsExtra = List.of();
    private List<String> jugadoresExtra = List.of();

    public AntiVPN(ModeracionX plugin) {
        this.plugin = plugin;
        this.archivoBypass = new File(plugin.getDataFolder(), "datos/bypass.yml");
        this.archivoWhitelist = new File(plugin.getDataFolder(), "datos/whitelist-vpn.yml");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        recargar();
        cargarBypass();
    }

    public void recargar() {
        cache.clear();
        cargarWhitelistExtra();
        List<Proveedor> lista = new ArrayList<>();
        List<Map<?, ?>> crudos = plugin.ajustes().raiz().getMapList("antivpn.proveedores");
        for (Map<?, ?> crudo : crudos) {
            if (!Boolean.TRUE.equals(valor(crudo, "activado", Boolean.class, Boolean.TRUE))) {
                continue;
            }
            String url = valor(crudo, "url", String.class, null);
            if (url == null || url.isBlank()) {
                continue;
            }
            lista.add(new Proveedor(
                    valor(crudo, "nombre", String.class, "?"),
                    url,
                    valor(crudo, "ruta", String.class, ""),
                    textos(crudo.get("valores-positivos")),
                    valor(crudo, "ruta-hosting", String.class, ""),
                    textos(crudo.get("valores-hosting")),
                    cabeceras(crudo.get("cabeceras"))));
        }
        proveedores = List.copyOf(lista);
    }

    public boolean activado() {
        return plugin.ajustes().raiz().getBoolean("antivpn.activado", true);
    }

    public String accion() {
        return plugin.ajustes().raiz().getString("antivpn.accion", "KICK").toUpperCase(Locale.ROOT);
    }

    public boolean bloquearHosting() {
        return plugin.ajustes().raiz().getBoolean("antivpn.bloquear-hosting", true);
    }

    public boolean fallarAbierto() {
        return plugin.ajustes().raiz().getBoolean("antivpn.fallar-abierto", true);
    }

    public boolean avisarStaff() {
        return plugin.ajustes().raiz().getBoolean("antivpn.avisar-staff", true);
    }

    public int enCache() {
        return cache.size();
    }

    public List<Proveedor> proveedores() {
        return proveedores;
    }

    // ------------------------------------------------------------ whitelist

    public boolean exento(UUID uuid, String nombre, String ip) {
        if (uuid != null && bypass.contains(uuid)) {
            return true;
        }
        for (String permitido : plugin.ajustes().raiz().getStringList("antivpn.jugadores-whitelist")) {
            if (permitido.equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        for (String permitido : jugadoresExtra) {
            if (permitido.equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return ipExenta(ip);
    }

    public boolean ipExenta(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        for (String patron : plugin.ajustes().raiz().getStringList("antivpn.ips-whitelist")) {
            if (coincide(ip, patron)) {
                return true;
            }
        }
        for (String patron : ipsExtra) {
            if (coincide(ip, patron)) {
                return true;
            }
        }
        return false;
    }

    private boolean coincide(String ip, String patron) {
        if (patron == null || patron.isBlank()) {
            return false;
        }
        if (patron.endsWith("*")) {
            return ip.startsWith(patron.substring(0, patron.length() - 1));
        }
        return ip.equalsIgnoreCase(patron);
    }

    /** Recuerda que este jugador puede saltarse el anti-VPN. */
    public void recordarBypass(UUID uuid, boolean tienePermiso) {
        if (!plugin.ajustes().raiz().getBoolean("antivpn.recordar-bypass", true)) {
            return;
        }
        boolean cambio = tienePermiso ? bypass.add(uuid) : bypass.remove(uuid);
        if (cambio) {
            guardarBypass();
        }
    }

    private void cargarBypass() {
        if (!archivoBypass.exists()) {
            return;
        }
        YamlConfiguration configuracion = YamlConfiguration.loadConfiguration(archivoBypass);
        for (String texto : configuracion.getStringList("bypass")) {
            try {
                bypass.add(UUID.fromString(texto));
            } catch (IllegalArgumentException ignorado) {
                // uuid corrupto
            }
        }
    }

    private void guardarBypass() {
        YamlConfiguration configuracion = new YamlConfiguration();
        configuracion.set("bypass", bypass.stream().map(UUID::toString).toList());
        try {
            File carpeta = archivoBypass.getParentFile();
            if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
                return;
            }
            configuracion.save(archivoBypass);
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo guardar bypass.yml: " + excepcion.getMessage());
        }
    }

    // --------------------------------------------------------- comprobacion

    /** Comprobacion bloqueante: llamar SIEMPRE desde un hilo asincrono. */
    public ResultadoVPN comprobar(String ip) {
        if (ip == null || ip.isBlank()) {
            return ResultadoVPN.sinDatos();
        }
        Entrada entrada = cache.get(ip);
        if (entrada != null && entrada.expira() > System.currentTimeMillis()) {
            return entrada.resultado();
        }
        int timeout = plugin.ajustes().raiz().getInt("antivpn.timeout-ms", 4000);
        for (Proveedor proveedor : proveedores) {
            ResultadoVPN resultado = consultar(proveedor, ip, timeout);
            if (resultado.valido()) {
                guardarEnCache(ip, resultado);
                return resultado;
            }
        }
        return ResultadoVPN.sinDatos();
    }

    private void guardarEnCache(String ip, ResultadoVPN resultado) {
        long minutos = plugin.ajustes().raiz().getLong("antivpn.cache-minutos", 120L);
        cache.put(ip, new Entrada(resultado, System.currentTimeMillis() + minutos * 60_000L));
    }

    public void limpiarCache() {
        cache.clear();
    }

    private ResultadoVPN consultar(Proveedor proveedor, String ip, int timeout) {
        try {
            HttpRequest.Builder constructor = HttpRequest.newBuilder()
                    .uri(URI.create(proveedor.url().replace("%ip%", ip)))
                    .timeout(Duration.ofMillis(Math.max(500, timeout)))
                    .header("User-Agent", "ModeracionX/" + plugin.getPluginMeta().getVersion())
                    .GET();
            for (Map.Entry<String, String> cabecera : proveedor.cabeceras().entrySet()) {
                constructor.header(cabecera.getKey(), cabecera.getValue());
            }
            HttpResponse<String> respuesta = http.send(constructor.build(), HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
                return ResultadoVPN.sinDatos();
            }
            JsonElement raiz = JsonParser.parseString(respuesta.body());
            if (!raiz.isJsonObject()) {
                return ResultadoVPN.sinDatos();
            }
            Optional<String> valorVpn = valorEn(raiz.getAsJsonObject(), proveedor.ruta(), ip);
            Optional<String> valorHosting = valorEn(raiz.getAsJsonObject(), proveedor.rutaHosting(), ip);
            if (valorVpn.isEmpty() && valorHosting.isEmpty()) {
                return ResultadoVPN.sinDatos();
            }
            boolean vpn = valorVpn.filter(valor -> contiene(proveedor.positivos(), valor)).isPresent();
            boolean hosting = valorHosting.filter(valor -> contiene(proveedor.hostings(), valor)).isPresent();
            return new ResultadoVPN(true, vpn, hosting, proveedor.nombre());
        } catch (Exception excepcion) {
            if (plugin.ajustes().raiz().getBoolean("general.log-consola", true)) {
                plugin.getLogger().fine("Proveedor " + proveedor.nombre() + " fallo para " + ip + ": " + excepcion.getMessage());
            }
            return ResultadoVPN.sinDatos();
        }
    }

    private boolean contiene(List<String> valores, String valor) {
        for (String candidato : valores) {
            if (candidato.equalsIgnoreCase(valor)) {
                return true;
            }
        }
        return false;
    }

    /** Recorre el JSON siguiendo una ruta separada por puntos; %ip% es una clave literal. */
    private Optional<String> valorEn(JsonObject raiz, String ruta, String ip) {
        if (ruta == null || ruta.isBlank()) {
            return Optional.empty();
        }
        JsonElement actual = raiz;
        for (String parte : ruta.split("\\.")) {
            String clave = "%ip%".equals(parte) ? ip : parte;
            if (actual == null || !actual.isJsonObject()) {
                return Optional.empty();
            }
            actual = actual.getAsJsonObject().get(clave);
        }
        if (actual == null || actual.isJsonNull() || !actual.isJsonPrimitive()) {
            return Optional.empty();
        }
        return Optional.of(actual.getAsString());
    }

    // ------------------------------------------------------------- utiles

    @SuppressWarnings("unchecked")
    private <T> T valor(Map<?, ?> mapa, String clave, Class<T> tipo, T porDefecto) {
        Object valor = mapa.get(clave);
        return tipo.isInstance(valor) ? (T) valor : porDefecto;
    }

    private List<String> textos(Object valor) {
        if (!(valor instanceof List<?> lista)) {
            return List.of();
        }
        List<String> resultado = new ArrayList<>(lista.size());
        for (Object elemento : lista) {
            resultado.add(String.valueOf(elemento));
        }
        return List.copyOf(resultado);
    }

    private Map<String, String> cabeceras(Object valor) {
        if (!(valor instanceof Map<?, ?> mapa)) {
            return Map.of();
        }
        Map<String, String> resultado = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
            resultado.put(String.valueOf(entrada.getKey()), String.valueOf(entrada.getValue()));
        }
        return Map.copyOf(resultado);
    }

    /**
     * Añade o quita una IP de la whitelist editable desde el juego.
     * Se guarda en datos/whitelist-vpn.yml para no tocar los comentarios de config.yml.
     */
    public void whitelistIp(String ip, boolean anadir) {
        editarWhitelist("ips", ip, anadir);
        cache.remove(ip);
    }

    /** Añade o quita un jugador de la whitelist editable desde el juego. */
    public void whitelistJugador(String nombre, boolean anadir) {
        editarWhitelist("jugadores", nombre, anadir);
    }

    private void editarWhitelist(String seccion, String valor, boolean anadir) {
        YamlConfiguration configuracion = YamlConfiguration.loadConfiguration(archivoWhitelist);
        List<String> lista = new ArrayList<>(configuracion.getStringList(seccion));
        if (anadir) {
            if (!lista.contains(valor)) {
                lista.add(valor);
            }
        } else {
            lista.remove(valor);
        }
        configuracion.set(seccion, lista);
        try {
            File carpeta = archivoWhitelist.getParentFile();
            if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
                return;
            }
            configuracion.save(archivoWhitelist);
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo guardar whitelist-vpn.yml: " + excepcion.getMessage());
        }
        cargarWhitelistExtra();
    }

    private void cargarWhitelistExtra() {
        if (!archivoWhitelist.exists()) {
            ipsExtra = List.of();
            jugadoresExtra = List.of();
            return;
        }
        YamlConfiguration configuracion = YamlConfiguration.loadConfiguration(archivoWhitelist);
        ipsExtra = List.copyOf(configuracion.getStringList("ips"));
        jugadoresExtra = List.copyOf(configuracion.getStringList("jugadores"));
    }

    /** Lee la seccion de proveedores tal cual, por si algun comando la necesita. */
    public ConfigurationSection seccion() {
        return plugin.ajustes().raiz().getConfigurationSection("antivpn");
    }
}
