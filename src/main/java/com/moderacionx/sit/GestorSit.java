package com.moderacionx.sit;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Sentarse en los bloques, con zonas donde queda prohibido. */
public final class GestorSit {

    private final ModeracionX plugin;
    private final NamespacedKey marca;
    private final File archivoZonas;

    private final Map<UUID, Asiento> asientos = new ConcurrentHashMap<>();
    private final List<ZonaSit> zonas = new CopyOnWriteArrayList<>();
    /** Selecciones de /sit zona pos1 y pos2. */
    private final Map<UUID, Location[]> selecciones = new ConcurrentHashMap<>();

    public GestorSit(ModeracionX plugin) {
        this.plugin = plugin;
        this.marca = new NamespacedKey(plugin, "asiento");
        this.archivoZonas = new File(plugin.getDataFolder(), "datos/zonas-sit.yml");
        cargarZonas();
        limpiarSillasHuerfanas();
    }

    private org.bukkit.configuration.file.FileConfiguration config() {
        return plugin.ajustes().raiz();
    }

    public boolean activado() {
        return config().getBoolean("sit.activado", true);
    }

    public boolean sentado(UUID uuid) {
        return asientos.containsKey(uuid);
    }

    public int sentados() {
        return asientos.size();
    }

    public List<ZonaSit> zonas() {
        return List.copyOf(zonas);
    }

    // ------------------------------------------------------------- sentarse

    /** Motivo por el que no se puede uno sentar, o null si si se puede. */
    public String motivoNo(Player jugador, Block bloque) {
        if (!activado()) {
            return "sit.desactivado";
        }
        for (String mundo : config().getStringList("sit.mundos-prohibidos")) {
            if (mundo.equalsIgnoreCase(jugador.getWorld().getName())) {
                return "sit.mundo-prohibido";
            }
        }
        if (zonaDe(bloque.getLocation()).isPresent() || zonaDe(jugador.getLocation()).isPresent()) {
            return "sit.zona-prohibida";
        }
        if (jugador.isInsideVehicle()) {
            return "sit.ya-montado";
        }
        if (jugador.isGliding() || jugador.isSwimming()) {
            return "sit.no-ahora";
        }
        if (bloque.isEmpty() || bloque.isLiquid() || bloque.isPassable()) {
            return "sit.bloque-invalido";
        }

        List<String> soloBloques = config().getStringList("sit.solo-bloques");
        if (!soloBloques.isEmpty() && !contiene(soloBloques, bloque.getType())) {
            return "sit.bloque-invalido";
        }
        if (contiene(config().getStringList("sit.bloques-prohibidos"), bloque.getType())) {
            return "sit.bloque-prohibido";
        }
        if (!bloque.getRelative(0, 1, 0).isPassable()) {
            return "sit.sin-espacio";
        }

        double distancia = config().getDouble("sit.distancia-maxima", 3.0D);
        if (jugador.getLocation().distance(bloque.getLocation().add(0.5, 0.5, 0.5)) > distancia) {
            return "sit.lejos";
        }
        return null;
    }

    private boolean contiene(List<String> lista, Material material) {
        for (String nombre : lista) {
            if (nombre.equalsIgnoreCase(material.name())) {
                return true;
            }
        }
        return false;
    }

    /** Sienta al jugador en el bloque. Devuelve null si salio bien, o la ruta del mensaje de error. */
    public String sentar(Player jugador, Block bloque) {
        String motivo = motivoNo(jugador, bloque);
        if (motivo != null) {
            return motivo;
        }

        // hay que guardarla antes de montarlo: al sentarse el servidor lo mueve al asiento
        Location origen = jugador.getLocation().clone();

        BoundingBox caja = bloque.getBoundingBox();
        double tope = caja.getMaxY();
        double altura = config().getDouble("sit.altura", 0.25D);

        float yaw = config().getBoolean("sit.mantener-mirada", true) ? jugador.getLocation().getYaw() : 0f;
        Location asiento = new Location(bloque.getWorld(),
                bloque.getX() + 0.5D, tope + altura, bloque.getZ() + 0.5D, yaw, 0f);

        ArmorStand silla = bloque.getWorld().spawn(asiento, ArmorStand.class, entidad -> {
            entidad.setVisible(false);
            entidad.setGravity(false);
            entidad.setMarker(true);
            entidad.setSmall(true);
            entidad.setBasePlate(false);
            entidad.setArms(false);
            entidad.setInvulnerable(true);
            entidad.setSilent(true);
            entidad.setPersistent(false);
            entidad.setCanPickupItems(false);
            entidad.setCollidable(false);
            entidad.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
        });

        if (!silla.addPassenger(jugador)) {
            silla.remove();
            return "sit.error";
        }
        asientos.put(jugador.getUniqueId(), new Asiento(silla, origen));
        return null;
    }

    /** Levanta al jugador y quita la silla. */
    public void levantar(Player jugador) {
        Asiento asiento = asientos.remove(jugador.getUniqueId());
        if (asiento == null) {
            return;
        }
        ArmorStand silla = asiento.silla();
        Location destino = asiento.origen().clone();
        if (silla.isValid()) {
            silla.eject();
            silla.remove();
        }
        if (jugador.isOnline()) {
            destino.setYaw(jugador.getLocation().getYaw());
            destino.setPitch(jugador.getLocation().getPitch());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (jugador.isOnline() && !jugador.isInsideVehicle()) {
                    jugador.teleport(destino);
                }
            });
        }
    }

    /** ¿Esta entidad es una de nuestras sillas? */
    public boolean esSilla(Entity entidad) {
        return entidad instanceof ArmorStand
                && entidad.getPersistentDataContainer().has(marca, PersistentDataType.BYTE);
    }

    public Optional<UUID> ocupanteDe(Entity silla) {
        for (Map.Entry<UUID, Asiento> entrada : asientos.entrySet()) {
            if (entrada.getValue().silla().equals(silla)) {
                return Optional.of(entrada.getKey());
            }
        }
        return Optional.empty();
    }

    /** Repasa que no queden asientos rotos (chunk descargado, entidad muerta...). */
    public void revisar() {
        for (Map.Entry<UUID, Asiento> entrada : Map.copyOf(asientos).entrySet()) {
            Player jugador = Bukkit.getPlayer(entrada.getKey());
            ArmorStand silla = entrada.getValue().silla();
            if (jugador == null || !jugador.isOnline() || !silla.isValid() || !silla.equals(jugador.getVehicle())) {
                asientos.remove(entrada.getKey());
                if (silla.isValid()) {
                    silla.remove();
                }
            }
        }
    }

    /** Quita todas las sillas (al apagar el plugin). */
    public void limpiarTodo() {
        for (Asiento asiento : asientos.values()) {
            if (asiento.silla().isValid()) {
                asiento.silla().eject();
                asiento.silla().remove();
            }
        }
        asientos.clear();
    }

    /** Borra sillas que hayan quedado sueltas tras un cierre brusco. */
    private void limpiarSillasHuerfanas() {
        int quitadas = 0;
        for (World mundo : Bukkit.getWorlds()) {
            for (ArmorStand entidad : mundo.getEntitiesByClass(ArmorStand.class)) {
                if (entidad.getPersistentDataContainer().has(marca, PersistentDataType.BYTE)) {
                    entidad.remove();
                    quitadas++;
                }
            }
        }
        if (quitadas > 0) {
            plugin.getLogger().info("Asientos sueltos eliminados: " + quitadas);
        }
    }

    // ---------------------------------------------------------------- zonas

    public Optional<ZonaSit> zonaDe(Location ubicacion) {
        for (ZonaSit zona : zonas) {
            if (zona.contiene(ubicacion)) {
                return Optional.of(zona);
            }
        }
        return Optional.empty();
    }

    public void seleccionar(Player jugador, boolean primera) {
        Location[] puntos = selecciones.computeIfAbsent(jugador.getUniqueId(), clave -> new Location[2]);
        puntos[primera ? 0 : 1] = jugador.getLocation().getBlock().getLocation();
    }

    public Location[] seleccion(Player jugador) {
        return selecciones.get(jugador.getUniqueId());
    }

    public boolean crearZona(Player jugador, String nombre) {
        Location[] puntos = selecciones.get(jugador.getUniqueId());
        if (puntos == null || puntos[0] == null || puntos[1] == null) {
            return false;
        }
        if (puntos[0].getWorld() == null || !puntos[0].getWorld().equals(puntos[1].getWorld())) {
            return false;
        }
        zonas.removeIf(zona -> zona.nombre().equalsIgnoreCase(nombre));
        zonas.add(ZonaSit.de(nombre, puntos[0].getWorld().getName(), puntos[0], puntos[1]));
        guardarZonas();
        return true;
    }

    public boolean borrarZona(String nombre) {
        boolean borrada = zonas.removeIf(zona -> zona.nombre().equalsIgnoreCase(nombre));
        if (borrada) {
            guardarZonas();
        }
        return borrada;
    }

    public void anadirZona(ZonaSit zona) {
        zonas.removeIf(existente -> existente.nombre().equalsIgnoreCase(zona.nombre()));
        zonas.add(zona);
    }

    public void cargarZonas() {
        zonas.clear();
        if (!archivoZonas.exists()) {
            return;
        }
        YamlConfiguration configuracion = YamlConfiguration.loadConfiguration(archivoZonas);
        ConfigurationSection raiz = configuracion.getConfigurationSection("zonas");
        if (raiz == null) {
            return;
        }
        for (String nombre : raiz.getKeys(false)) {
            ConfigurationSection seccion = raiz.getConfigurationSection(nombre);
            if (seccion == null) {
                continue;
            }
            zonas.add(new ZonaSit(nombre,
                    seccion.getString("mundo", "world"),
                    seccion.getInt("min-x"), seccion.getInt("min-y"), seccion.getInt("min-z"),
                    seccion.getInt("max-x"), seccion.getInt("max-y"), seccion.getInt("max-z")));
        }
    }

    public void guardarZonas() {
        YamlConfiguration configuracion = new YamlConfiguration();
        for (ZonaSit zona : zonas) {
            String base = "zonas." + zona.nombre();
            configuracion.set(base + ".mundo", zona.mundo());
            configuracion.set(base + ".min-x", zona.minX());
            configuracion.set(base + ".min-y", zona.minY());
            configuracion.set(base + ".min-z", zona.minZ());
            configuracion.set(base + ".max-x", zona.maxX());
            configuracion.set(base + ".max-y", zona.maxY());
            configuracion.set(base + ".max-z", zona.maxZ());
        }
        try {
            File carpeta = archivoZonas.getParentFile();
            if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
                return;
            }
            configuracion.save(archivoZonas);
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudo guardar zonas-sit.yml: " + excepcion.getMessage());
        }
    }

    // ------------------------------------------------------------ importar

    /** Una arena encontrada al escanear la carpeta de otro plugin. */
    public record Encontrada(String nombre, ZonaSit zona) {
    }

    /**
     * Busca arenas en la carpeta de otro plugin (BlockBall y similares) y las
     * convierte en zonas prohibidas.
     * <p>No depende de los nombres de las claves: recoge todas las coordenadas
     * x/y/z de cada fichero y se queda con la caja que las envuelve.
     */
    public List<Encontrada> importar(String nombrePlugin, int margen) {
        List<Encontrada> encontradas = new ArrayList<>();
        File carpeta = new File(plugin.getDataFolder().getParentFile(), nombrePlugin);
        if (!carpeta.isDirectory()) {
            return encontradas;
        }
        escanear(carpeta, encontradas, margen);
        return encontradas;
    }

    private void escanear(File carpeta, List<Encontrada> encontradas, int margen) {
        File[] ficheros = carpeta.listFiles();
        if (ficheros == null) {
            return;
        }
        for (File fichero : ficheros) {
            if (fichero.isDirectory()) {
                escanear(fichero, encontradas, margen);
                continue;
            }
            String nombre = fichero.getName().toLowerCase(Locale.ROOT);
            if (!nombre.endsWith(".yml") && !nombre.endsWith(".yaml") && !nombre.endsWith(".json")) {
                continue;
            }
            try {
                leerArena(fichero, encontradas, margen);
            } catch (Exception excepcion) {
                plugin.getLogger().warning("No se pudo leer " + fichero.getName() + ": " + excepcion.getMessage());
            }
        }
    }

    private void leerArena(File fichero, List<Encontrada> encontradas, int margen) throws Exception {
        List<double[]> puntos = new ArrayList<>();
        List<String> mundos = new ArrayList<>();

        if (fichero.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
            com.google.gson.JsonElement raiz = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(fichero.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            recorrerJson(raiz, puntos, mundos);
        } else {
            YamlConfiguration configuracion = YamlConfiguration.loadConfiguration(fichero);
            recorrerYaml(configuracion.getValues(true), puntos, mundos);
        }

        if (puntos.size() < 2 || mundos.isEmpty()) {
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (double[] punto : puntos) {
            minX = Math.min(minX, punto[0]);
            minY = Math.min(minY, punto[1]);
            minZ = Math.min(minZ, punto[2]);
            maxX = Math.max(maxX, punto[0]);
            maxY = Math.max(maxY, punto[1]);
            maxZ = Math.max(maxZ, punto[2]);
        }

        String nombre = fichero.getName().replaceAll("\\.(yml|yaml|json)$", "");
        encontradas.add(new Encontrada(nombre, new ZonaSit(nombre, mundos.get(0),
                (int) Math.floor(minX) - margen, (int) Math.floor(minY) - margen, (int) Math.floor(minZ) - margen,
                (int) Math.ceil(maxX) + margen, (int) Math.ceil(maxY) + margen, (int) Math.ceil(maxZ) + margen)));
    }

    @SuppressWarnings("unchecked")
    private void recorrerYaml(Map<String, Object> valores, List<double[]> puntos, List<String> mundos) {
        Map<String, Double> coordenadas = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entrada : valores.entrySet()) {
            String clave = entrada.getKey();
            Object valor = entrada.getValue();
            String hoja = clave.contains(".") ? clave.substring(clave.lastIndexOf('.') + 1) : clave;
            String padre = clave.contains(".") ? clave.substring(0, clave.lastIndexOf('.')) : "";

            if (valor instanceof Number numero && List.of("x", "y", "z").contains(hoja.toLowerCase(Locale.ROOT))) {
                coordenadas.put(padre + "#" + hoja.toLowerCase(Locale.ROOT), numero.doubleValue());
            } else if (valor instanceof String texto
                    && List.of("world", "worldname", "mundo").contains(hoja.toLowerCase(Locale.ROOT))
                    && !texto.isBlank() && !mundos.contains(texto)) {
                mundos.add(texto);
            } else if (valor instanceof Map<?, ?> mapa) {
                recorrerYaml((Map<String, Object>) mapa, puntos, mundos);
            }
        }
        java.util.Set<String> padres = new java.util.HashSet<>();
        for (String clave : coordenadas.keySet()) {
            padres.add(clave.substring(0, clave.indexOf('#')));
        }
        for (String padre : padres) {
            Double x = coordenadas.get(padre + "#x");
            Double y = coordenadas.get(padre + "#y");
            Double z = coordenadas.get(padre + "#z");
            if (x != null && y != null && z != null) {
                puntos.add(new double[]{x, y, z});
            }
        }
    }

    private void recorrerJson(com.google.gson.JsonElement elemento, List<double[]> puntos, List<String> mundos) {
        if (elemento == null || elemento.isJsonNull()) {
            return;
        }
        if (elemento.isJsonArray()) {
            for (com.google.gson.JsonElement hijo : elemento.getAsJsonArray()) {
                recorrerJson(hijo, puntos, mundos);
            }
            return;
        }
        if (!elemento.isJsonObject()) {
            return;
        }
        com.google.gson.JsonObject objeto = elemento.getAsJsonObject();
        Double x = numero(objeto, "x");
        Double y = numero(objeto, "y");
        Double z = numero(objeto, "z");
        if (x != null && y != null && z != null) {
            puntos.add(new double[]{x, y, z});
        }
        for (String clave : List.of("world", "worldName", "worldname", "mundo")) {
            if (objeto.has(clave) && objeto.get(clave).isJsonPrimitive()) {
                String mundo = objeto.get(clave).getAsString();
                if (!mundo.isBlank() && !mundos.contains(mundo)) {
                    mundos.add(mundo);
                }
            }
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entrada : objeto.entrySet()) {
            recorrerJson(entrada.getValue(), puntos, mundos);
        }
    }

    private Double numero(com.google.gson.JsonObject objeto, String clave) {
        if (!objeto.has(clave) || !objeto.get(clave).isJsonPrimitive()) {
            return null;
        }
        try {
            return objeto.get(clave).getAsDouble();
        } catch (Exception excepcion) {
            return null;
        }
    }

    public Ph datosZona(ZonaSit zona) {
        return Ph.de().con("zona", zona.nombre()).con("coordenadas", zona.coordenadas());
    }
}
