package com.moderacionx.almacen;

import com.moderacionx.ModeracionX;
import com.moderacionx.espia.RegistroComando;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Persistencia en SQLite (una sola conexion, accesos sincronizados). */
public final class AlmacenSQLite implements Almacen {

    private static final String COLUMNAS =
            "id, tipo, objetivo_uuid, objetivo_nombre, objetivo_ip, autor_uuid, autor_nombre, "
                    + "razon, creada, expira, activa, removida_por, removida_en, razon_remocion";

    private final ModeracionX plugin;
    private final File archivo;
    private Connection conexion;

    public AlmacenSQLite(ModeracionX plugin, File archivo) {
        this.plugin = plugin;
        this.archivo = archivo;
    }

    @Override
    public synchronized void inicializar() throws Exception {
        File carpeta = archivo.getParentFile();
        if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
            throw new IllegalStateException("No se pudo crear la carpeta " + carpeta.getAbsolutePath());
        }
        Class.forName("org.sqlite.JDBC");
        conexion = DriverManager.getConnection("jdbc:sqlite:" + archivo.getAbsolutePath());
        try (Statement statement = conexion.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL;");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mx_sanciones (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tipo TEXT NOT NULL,
                        objetivo_uuid TEXT,
                        objetivo_nombre TEXT,
                        objetivo_ip TEXT,
                        autor_uuid TEXT,
                        autor_nombre TEXT NOT NULL,
                        razon TEXT NOT NULL,
                        creada INTEGER NOT NULL,
                        expira INTEGER NOT NULL,
                        activa INTEGER NOT NULL,
                        removida_por TEXT,
                        removida_en INTEGER NOT NULL DEFAULT 0,
                        razon_remocion TEXT
                    );""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mx_jugadores (
                        uuid TEXT PRIMARY KEY,
                        nombre TEXT NOT NULL,
                        nombre_bajo TEXT NOT NULL,
                        ip TEXT,
                        primera INTEGER NOT NULL,
                        ultima INTEGER NOT NULL
                    );""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mx_comandos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT,
                        nombre TEXT NOT NULL,
                        comando TEXT NOT NULL,
                        linea TEXT NOT NULL,
                        mundo TEXT,
                        fecha INTEGER NOT NULL
                    );""");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_cmd_fecha ON mx_comandos(fecha);");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_cmd_uuid ON mx_comandos(uuid);");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_cmd_comando ON mx_comandos(comando);");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_obj_uuid ON mx_sanciones(objetivo_uuid);");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_obj_ip ON mx_sanciones(objetivo_ip);");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_activa ON mx_sanciones(activa);");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mx_nombre_bajo ON mx_jugadores(nombre_bajo);");
        }
        plugin.getLogger().info("Base de datos SQLite lista: " + archivo.getName());
    }

    @Override
    public synchronized void cerrar() {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (SQLException excepcion) {
                plugin.getLogger().warning("Error cerrando la base de datos: " + excepcion.getMessage());
            }
            conexion = null;
        }
    }

    @Override
    public String nombre() {
        return "SQLite";
    }

    @Override
    public synchronized void insertar(Sancion sancion) throws SQLException {
        String sql = "INSERT INTO mx_sanciones (tipo, objetivo_uuid, objetivo_nombre, objetivo_ip, autor_uuid, "
                + "autor_nombre, razon, creada, expira, activa, removida_por, removida_en, razon_remocion) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sancion.tipo().name());
            ps.setString(2, sancion.objetivoUuid() == null ? null : sancion.objetivoUuid().toString());
            ps.setString(3, sancion.objetivoNombre());
            ps.setString(4, sancion.objetivoIp());
            ps.setString(5, sancion.autorUuid() == null ? null : sancion.autorUuid().toString());
            ps.setString(6, sancion.autorNombre());
            ps.setString(7, sancion.razon());
            ps.setLong(8, sancion.creada());
            ps.setLong(9, sancion.expira());
            ps.setInt(10, sancion.activa() ? 1 : 0);
            ps.setString(11, sancion.removidaPor());
            ps.setLong(12, sancion.removidaEn());
            ps.setString(13, sancion.razonRemocion());
            ps.executeUpdate();
            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (claves.next()) {
                    sancion.id(claves.getLong(1));
                }
            }
        }
    }

    @Override
    public synchronized void actualizar(Sancion sancion) throws SQLException {
        String sql = "UPDATE mx_sanciones SET activa=?, removida_por=?, removida_en=?, razon_remocion=? WHERE id=?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, sancion.activa() ? 1 : 0);
            ps.setString(2, sancion.removidaPor());
            ps.setLong(3, sancion.removidaEn());
            ps.setString(4, sancion.razonRemocion());
            ps.setLong(5, sancion.id());
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized List<Sancion> historial(UUID uuid) throws SQLException {
        return consultar("SELECT " + COLUMNAS + " FROM mx_sanciones WHERE objetivo_uuid=? ORDER BY creada DESC",
                uuid.toString());
    }

    @Override
    public synchronized List<Sancion> historialPorNombre(String nombre) throws SQLException {
        return consultar("SELECT " + COLUMNAS + " FROM mx_sanciones WHERE LOWER(objetivo_nombre)=? ORDER BY creada DESC",
                nombre.toLowerCase(Locale.ROOT));
    }

    @Override
    public synchronized List<Sancion> historialPorIp(String ip) throws SQLException {
        return consultar("SELECT " + COLUMNAS + " FROM mx_sanciones WHERE objetivo_ip=? ORDER BY creada DESC", ip);
    }

    @Override
    public synchronized List<Sancion> activas() throws SQLException {
        return consultar("SELECT " + COLUMNAS + " FROM mx_sanciones WHERE activa=1 ORDER BY creada DESC");
    }

    @Override
    public synchronized List<Sancion> activasDe(UUID uuid, TipoSancion tipo) throws SQLException {
        return consultar("SELECT " + COLUMNAS + " FROM mx_sanciones WHERE activa=1 AND objetivo_uuid=? AND tipo=? "
                + "ORDER BY creada DESC", uuid.toString(), tipo.name());
    }

    @Override
    public synchronized int total() throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("SELECT COUNT(*) FROM mx_sanciones");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public synchronized void guardarPerfil(Perfil perfil) throws SQLException {
        String sql = "INSERT INTO mx_jugadores (uuid, nombre, nombre_bajo, ip, primera, ultima) VALUES (?,?,?,?,?,?) "
                + "ON CONFLICT(uuid) DO UPDATE SET nombre=excluded.nombre, nombre_bajo=excluded.nombre_bajo, "
                + "ip=excluded.ip, ultima=excluded.ultima";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, perfil.uuid().toString());
            ps.setString(2, perfil.nombre());
            ps.setString(3, perfil.nombre().toLowerCase(Locale.ROOT));
            ps.setString(4, perfil.ip());
            ps.setLong(5, perfil.primeraConexion());
            ps.setLong(6, perfil.ultimaConexion());
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized Optional<Perfil> perfilPorUuid(UUID uuid) throws SQLException {
        return perfil("SELECT uuid, nombre, ip, primera, ultima FROM mx_jugadores WHERE uuid=?", uuid.toString());
    }

    @Override
    public synchronized Optional<Perfil> perfilPorNombre(String nombre) throws SQLException {
        return perfil("SELECT uuid, nombre, ip, primera, ultima FROM mx_jugadores WHERE nombre_bajo=?",
                nombre.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------- registro de comandos

    @Override
    public synchronized void registrarComandos(List<RegistroComando> registros) throws SQLException {
        if (registros.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO mx_comandos (uuid, nombre, comando, linea, mundo, fecha) VALUES (?,?,?,?,?,?)";
        boolean autocommit = conexion.getAutoCommit();
        conexion.setAutoCommit(false);
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            for (RegistroComando registro : registros) {
                ps.setString(1, registro.uuid() == null ? null : registro.uuid().toString());
                ps.setString(2, registro.nombre());
                ps.setString(3, registro.comando());
                ps.setString(4, registro.linea());
                ps.setString(5, registro.mundo());
                ps.setLong(6, registro.fecha());
                ps.addBatch();
            }
            ps.executeBatch();
            conexion.commit();
        } catch (SQLException excepcion) {
            conexion.rollback();
            throw excepcion;
        } finally {
            conexion.setAutoCommit(autocommit);
        }
    }

    @Override
    public synchronized List<RegistroComando> buscarComandos(String texto, UUID jugador, long desde,
                                                             int limite, int desplazamiento) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, uuid, nombre, comando, linea, mundo, fecha FROM mx_comandos WHERE 1=1");
        List<Object> parametros = filtros(sql, texto, jugador, desde);
        sql.append(" ORDER BY fecha DESC LIMIT ? OFFSET ?");
        parametros.add(limite);
        parametros.add(desplazamiento);

        List<RegistroComando> resultado = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(sql.toString())) {
            aplicar(ps, parametros);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuidTexto = rs.getString("uuid");
                    resultado.add(new RegistroComando(
                            rs.getLong("id"),
                            uuidTexto == null ? null : UUID.fromString(uuidTexto),
                            rs.getString("nombre"),
                            rs.getString("comando"),
                            rs.getString("linea"),
                            rs.getString("mundo"),
                            rs.getLong("fecha")));
                }
            }
        }
        return resultado;
    }

    @Override
    public synchronized int contarComandos(String texto, UUID jugador, long desde) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM mx_comandos WHERE 1=1");
        List<Object> parametros = filtros(sql, texto, jugador, desde);
        try (PreparedStatement ps = conexion.prepareStatement(sql.toString())) {
            aplicar(ps, parametros);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public synchronized int limpiarComandos(long antesDe) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("DELETE FROM mx_comandos WHERE fecha < ?")) {
            ps.setLong(1, antesDe);
            return ps.executeUpdate();
        }
    }

    @Override
    public synchronized int totalComandos() throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("SELECT COUNT(*) FROM mx_comandos");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Anade los WHERE comunes de la busqueda y devuelve sus parametros en orden. */
    private List<Object> filtros(StringBuilder sql, String texto, UUID jugador, long desde) {
        List<Object> parametros = new ArrayList<>();
        if (texto != null && !texto.isBlank()) {
            sql.append(" AND LOWER(linea) LIKE ? ESCAPE '\\'");
            parametros.add("%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%");
        }
        if (jugador != null) {
            sql.append(" AND uuid = ?");
            parametros.add(jugador.toString());
        }
        if (desde > 0) {
            sql.append(" AND fecha >= ?");
            parametros.add(desde);
        }
        return parametros;
    }

    private void aplicar(PreparedStatement ps, List<Object> parametros) throws SQLException {
        for (int i = 0; i < parametros.size(); i++) {
            Object valor = parametros.get(i);
            if (valor instanceof Integer numero) {
                ps.setInt(i + 1, numero);
            } else if (valor instanceof Long numero) {
                ps.setLong(i + 1, numero);
            } else {
                ps.setString(i + 1, String.valueOf(valor));
            }
        }
    }

    /** Escapa los comodines para que buscar "%" no devuelva todo. */
    private String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ---------------------------------------------------------------- internos

    private Optional<Perfil> perfil(String sql, String parametro) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Perfil(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("nombre"),
                        rs.getString("ip"),
                        rs.getLong("primera"),
                        rs.getLong("ultima")));
            }
        }
    }

    private List<Sancion> consultar(String sql, String... parametros) throws SQLException {
        List<Sancion> resultado = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setString(i + 1, parametros[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(leer(rs));
                }
            }
        }
        return resultado;
    }

    private Sancion leer(ResultSet rs) throws SQLException {
        String objetivoUuid = rs.getString("objetivo_uuid");
        String autorUuid = rs.getString("autor_uuid");
        TipoSancion tipo = TipoSancion.desde(rs.getString("tipo"));
        return new Sancion(
                rs.getLong("id"),
                tipo == null ? TipoSancion.WARN : tipo,
                objetivoUuid == null ? null : UUID.fromString(objetivoUuid),
                rs.getString("objetivo_nombre"),
                rs.getString("objetivo_ip"),
                autorUuid == null ? null : UUID.fromString(autorUuid),
                rs.getString("autor_nombre"),
                rs.getString("razon"),
                rs.getLong("creada"),
                rs.getLong("expira"),
                rs.getInt("activa") == 1,
                rs.getString("removida_por"),
                rs.getLong("removida_en"),
                rs.getString("razon_remocion"));
    }
}
