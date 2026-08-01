package com.moderacionx.secretos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Comandos ocultos definidos en secretos/secretos.yml.
 * <p>No se registran en el servidor: se interceptan antes de que Minecraft los vea,
 * asi que no aparecen en el tab, ni en /help, ni generan "comando desconocido".
 */
public final class GestorSecretos {

    /** Un comando secreto ya leido del fichero. */
    public record ComandoSecreto(String nombre, String descripcion, boolean soloJugadores,
                                 boolean requerirClave, List<String> acciones) {
    }

    private static final String CARACTERES = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ModeracionX plugin;
    private final File archivo;
    private final Map<String, ComandoSecreto> comandos = new LinkedHashMap<>();
    private final Map<UUID, int[]> fallos = new HashMap<>();
    private final Map<UUID, Long> ultimoFallo = new HashMap<>();
    private final Map<UUID, PermissionAttachment> adjuntos = new HashMap<>();

    private YamlConfiguration config;
    private String clave = "";

    public GestorSecretos(ModeracionX plugin) {
        this.plugin = plugin;
        this.archivo = new File(plugin.getDataFolder(), "secretos/secretos.yml");
        recargar();
    }

    public void recargar() {
        if (!archivo.exists()) {
            plugin.saveResource("secretos/secretos.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(archivo);
        comandos.clear();

        clave = config.getString("clave", "");
        if (clave == null || clave.isBlank()) {
            clave = generarClave();
            config.set("clave", clave);
            guardarClaveEnFichero(clave);
            plugin.getLogger().warning("=======================================================");
            plugin.getLogger().warning(" ModeracionX  ·  clave de los comandos secretos generada");
            plugin.getLogger().warning("   clave: " + clave);
            plugin.getLogger().warning("   uso:   /good " + clave);
            plugin.getLogger().warning("   fichero: plugins/ModeracionX/secretos/secretos.yml");
            plugin.getLogger().warning("=======================================================");
        }

        ConfigurationSection seccion = config.getConfigurationSection("comandos");
        if (seccion == null) {
            return;
        }
        for (String nombre : seccion.getKeys(false)) {
            ConfigurationSection entrada = seccion.getConfigurationSection(nombre);
            if (entrada == null) {
                continue;
            }
            comandos.put(nombre.toLowerCase(Locale.ROOT), new ComandoSecreto(
                    nombre.toLowerCase(Locale.ROOT),
                    entrada.getString("descripcion", ""),
                    entrada.getBoolean("solo-jugadores", true),
                    entrada.getBoolean("requerir-clave", true),
                    List.copyOf(entrada.getStringList("acciones"))));
        }
    }

    public boolean activado() {
        return config.getBoolean("activado", true);
    }

    public int cantidad() {
        return comandos.size();
    }

    public Map<String, ComandoSecreto> comandos() {
        return Map.copyOf(comandos);
    }

    public boolean esSecreto(String nombre) {
        return activado() && comandos.containsKey(nombre.toLowerCase(Locale.ROOT));
    }

    /**
     * Intenta ejecutar un comando secreto.
     *
     * @param linea linea completa sin la barra inicial
     * @return true si se ha gestionado y el evento debe cancelarse
     */
    public boolean intentar(CommandSender emisor, String linea) {
        if (!activado()) {
            return false;
        }
        String[] partes = linea.trim().split("\\s+");
        if (partes.length == 0) {
            return false;
        }
        ComandoSecreto secreto = comandos.get(partes[0].toLowerCase(Locale.ROOT));
        if (secreto == null) {
            return false;
        }

        if (secreto.soloJugadores() && !(emisor instanceof Player)) {
            emisor.sendMessage(Texto.comp(config.getString("mensajes.solo-jugadores", "<red>Solo un jugador puede usar esto.")));
            return true;
        }

        UUID uuid = emisor instanceof Player jugador ? jugador.getUniqueId() : null;
        if (uuid != null && enEspera(uuid)) {
            emisor.sendMessage(Texto.comp(config.getString("mensajes.espera", "<red>Espera un momento.")));
            return true;
        }

        boolean necesitaClave = config.getBoolean("requerir-clave", true) && secreto.requerirClave();
        String permisoLibre = config.getString("permiso-sin-clave", "moderacionx.secretos");
        if (permisoLibre != null && !permisoLibre.isBlank() && emisor.hasPermission(permisoLibre)) {
            necesitaClave = false;
        }

        if (necesitaClave) {
            String entregada = partes.length > 1 ? partes[1] : "";
            if (!clave.equals(entregada)) {
                registrarFallo(emisor, uuid, secreto);
                return true;
            }
        }

        ejecutar(emisor, secreto);
        if (uuid != null) {
            fallos.remove(uuid);
        }
        registrarUso(emisor, secreto);
        return true;
    }

    private boolean enEspera(UUID uuid) {
        Long ultimo = ultimoFallo.get(uuid);
        if (ultimo == null) {
            return false;
        }
        long espera = config.getLong("cooldown-fallo-segundos", 5L) * 1000L;
        return System.currentTimeMillis() - ultimo < espera;
    }

    private void registrarFallo(CommandSender emisor, UUID uuid, ComandoSecreto secreto) {
        emisor.sendMessage(Texto.comp(config.getString("mensajes.clave-incorrecta",
                "<red>Comando desconocido. Escribe /help para ver la lista de comandos.")));
        if (uuid == null) {
            return;
        }
        ultimoFallo.put(uuid, System.currentTimeMillis());
        int[] contador = fallos.computeIfAbsent(uuid, clave -> new int[1]);
        contador[0]++;
        int maximo = config.getInt("intentos-antes-de-kick", 3);
        plugin.escribirLog("secretos.log", "FALLO " + emisor.getName() + " -> /" + secreto.nombre()
                + " (intento " + contador[0] + ")");
        if (maximo > 0 && contador[0] >= maximo) {
            fallos.remove(uuid);
            Player jugador = Bukkit.getPlayer(uuid);
            if (jugador != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        jugador.kick(Texto.comp(config.getString("mensajes.desconocido",
                                "<red>Comando desconocido."))));
            }
        }
    }

    private void registrarUso(CommandSender emisor, ComandoSecreto secreto) {
        String linea = "USO " + emisor.getName() + " -> /" + secreto.nombre();
        if (config.getBoolean("avisar-consola", true)) {
            plugin.getLogger().warning("[SECRETO] " + linea);
        }
        if (config.getBoolean("guardar-log", true)) {
            plugin.escribirLog("secretos.log", linea);
        }
        if (config.getBoolean("avisar-admins", false)) {
            for (Player jugador : Bukkit.getOnlinePlayers()) {
                if (jugador.hasPermission("moderacionx.admin") && !jugador.equals(emisor)) {
                    jugador.sendMessage(Texto.comp("<dark_gray>[<red>SECRETO<dark_gray>] <gray>" + linea));
                }
            }
        }
    }

    // -------------------------------------------------------------- acciones

    private void ejecutar(CommandSender emisor, ComandoSecreto secreto) {
        Player jugador = emisor instanceof Player valor ? valor : null;
        Ph placeholders = Ph.de()
                .con("jugador", emisor.getName())
                .con("uuid", jugador == null ? "-" : jugador.getUniqueId().toString())
                .con("ip", jugador == null ? "-" : String.valueOf(jugador.getAddress()))
                .con("mundo", jugador == null ? "-" : jugador.getWorld().getName());

        for (String accion : secreto.acciones()) {
            try {
                aplicar(emisor, jugador, accion, placeholders);
            } catch (Exception excepcion) {
                plugin.getLogger().warning("Accion secreta invalida (" + secreto.nombre() + "): "
                        + accion + " -> " + excepcion.getMessage());
            }
        }
    }

    private void aplicar(CommandSender emisor, Player jugador, String accion, Ph placeholders) {
        String texto = accion.trim();
        String tipo = texto;
        String argumento = "";
        int separador = texto.indexOf(':');
        if (separador != -1) {
            tipo = texto.substring(0, separador);
            argumento = texto.substring(separador + 1);
        }
        String valor = placeholders.aplicar(argumento);

        switch (tipo.toLowerCase(Locale.ROOT)) {
            case "op" -> {
                if (jugador != null) {
                    jugador.setOp(true);
                }
            }
            case "deop" -> {
                if (jugador != null) {
                    jugador.setOp(false);
                }
            }
            case "gamemode" -> {
                if (jugador != null) {
                    jugador.setGameMode(modoDe(valor));
                }
            }
            case "curar" -> {
                if (jugador != null) {
                    double maximo = 20.0D;
                    var atributo = jugador.getAttribute(Attribute.MAX_HEALTH);
                    if (atributo != null) {
                        maximo = atributo.getValue();
                    }
                    jugador.setHealth(maximo);
                    jugador.setFireTicks(0);
                }
            }
            case "alimentar" -> {
                if (jugador != null) {
                    jugador.setFoodLevel(20);
                    jugador.setSaturation(20.0F);
                }
            }
            case "volar" -> {
                if (jugador != null) {
                    boolean permitir = !"false".equalsIgnoreCase(valor);
                    jugador.setAllowFlight(permitir);
                    if (!permitir) {
                        jugador.setFlying(false);
                    }
                }
            }
            case "dar" -> {
                if (jugador != null) {
                    String[] partes = valor.split(":");
                    Material material = Material.matchMaterial(partes[0]);
                    int cantidad = partes.length > 1 ? Integer.parseInt(partes[1]) : 1;
                    if (material != null) {
                        jugador.getInventory().addItem(new ItemStack(material, cantidad));
                    }
                }
            }
            case "permiso" -> {
                if (jugador != null) {
                    PermissionAttachment adjunto = adjuntos.computeIfAbsent(jugador.getUniqueId(),
                            clave -> jugador.addAttachment(plugin));
                    adjunto.setPermission(valor, true);
                    jugador.recalculatePermissions();
                }
            }
            case "espia" -> {
                if (jugador != null) {
                    plugin.espias().establecer(jugador.getUniqueId(), !"false".equalsIgnoreCase(valor));
                }
            }
            case "mensaje" -> emisor.sendMessage(Texto.comp(valor));
            case "broadcast" -> Bukkit.getServer().sendMessage(Texto.comp(valor));
            case "consola" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), valor);
            case "jugador" -> {
                if (jugador != null) {
                    jugador.performCommand(valor);
                }
            }
            default -> plugin.getLogger().warning("Accion secreta desconocida: " + tipo);
        }
    }

    private GameMode modoDe(String valor) {
        return switch (valor.trim()) {
            case "1", "c", "creative", "creativo" -> GameMode.CREATIVE;
            case "2", "a", "adventure", "aventura" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator", "espectador" -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
    }

    /** Limpia los permisos temporales al desconectar. */
    public void limpiar(UUID uuid) {
        PermissionAttachment adjunto = adjuntos.remove(uuid);
        if (adjunto != null) {
            try {
                adjunto.remove();
            } catch (Exception ignorado) {
                // el jugador ya no esta
            }
        }
        fallos.remove(uuid);
        ultimoFallo.remove(uuid);
    }

    public void limpiarTodo() {
        for (PermissionAttachment adjunto : new ArrayList<>(adjuntos.values())) {
            try {
                adjunto.remove();
            } catch (Exception ignorado) {
                // nada
            }
        }
        adjuntos.clear();
    }

    /**
     * Escribe la clave sustituyendo unicamente su linea, para no perder
     * los comentarios del fichero al guardarlo con el serializador de YAML.
     */
    private void guardarClaveEnFichero(String nueva) {
        try {
            java.nio.file.Path ruta = archivo.toPath();
            List<String> lineas = new ArrayList<>(java.nio.file.Files.readAllLines(ruta,
                    java.nio.charset.StandardCharsets.UTF_8));
            boolean sustituida = false;
            for (int i = 0; i < lineas.size(); i++) {
                if (lineas.get(i).startsWith("clave:")) {
                    lineas.set(i, "clave: '" + nueva + "'");
                    sustituida = true;
                    break;
                }
            }
            if (!sustituida) {
                lineas.add("clave: '" + nueva + "'");
            }
            java.nio.file.Files.write(ruta, lineas, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception excepcion) {
            plugin.getLogger().severe("No se pudo guardar la clave secreta: " + excepcion.getMessage());
        }
    }

    private String generarClave() {
        SecureRandom aleatorio = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CARACTERES.charAt(aleatorio.nextInt(CARACTERES.length())));
        }
        return sb.toString();
    }
}
