package com.moderacionx.bloques;

import com.moderacionx.ModeracionX;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registro de bloques rotos y colocados, estilo CoreProtect.
 * <p>Usa su propia base de datos SQLite (datos/bloques.db) porque el volumen es alto.
 * Los cambios se acumulan en una cola y se escriben en lotes en segundo plano.
 * <p>IMPORTANTE: solo registra desde que el plugin esta instalado. No hay forma de
 * saber que paso con los bloques antes de instalarlo.
 */
public final class GestorBloques {

    private final ModeracionX plugin;
    private final File archivo;
    private Connection conexion;
    private boolean disponible;

    private final ConcurrentLinkedQueue<RegistroBloque> pendientes = new ConcurrentLinkedQueue<>();
    private final Set<UUID> inspectores = new CopyOnWriteArraySet<>();

    public GestorBloques(ModeracionX plugin) {
        this.plugin = plugin;
        this.archivo = new File(plugin.getDataFolder(), "datos/bloques.db");
        inicializar();
    }

    private void inicializar() {
        if (!plugin.ajustes().raiz().getBoolean("bloques.activado", true)) {
            return;
        }
        try {
            File carpeta = archivo.getParentFile();
            if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
                throw new IllegalStateException("no se pudo crear " + carpeta);
            }
            Class.forName("org.sqlite.JDBC");
            conexion = DriverManager.getConnection("jdbc:sqlite:" + archivo.getAbsolutePath());
            try (Statement st = conexion.createStatement()) {
                st.executeUpdate("PRAGMA journal_mode=WAL;");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS mx_bloques (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            uuid TEXT,
                            nombre TEXT NOT NULL,
                            accion INTEGER NOT NULL,
                            material TEXT NOT NULL,
                            mundo TEXT NOT NULL,
                            x INTEGER NOT NULL,
                            y INTEGER NOT NULL,
                            z INTEGER NOT NULL,
                            fecha INTEGER NOT NULL
                        );""");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_blq_pos ON mx_bloques(mundo,x,y,z);");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_blq_uuid ON mx_bloques(uuid);");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_blq_fecha ON mx_bloques(fecha);");
            }
            disponible = true;
            plugin.getLogger().info("Registro de bloques listo: " + archivo.getName());
        } catch (Exception excepcion) {
            disponible = false;
            plugin.getLogger().warning("No se pudo iniciar el registro de bloques (" + excepcion.getMessage()
                    + "). El logging de bloques queda desactivado.");
        }
    }

    public boolean disponible() {
        return disponible && plugin.ajustes().raiz().getBoolean("bloques.activado", true);
    }

    // ------------------------------------------------------------- inspector

    public boolean inspecciona(UUID uuid) {
        return inspectores.contains(uuid);
    }

    /** Activa o desactiva el inspector. Devuelve el estado resultante. */
    public boolean alternarInspector(UUID uuid) {
        if (inspectores.remove(uuid)) {
            return false;
        }
        inspectores.add(uuid);
        return true;
    }

    public void olvidar(UUID uuid) {
        inspectores.remove(uuid);
    }

    // -------------------------------------------------------------- registrar

    public void registrar(Player jugador, int accion, org.bukkit.Material material, Block bloque) {
        if (!disponible()) {
            return;
        }
        if (accion == RegistroBloque.ROTURA && !plugin.ajustes().raiz().getBoolean("bloques.registrar-roturas", true)) {
            return;
        }
        if (accion == RegistroBloque.COLOCACION && !plugin.ajustes().raiz().getBoolean("bloques.registrar-colocaciones", true)) {
            return;
        }
        pendientes.add(RegistroBloque.nuevo(
                jugador == null ? null : jugador.getUniqueId(),
                jugador == null ? "?" : jugador.getName(),
                accion, material.name(),
                bloque.getWorld().getName(), bloque.getX(), bloque.getY(), bloque.getZ()));

        int tope = Math.max(1, plugin.ajustes().raiz().getInt("bloques.guardar-cada", 50));
        if (pendientes.size() >= tope) {
            volcar();
        }
    }

    public void volcar() {
        if (!disponible() || pendientes.isEmpty()) {
            return;
        }
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::volcarAhora);
        } else {
            volcarAhora();
        }
    }

    /** Escribe la cola en el hilo actual. Llamar antes de consultar; siempre en hilo asincrono. */
    public synchronized void volcarAhora() {
        if (!disponible) {
            return;
        }
        List<RegistroBloque> lote = new ArrayList<>();
        RegistroBloque registro;
        while ((registro = pendientes.poll()) != null) {
            lote.add(registro);
        }
        if (lote.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO mx_bloques (uuid, nombre, accion, material, mundo, x, y, z, fecha) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        try {
            boolean autocommit = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                for (RegistroBloque r : lote) {
                    ps.setString(1, r.uuid() == null ? null : r.uuid().toString());
                    ps.setString(2, r.nombre());
                    ps.setInt(3, r.accion());
                    ps.setString(4, r.material());
                    ps.setString(5, r.mundo());
                    ps.setInt(6, r.x());
                    ps.setInt(7, r.y());
                    ps.setInt(8, r.z());
                    ps.setLong(9, r.fecha());
                    ps.addBatch();
                }
                ps.executeBatch();
                conexion.commit();
            } finally {
                conexion.setAutoCommit(autocommit);
            }
        } catch (SQLException excepcion) {
            plugin.getLogger().warning("No se pudo guardar el registro de bloques: " + excepcion.getMessage());
        }
    }

    // --------------------------------------------------------------- consultas

    /** Historial de un bloque concreto, de mas reciente a mas antiguo. */
    public synchronized List<RegistroBloque> historialEn(String mundo, int x, int y, int z, int limite) {
        List<RegistroBloque> resultado = new ArrayList<>();
        if (!disponible) {
            return resultado;
        }
        String sql = "SELECT id, uuid, nombre, accion, material, mundo, x, y, z, fecha FROM mx_bloques "
                + "WHERE mundo=? AND x=? AND y=? AND z=? ORDER BY fecha DESC LIMIT ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, mundo);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setInt(5, limite);
            leer(ps, resultado);
        } catch (SQLException excepcion) {
            plugin.getLogger().warning("Error consultando el bloque: " + excepcion.getMessage());
        }
        return resultado;
    }

    /** Historial de lo que ha hecho un jugador, de mas reciente a mas antiguo. */
    public synchronized List<RegistroBloque> historialJugador(UUID uuid, long desde, int limite, int offset) {
        List<RegistroBloque> resultado = new ArrayList<>();
        if (!disponible) {
            return resultado;
        }
        String sql = "SELECT id, uuid, nombre, accion, material, mundo, x, y, z, fecha FROM mx_bloques "
                + "WHERE uuid=? AND fecha>=? ORDER BY fecha DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, desde);
            ps.setInt(3, limite);
            ps.setInt(4, offset);
            leer(ps, resultado);
        } catch (SQLException excepcion) {
            plugin.getLogger().warning("Error consultando el jugador: " + excepcion.getMessage());
        }
        return resultado;
    }

    public synchronized int contarJugador(UUID uuid, long desde) {
        if (!disponible) {
            return 0;
        }
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT COUNT(*) FROM mx_bloques WHERE uuid=? AND fecha>=?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, desde);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException excepcion) {
            return 0;
        }
    }

    public synchronized int total() {
        if (!disponible) {
            return 0;
        }
        try (PreparedStatement ps = conexion.prepareStatement("SELECT COUNT(*) FROM mx_bloques");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException excepcion) {
            return 0;
        }
    }

    private void leer(PreparedStatement ps, List<RegistroBloque> destino) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String u = rs.getString("uuid");
                destino.add(new RegistroBloque(
                        rs.getLong("id"),
                        u == null ? null : UUID.fromString(u),
                        rs.getString("nombre"),
                        rs.getInt("accion"),
                        rs.getString("material"),
                        rs.getString("mundo"),
                        rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                        rs.getLong("fecha")));
            }
        }
    }

    /** Borra lo mas viejo que los dias configurados. */
    public void purgar() {
        int dias = plugin.ajustes().raiz().getInt("bloques.dias", 30);
        if (!disponible() || dias <= 0) {
            return;
        }
        long limite = System.currentTimeMillis() - (long) dias * 86_400_000L;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = conexion.prepareStatement("DELETE FROM mx_bloques WHERE fecha < ?")) {
                ps.setLong(1, limite);
                int quitados = ps.executeUpdate();
                if (quitados > 0) {
                    plugin.getLogger().info("Registro de bloques: " + quitados + " entradas antiguas borradas.");
                }
            } catch (SQLException excepcion) {
                plugin.getLogger().warning("No se pudo purgar el registro de bloques: " + excepcion.getMessage());
            }
        });
    }

    public void cerrar() {
        volcarAhora();
        if (conexion != null) {
            try {
                conexion.close();
            } catch (SQLException ignorado) {
                // nada
            }
            conexion = null;
        }
    }
}
