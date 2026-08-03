package com.braiszx.bbranked.data;

import com.braiszx.bbranked.ban.BanEntry;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.season.Season;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Capa de persistencia. Todo el trabajo de base de datos ocurre en un unico
 * hilo propio, asi que nunca bloquea el hilo principal del servidor y no hace
 * falta pool de conexiones ni sincronizacion extra.
 */
public final class StatsStorage implements AutoCloseable {

    private final Plugin plugin;
    private final RankedConfig config;
    private final ExecutorService executor;
    private final boolean mysql;
    private final String playersTable;
    private final String matchesTable;
    private final String bansTable;
    private final String encountersTable;
    private final String seasonsTable;
    private final String seasonResultsTable;

    private Connection connection;

    public StatsStorage(Plugin plugin, RankedConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.mysql = "mysql".equalsIgnoreCase(config.dbType());
        this.playersTable = config.tablePrefix() + "players";
        this.matchesTable = config.tablePrefix() + "matches";
        this.bansTable = config.tablePrefix() + "bans";
        this.encountersTable = config.tablePrefix() + "encounters";
        this.seasonsTable = config.tablePrefix() + "seasons";
        this.seasonResultsTable = config.tablePrefix() + "season_results";
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BlockBallRanked-DB");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Abre la conexion y crea las tablas. Bloquea a proposito: se llama en
     * onEnable para que el plugin no arranque si la base de datos falla.
     */
    public void initialize() throws SQLException {
        openConnection();
        createTables();
    }

    private void openConnection() throws SQLException {
        try {
            if (mysql) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } else {
                Class.forName("org.sqlite.JDBC");
            }
        } catch (ClassNotFoundException exception) {
            throw new SQLException("No se ha encontrado el driver JDBC de "
                    + (mysql ? "MySQL" : "SQLite") + ".", exception);
        }

        if (mysql) {
            String url = "jdbc:mysql://" + config.dbHost() + ":" + config.dbPort() + "/"
                    + config.dbName() + "?" + config.mysqlProperties();
            this.connection = DriverManager.getConnection(url, config.dbUser(), config.dbPassword());
        } else {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new SQLException("No se ha podido crear la carpeta del plugin.");
            }
            File file = new File(folder, config.dbFile());
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        }
    }

    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            openConnection();
        }
        return connection;
    }

    private void createTables() throws SQLException {
        String autoIncrement = mysql ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";

        try (Statement statement = connection().createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        name VARCHAR(16) NOT NULL,
                        elo INT NOT NULL,
                        peak_elo INT NOT NULL,
                        wins INT NOT NULL,
                        losses INT NOT NULL,
                        draws INT NOT NULL,
                        goals INT NOT NULL,
                        leaves INT NOT NULL,
                        win_streak INT NOT NULL,
                        best_streak INT NOT NULL,
                        last_seen BIGINT NOT NULL
                    )""".formatted(playersTable));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id %s,
                        mode VARCHAR(32) NOT NULL,
                        arena VARCHAR(64) NOT NULL,
                        red_score INT NOT NULL,
                        blue_score INT NOT NULL,
                        winner VARCHAR(8),
                        red_players TEXT NOT NULL,
                        blue_players TEXT NOT NULL,
                        elo_changes TEXT NOT NULL,
                        played_at BIGINT NOT NULL
                    )""".formatted(matchesTable, autoIncrement));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        target VARCHAR(48) NOT NULL,
                        type VARCHAR(8) NOT NULL,
                        label VARCHAR(48) NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        expires_at BIGINT NOT NULL,
                        banned_by VARCHAR(48) NOT NULL,
                        created_at BIGINT NOT NULL,
                        PRIMARY KEY (target, type)
                    )""".formatted(bansTable));

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_" + playersTable + "_elo ON " + playersTable + " (elo)");
        }

        try (Statement statement = connection().createStatement()) {
            // Cuantas veces se han enfrentado dos jugadores hace poco, para el
            // anti-boosting.
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        player VARCHAR(36) NOT NULL,
                        opponent VARCHAR(36) NOT NULL,
                        encounters INT NOT NULL,
                        last_at BIGINT NOT NULL,
                        PRIMARY KEY (player, opponent)
                    )""".formatted(encountersTable));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id %s,
                        name VARCHAR(64) NOT NULL,
                        started_at BIGINT NOT NULL,
                        ended_at BIGINT
                    )""".formatted(seasonsTable, mysql ? "INT AUTO_INCREMENT PRIMARY KEY"
                    : "INTEGER PRIMARY KEY AUTOINCREMENT"));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        season_id INT NOT NULL,
                        uuid VARCHAR(36) NOT NULL,
                        name VARCHAR(16) NOT NULL,
                        position INT NOT NULL,
                        elo INT NOT NULL,
                        wins INT NOT NULL,
                        losses INT NOT NULL,
                        draws INT NOT NULL,
                        PRIMARY KEY (season_id, uuid)
                    )""".formatted(seasonResultsTable));
        }

        // Columnas anadidas despues de la primera version. Para una tabla que
        // ya existe hay que anadirlas a mano.
        addColumnIfMissing(playersTable, "mvps", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(playersTable, "last_decay", "BIGINT NOT NULL DEFAULT 0");
    }

    /**
     * Anade una columna si no estaba. Si ya existe, la base de datos protesta
     * y se ignora: es la forma portable de migrar entre SQLite y MySQL sin
     * tener que leer el esquema de cada uno.
     */
    private void addColumnIfMissing(String table, String column, String definition) {
        try (Statement statement = connection().createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            plugin.getLogger().info("Base de datos actualizada: columna '" + column + "' anadida.");
        } catch (SQLException ignored) {
            // La columna ya existia.
        }
    }

    // ------------------------------------------------------------------
    //  Baneos
    // ------------------------------------------------------------------

    /**
     * Carga todos los baneos. Bloquea a proposito: se llama en onEnable para
     * que la cache este lista antes de que nadie entre en cola.
     */
    public List<BanEntry> loadBansBlocking() {
        List<BanEntry> entries = new ArrayList<>();
        try (Statement statement = connection().createStatement();
             ResultSet result = statement.executeQuery("SELECT * FROM " + bansTable)) {
            while (result.next()) {
                BanEntry.BanType type;
                try {
                    type = BanEntry.BanType.valueOf(result.getString("type"));
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                entries.add(new BanEntry(
                        result.getString("target"),
                        result.getString("label"),
                        type,
                        result.getString("reason"),
                        result.getLong("expires_at"),
                        result.getString("banned_by"),
                        result.getLong("created_at")));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando los baneos del ranked", exception);
        }
        return entries;
    }

    public CompletableFuture<Void> saveBan(BanEntry entry) {
        return CompletableFuture.runAsync(() -> {
            String update = "UPDATE " + bansTable + " SET label = ?, reason = ?, expires_at = ?,"
                    + " banned_by = ?, created_at = ? WHERE target = ? AND type = ?";
            try (PreparedStatement statement = connection().prepareStatement(update)) {
                statement.setString(1, entry.label());
                statement.setString(2, entry.reason());
                statement.setLong(3, entry.expiresAt());
                statement.setString(4, entry.bannedBy());
                statement.setLong(5, entry.createdAt());
                statement.setString(6, entry.target());
                statement.setString(7, entry.type().name());
                if (statement.executeUpdate() > 0) {
                    return;
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error guardando el baneo de " + entry.target(), exception);
                return;
            }

            String insert = "INSERT INTO " + bansTable
                    + " (label, reason, expires_at, banned_by, created_at, target, type)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection().prepareStatement(insert)) {
                statement.setString(1, entry.label());
                statement.setString(2, entry.reason());
                statement.setLong(3, entry.expiresAt());
                statement.setString(4, entry.bannedBy());
                statement.setLong(5, entry.createdAt());
                statement.setString(6, entry.target());
                statement.setString(7, entry.type().name());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error guardando el baneo de " + entry.target(), exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteBan(String target, BanEntry.BanType type) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement statement = connection().prepareStatement(
                    "DELETE FROM " + bansTable + " WHERE target = ? AND type = ?")) {
                statement.setString(1, target);
                statement.setString(2, type.name());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error borrando el baneo de " + target, exception);
            }
        }, executor);
    }

    // ------------------------------------------------------------------
    //  API asincrona
    // ------------------------------------------------------------------

    public CompletableFuture<PlayerStats> loadOrCreate(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerStats stats = loadBlocking(uuid);
                if (stats == null) {
                    stats = new PlayerStats(uuid, name, config.startingElo());
                    saveBlocking(stats);
                } else {
                    stats.name(name);
                }
                return stats;
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error cargando las estadisticas de " + uuid, exception);
                return new PlayerStats(uuid, name, config.startingElo());
            }
        }, executor);
    }

    /**
     * Busca por nombre a un jugador que no esta conectado.
     */
    public CompletableFuture<PlayerStats> loadByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + playersTable + " WHERE LOWER(name) = ? LIMIT 1";
            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? read(result) : null;
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error buscando las estadisticas de " + name, exception);
                return null;
            }
        }, executor);
    }

    public CompletableFuture<Void> save(PlayerStats stats) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveBlocking(stats);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error guardando las estadisticas de " + stats.uuid(), exception);
            }
        }, executor);
    }

    /**
     * Guarda varios jugadores en una sola transaccion.
     */
    public CompletableFuture<Void> saveAll(List<PlayerStats> all) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            try {
                conn = connection();
                conn.setAutoCommit(false);
                for (PlayerStats stats : all) {
                    saveBlocking(stats);
                }
                conn.commit();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error guardando estadisticas en lote", exception);
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                        // nada que hacer
                    }
                }
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // nada que hacer
                    }
                }
            }
        }, executor);
    }

    public CompletableFuture<List<LeaderboardEntry>> topPlayers(int limit, int minMatches) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            String sql = "SELECT uuid, name, elo, wins, losses, draws FROM " + playersTable
                    + " WHERE (wins + losses + draws) >= ? ORDER BY elo DESC LIMIT ?";
            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setInt(1, minMatches);
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new LeaderboardEntry(
                                UUID.fromString(result.getString("uuid")),
                                result.getString("name"),
                                result.getInt("elo"),
                                result.getInt("wins"),
                                result.getInt("losses"),
                                result.getInt("draws")));
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error cargando el ranking", exception);
            }
            return entries;
        }, executor);
    }

    /**
     * Clasificacion por cualquiera de las estadisticas.
     *
     * <p>La expresion de orden sale del enum {@link LeaderboardType}, nunca de
     * texto escrito por nadie, asi que puede ir concatenada.</p>
     */
    public CompletableFuture<List<TopEntry>> topBy(LeaderboardType type, int limit, int minMatches) {
        return CompletableFuture.supplyAsync(() -> {
            List<TopEntry> entries = new ArrayList<>();
            String sql = "SELECT uuid, name, " + type.expression() + " AS value FROM " + playersTable
                    + " WHERE (wins + losses + draws) >= ? ORDER BY value DESC LIMIT ?";

            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setInt(1, minMatches);
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new TopEntry(
                                UUID.fromString(result.getString("uuid")),
                                result.getString("name"),
                                result.getDouble("value")));
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error cargando el ranking de " + type.id(), exception);
            }
            return entries;
        }, executor);
    }

    public CompletableFuture<Void> recordMatch(String mode, String arena, int redScore, int blueScore,
                                               String winner, String redPlayers, String bluePlayers,
                                               String eloChanges) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + matchesTable
                    + " (mode, arena, red_score, blue_score, winner, red_players, blue_players, elo_changes, played_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setString(1, mode);
                statement.setString(2, arena);
                statement.setInt(3, redScore);
                statement.setInt(4, blueScore);
                statement.setString(5, winner);
                statement.setString(6, redPlayers);
                statement.setString(7, bluePlayers);
                statement.setString(8, eloChanges);
                statement.setLong(9, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error guardando el historial de la partida", exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> delete(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement statement =
                         connection().prepareStatement("DELETE FROM " + playersTable + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error borrando las estadisticas de " + uuid, exception);
            }
        }, executor);
    }

    // ------------------------------------------------------------------
    //  Enfrentamientos repetidos (anti-boosting)
    // ------------------------------------------------------------------

    /**
     * Cuantas veces se ha enfrentado cada jugador de un equipo a los del otro
     * hace poco. Se queda con el maximo por jugador, que es lo que penaliza.
     *
     * @param staleMillis a partir de cuanto tiempo un enfrentamiento ya no cuenta
     */
    public CompletableFuture<Map<UUID, Integer>> loadEncounters(List<UUID> teamA, List<UUID> teamB,
                                                                long staleMillis) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Integer> result = new HashMap<>();
            long cutoff = System.currentTimeMillis() - staleMillis;
            String sql = "SELECT encounters FROM " + encountersTable
                    + " WHERE player = ? AND opponent = ? AND last_at >= ?";

            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                for (List<UUID> pair : List.of(teamA, teamB)) {
                    List<UUID> others = pair == teamA ? teamB : teamA;
                    for (UUID player : pair) {
                        int worst = 0;
                        for (UUID opponent : others) {
                            statement.setString(1, player.toString());
                            statement.setString(2, opponent.toString());
                            statement.setLong(3, cutoff);
                            try (ResultSet rs = statement.executeQuery()) {
                                if (rs.next()) {
                                    worst = Math.max(worst, rs.getInt("encounters"));
                                }
                            }
                        }
                        result.put(player, worst);
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error cargando enfrentamientos previos", exception);
            }
            return result;
        }, executor);
    }

    /**
     * Apunta que estos dos equipos se han enfrentado. Si el ultimo
     * enfrentamiento es viejo, el contador vuelve a empezar.
     */
    public CompletableFuture<Void> recordEncounters(List<UUID> teamA, List<UUID> teamB, long staleMillis) {
        return CompletableFuture.runAsync(() -> {
            long now = System.currentTimeMillis();
            long cutoff = now - staleMillis;

            for (UUID a : teamA) {
                for (UUID b : teamB) {
                    bumpEncounter(a, b, now, cutoff);
                    bumpEncounter(b, a, now, cutoff);
                }
            }
        }, executor);
    }

    private void bumpEncounter(UUID player, UUID opponent, long now, long cutoff) {
        String select = "SELECT encounters, last_at FROM " + encountersTable
                + " WHERE player = ? AND opponent = ?";
        try (PreparedStatement statement = connection().prepareStatement(select)) {
            statement.setString(1, player.toString());
            statement.setString(2, opponent.toString());

            int next = 1;
            boolean exists = false;
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    exists = true;
                    // Si hace mucho que no se ven, el contador se reinicia.
                    next = rs.getLong("last_at") >= cutoff ? rs.getInt("encounters") + 1 : 1;
                }
            }

            String sql = exists
                    ? "UPDATE " + encountersTable + " SET encounters = ?, last_at = ? WHERE player = ? AND opponent = ?"
                    : "INSERT INTO " + encountersTable + " (encounters, last_at, player, opponent) VALUES (?, ?, ?, ?)";
            try (PreparedStatement write = connection().prepareStatement(sql)) {
                write.setInt(1, next);
                write.setLong(2, now);
                write.setString(3, player.toString());
                write.setString(4, opponent.toString());
                write.executeUpdate();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error guardando el enfrentamiento", exception);
        }
    }

    // ------------------------------------------------------------------
    //  Historial
    // ------------------------------------------------------------------

    /**
     * Ultimas partidas de un jugador, de la mas reciente a la mas antigua.
     *
     * <p>Filtra con LIKE sobre las columnas de equipos, que guardan los uuid
     * separados por comas. Para el volumen de un servidor de Minecraft es de
     * sobra; si algun dia hay cientos de miles de partidas, habria que
     * normalizarlo en una tabla aparte.</p>
     */
    public CompletableFuture<List<MatchRecord>> matchHistory(UUID uuid, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<MatchRecord> records = new ArrayList<>();
            String needle = "%" + uuid + "%";
            String sql = "SELECT * FROM " + matchesTable
                    + " WHERE red_players LIKE ? OR blue_players LIKE ?"
                    + " ORDER BY played_at DESC LIMIT ? OFFSET ?";

            try (PreparedStatement statement = connection().prepareStatement(sql)) {
                statement.setString(1, needle);
                statement.setString(2, needle);
                statement.setInt(3, limit);
                statement.setInt(4, offset);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        records.add(new MatchRecord(
                                rs.getString("mode"),
                                rs.getString("arena"),
                                rs.getInt("red_score"),
                                rs.getInt("blue_score"),
                                rs.getString("winner"),
                                parseUuids(rs.getString("red_players")),
                                parseUuids(rs.getString("blue_players")),
                                parseEloChanges(rs.getString("elo_changes")),
                                rs.getLong("played_at")));
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error cargando el historial de " + uuid, exception);
            }
            return records;
        }, executor);
    }

    private static List<UUID> parseUuids(String csv) {
        List<UUID> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String part : csv.split(",")) {
            try {
                result.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException ignored) {
                // fila corrupta, se ignora
            }
        }
        return result;
    }

    /**
     * Formato guardado: {@code uuid:antes>despues,uuid:antes>despues}
     */
    private static Map<UUID, Integer> parseEloChanges(String raw) {
        Map<UUID, Integer> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            try {
                String[] halves = part.split(":");
                String[] values = halves[1].split(">");
                int before = Integer.parseInt(values[0]);
                int after = Integer.parseInt(values[1]);
                result.put(UUID.fromString(halves[0]), after - before);
            } catch (RuntimeException ignored) {
                // entrada corrupta, se ignora
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    //  Temporadas
    // ------------------------------------------------------------------

    /**
     * Temporada abierta, creando la primera si no hay ninguna. Bloquea: se
     * llama al arrancar.
     */
    public Season currentSeasonBlocking() {
        String sql = "SELECT * FROM " + seasonsTable + " WHERE ended_at IS NULL ORDER BY id DESC LIMIT 1";
        try (Statement statement = connection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return new Season(rs.getInt("id"), rs.getString("name"),
                        rs.getLong("started_at"), 0L);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando la temporada actual", exception);
            return null;
        }
        return startSeasonBlocking("Temporada 1");
    }

    public Season startSeasonBlocking(String name) {
        String sql = "INSERT INTO " + seasonsTable + " (name, started_at, ended_at) VALUES (?, ?, NULL)";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error creando la temporada", exception);
            return null;
        }
        return currentSeasonBlocking();
    }

    public void closeSeasonBlocking(int seasonId) {
        try (PreparedStatement statement = connection().prepareStatement(
                "UPDATE " + seasonsTable + " SET ended_at = ? WHERE id = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setInt(2, seasonId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error cerrando la temporada", exception);
        }
    }

    /**
     * Guarda el ranking final de una temporada.
     */
    public void saveSeasonResultsBlocking(int seasonId, List<LeaderboardEntry> ranking) {
        String sql = "INSERT INTO " + seasonResultsTable
                + " (season_id, uuid, name, position, elo, wins, losses, draws) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            for (int i = 0; i < ranking.size(); i++) {
                LeaderboardEntry entry = ranking.get(i);
                statement.setInt(1, seasonId);
                statement.setString(2, entry.uuid().toString());
                statement.setString(3, entry.name());
                statement.setInt(4, i + 1);
                statement.setInt(5, entry.elo());
                statement.setInt(6, entry.wins());
                statement.setInt(7, entry.losses());
                statement.setInt(8, entry.draws());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error guardando el ranking de la temporada", exception);
        }
    }

    /**
     * Todos los jugadores con partidas jugadas, para el cierre de temporada.
     */
    public List<LeaderboardEntry> allRankedPlayersBlocking() {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String sql = "SELECT uuid, name, elo, wins, losses, draws FROM " + playersTable
                + " WHERE (wins + losses + draws) > 0 ORDER BY elo DESC";
        try (Statement statement = connection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                entries.add(new LeaderboardEntry(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        rs.getInt("elo"),
                        rs.getInt("wins"),
                        rs.getInt("losses"),
                        rs.getInt("draws")));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando los jugadores de la temporada", exception);
        }
        return entries;
    }

    /**
     * Aplica el reset suave de temporada a todo el mundo y pone los
     * contadores a cero.
     */
    public void softResetAllBlocking(int target, double factor) {
        String sql = "UPDATE " + playersTable + " SET elo = CAST(? + (elo - ?) * ? AS INT),"
                + " wins = 0, losses = 0, draws = 0, goals = 0, mvps = 0, win_streak = 0";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setInt(1, target);
            statement.setInt(2, target);
            statement.setDouble(3, factor);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error aplicando el reset de temporada", exception);
        }
    }

    // ------------------------------------------------------------------
    //  Decaimiento por inactividad
    // ------------------------------------------------------------------

    /**
     * Aplica el decaimiento y devuelve a cuanta gente le ha afectado.
     */
    public CompletableFuture<Integer> applyDecay(int inactiveDays, int eloPerDay, int floor,
                                                 int minMatches) {
        return CompletableFuture.supplyAsync(() -> {
            long now = System.currentTimeMillis();
            long dayMillis = 86_400_000L;
            long inactiveSince = now - inactiveDays * dayMillis;
            int affected = 0;

            String select = "SELECT uuid, elo, last_seen, last_decay FROM " + playersTable
                    + " WHERE last_seen < ? AND elo > ? AND (wins + losses + draws) >= ?";
            String update = "UPDATE " + playersTable + " SET elo = ?, last_decay = ? WHERE uuid = ?";

            try (PreparedStatement statement = connection().prepareStatement(select)) {
                statement.setLong(1, inactiveSince);
                statement.setInt(2, floor);
                statement.setInt(3, minMatches);

                List<Object[]> updates = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        long lastSeen = rs.getLong("last_seen");
                        long lastDecay = rs.getLong("last_decay");
                        // Se cobra desde el ultimo cobro, o desde que empezo a
                        // estar inactivo si nunca se le habia cobrado.
                        long from = Math.max(lastDecay, lastSeen + inactiveDays * dayMillis);
                        long days = (now - from) / dayMillis;
                        if (days <= 0) {
                            continue;
                        }
                        int elo = rs.getInt("elo");
                        int newElo = Math.max(floor, elo - (int) (days * eloPerDay));
                        if (newElo == elo) {
                            continue;
                        }
                        updates.add(new Object[]{newElo, now, rs.getString("uuid")});
                    }
                }

                try (PreparedStatement write = connection().prepareStatement(update)) {
                    for (Object[] row : updates) {
                        write.setInt(1, (Integer) row[0]);
                        write.setLong(2, (Long) row[1]);
                        write.setString(3, (String) row[2]);
                        write.addBatch();
                    }
                    write.executeBatch();
                    affected = updates.size();
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "Error aplicando el decaimiento por inactividad", exception);
            }
            return affected;
        }, executor);
    }

    /**
     * Ejecuta algo en el hilo de la base de datos. Lo usan las operaciones de
     * temporada, que encadenan varias consultas bloqueantes.
     */
    public CompletableFuture<Void> runAsync(Runnable action) {
        return CompletableFuture.runAsync(action, executor);
    }

    // ------------------------------------------------------------------
    //  Operaciones bloqueantes (solo se llaman desde el hilo del executor)
    // ------------------------------------------------------------------

    private PlayerStats loadBlocking(UUID uuid) throws SQLException {
        try (PreparedStatement statement =
                     connection().prepareStatement("SELECT * FROM " + playersTable + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? read(result) : null;
            }
        }
    }

    private void saveBlocking(PlayerStats stats) throws SQLException {
        // UPDATE y si no existia INSERT: portable entre SQLite y MySQL sin
        // tener que escribir dos dialectos de upsert.
        String update = "UPDATE " + playersTable + " SET name = ?, elo = ?, peak_elo = ?, wins = ?, losses = ?,"
                + " draws = ?, goals = ?, leaves = ?, win_streak = ?, best_streak = ?, last_seen = ?, mvps = ?"
                + " WHERE uuid = ?";
        try (PreparedStatement statement = connection().prepareStatement(update)) {
            bindStats(statement, stats);
            statement.setString(UUID_PARAMETER, stats.uuid().toString());
            if (statement.executeUpdate() > 0) {
                return;
            }
        }

        String insert = "INSERT INTO " + playersTable + " (name, elo, peak_elo, wins, losses, draws, goals,"
                + " leaves, win_streak, best_streak, last_seen, mvps, uuid)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection().prepareStatement(insert)) {
            bindStats(statement, stats);
            statement.setString(UUID_PARAMETER, stats.uuid().toString());
            statement.executeUpdate();
        }
    }

    /**
     * Los dos SQL de arriba ponen el uuid al final, justo despues de las
     * columnas que enlaza {@link #bindStats}.
     */
    private static final int UUID_PARAMETER = 13;

    private void bindStats(PreparedStatement statement, PlayerStats stats) throws SQLException {
        statement.setString(1, stats.name());
        statement.setInt(2, stats.elo());
        statement.setInt(3, stats.peakElo());
        statement.setInt(4, stats.wins());
        statement.setInt(5, stats.losses());
        statement.setInt(6, stats.draws());
        statement.setInt(7, stats.goals());
        statement.setInt(8, stats.leaves());
        statement.setInt(9, stats.winStreak());
        statement.setInt(10, stats.bestStreak());
        statement.setLong(11, stats.lastSeen());
        statement.setInt(12, stats.mvps());
    }

    private PlayerStats read(ResultSet result) throws SQLException {
        PlayerStats stats = new PlayerStats(
                UUID.fromString(result.getString("uuid")),
                result.getString("name"),
                result.getInt("elo"));
        stats.peakElo(result.getInt("peak_elo"));
        stats.wins(result.getInt("wins"));
        stats.losses(result.getInt("losses"));
        stats.draws(result.getInt("draws"));
        stats.goals(result.getInt("goals"));
        stats.mvps(result.getInt("mvps"));
        stats.leaves(result.getInt("leaves"));
        stats.bestStreak(result.getInt("best_streak"));
        stats.winStreak(result.getInt("win_streak"));
        stats.lastSeen(result.getLong("last_seen"));
        return stats;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Error cerrando la conexion", exception);
            }
        }
    }
}
