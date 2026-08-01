package com.braiszx.bbranked.data;

import com.braiszx.bbranked.config.RankedConfig;
import org.bukkit.plugin.Plugin;

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

    private Connection connection;

    public StatsStorage(Plugin plugin, RankedConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.mysql = "mysql".equalsIgnoreCase(config.dbType());
        this.playersTable = config.tablePrefix() + "players";
        this.matchesTable = config.tablePrefix() + "matches";
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

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_" + playersTable + "_elo ON " + playersTable + " (elo)");
        }
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
                + " draws = ?, goals = ?, leaves = ?, win_streak = ?, best_streak = ?, last_seen = ?"
                + " WHERE uuid = ?";
        try (PreparedStatement statement = connection().prepareStatement(update)) {
            bindStats(statement, stats);
            statement.setString(12, stats.uuid().toString());
            if (statement.executeUpdate() > 0) {
                return;
            }
        }

        String insert = "INSERT INTO " + playersTable + " (name, elo, peak_elo, wins, losses, draws, goals,"
                + " leaves, win_streak, best_streak, last_seen, uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection().prepareStatement(insert)) {
            bindStats(statement, stats);
            statement.setString(12, stats.uuid().toString());
            statement.executeUpdate();
        }
    }

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
