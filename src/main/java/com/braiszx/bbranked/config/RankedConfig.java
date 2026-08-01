package com.braiszx.bbranked.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vista tipada de config.yml. Se vuelve a construir entera en cada /ranked reload.
 */
public final class RankedConfig {

    private final Plugin plugin;

    // --- base de datos ---
    private String dbType;
    private String dbFile;
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUser;
    private String dbPassword;
    private String tablePrefix;
    private String mysqlProperties;

    // --- elo ---
    private int startingElo;
    private int minimumElo;
    private int placementMatches;
    private int placementK;
    private int baseK;
    private int highEloThreshold;
    private int highK;
    private int minGain;
    private int minLoss;
    private boolean goalDifferenceEnabled;
    private double goalDifferenceFactor;
    private double goalDifferenceMaxMultiplier;

    // --- abandonos ---
    private boolean forfeitOnLeave;
    private int leaverExtraPenalty;
    private boolean protectTeammates;
    private int leaverQueueBanSeconds;

    // --- cola ---
    private int queueCheckIntervalSeconds;
    private int queueInitialRange;
    private int queueRangeExpansionPerSecond;
    private int queueMaxRange;
    private int startTimeoutSeconds;
    private boolean blockManualJoin;

    // --- leaderboard ---
    private int leaderboardPageSize;
    private int leaderboardMinMatches;
    private int leaderboardRefreshSeconds;

    private boolean broadcastResults;

    private Map<String, QueueMode> modes = Collections.emptyMap();
    private List<RankTier> ranks = Collections.emptyList();

    public RankedConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        dbType = c.getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
        dbFile = c.getString("database.file", "ranked.db");
        dbHost = c.getString("database.host", "localhost");
        dbPort = c.getInt("database.port", 3306);
        dbName = c.getString("database.database", "minecraft");
        dbUser = c.getString("database.username", "root");
        dbPassword = c.getString("database.password", "");
        tablePrefix = c.getString("database.table-prefix", "bbranked_");
        mysqlProperties = c.getString("database.mysql-properties", "useSSL=false");

        startingElo = c.getInt("elo.starting", 1000);
        minimumElo = c.getInt("elo.minimum", 0);
        placementMatches = c.getInt("elo.placement-matches", 10);
        placementK = c.getInt("elo.placement-k", 60);
        baseK = c.getInt("elo.base-k", 32);
        highEloThreshold = c.getInt("elo.high-elo-threshold", 1800);
        highK = c.getInt("elo.high-k", 16);
        minGain = c.getInt("elo.min-gain", 1);
        minLoss = c.getInt("elo.min-loss", 1);
        goalDifferenceEnabled = c.getBoolean("elo.goal-difference.enabled", true);
        goalDifferenceFactor = c.getDouble("elo.goal-difference.factor", 0.18D);
        goalDifferenceMaxMultiplier = c.getDouble("elo.goal-difference.max-multiplier", 1.6D);

        forfeitOnLeave = c.getBoolean("elo.leaver.forfeit-on-leave", true);
        leaverExtraPenalty = c.getInt("elo.leaver.extra-penalty", 25);
        protectTeammates = c.getBoolean("elo.leaver.protect-teammates", true);
        leaverQueueBanSeconds = c.getInt("elo.leaver.queue-ban-seconds", 300);

        queueCheckIntervalSeconds = Math.max(1, c.getInt("queue.check-interval-seconds", 3));
        queueInitialRange = c.getInt("queue.initial-range", 100);
        queueRangeExpansionPerSecond = c.getInt("queue.range-expansion-per-second", 10);
        queueMaxRange = c.getInt("queue.max-range", 1000);
        startTimeoutSeconds = Math.max(10, c.getInt("queue.start-timeout-seconds", 60));
        blockManualJoin = c.getBoolean("queue.block-manual-join", true);

        leaderboardPageSize = Math.max(1, c.getInt("leaderboard.page-size", 10));
        leaderboardMinMatches = c.getInt("leaderboard.min-matches", 5);
        leaderboardRefreshSeconds = Math.max(5, c.getInt("leaderboard.refresh-seconds", 60));

        broadcastResults = c.getBoolean("broadcast-results", false);

        this.modes = loadModes(c);
        this.ranks = loadRanks(c);
    }

    private Map<String, QueueMode> loadModes(FileConfiguration c) {
        Map<String, QueueMode> result = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("modes");
        if (section == null) {
            plugin.getLogger().warning("No hay ningun modo definido en 'modes' del config.yml.");
            return result;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection mode = section.getConfigurationSection(key);
            if (mode == null) {
                continue;
            }
            int teamSize = mode.getInt("team-size", 1);
            if (teamSize < 1) {
                plugin.getLogger().warning("El modo '" + key + "' tiene team-size invalido, se ignora.");
                continue;
            }
            List<String> arenas = mode.getStringList("arenas");
            if (arenas.isEmpty()) {
                plugin.getLogger().warning("El modo '" + key + "' no tiene arenas asignadas.");
            }
            String id = key.toLowerCase(Locale.ROOT);
            result.put(id, new QueueMode(id, mode.getString("display-name", key), teamSize, List.copyOf(arenas)));
        }
        return Collections.unmodifiableMap(result);
    }

    private List<RankTier> loadRanks(FileConfiguration c) {
        List<RankTier> result = new ArrayList<>();
        for (Map<?, ?> entry : c.getMapList("ranks")) {
            Object id = entry.get("id");
            Object display = entry.get("display-name");
            Object minElo = entry.get("min-elo");
            if (id == null || display == null || !(minElo instanceof Number number)) {
                continue;
            }
            result.add(new RankTier(id.toString(), display.toString(), number.intValue()));
        }
        // De menor a mayor Elo, asi rankOf() puede recorrer al reves.
        result.sort((a, b) -> Integer.compare(a.minElo(), b.minElo()));
        if (result.isEmpty()) {
            result.add(new RankTier("sin-rango", "<gray>Sin rango</gray>", 0));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Devuelve la division que le corresponde a un Elo.
     */
    public RankTier rankOf(int elo) {
        RankTier current = ranks.get(0);
        for (RankTier tier : ranks) {
            if (elo >= tier.minElo()) {
                current = tier;
            } else {
                break;
            }
        }
        return current;
    }

    public QueueMode mode(String id) {
        return id == null ? null : modes.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, QueueMode> modes() {
        return modes;
    }

    public List<RankTier> ranks() {
        return ranks;
    }

    public String dbType() {
        return dbType;
    }

    public String dbFile() {
        return dbFile;
    }

    public String dbHost() {
        return dbHost;
    }

    public int dbPort() {
        return dbPort;
    }

    public String dbName() {
        return dbName;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public String mysqlProperties() {
        return mysqlProperties;
    }

    public int startingElo() {
        return startingElo;
    }

    public int minimumElo() {
        return minimumElo;
    }

    public int placementMatches() {
        return placementMatches;
    }

    public int placementK() {
        return placementK;
    }

    public int baseK() {
        return baseK;
    }

    public int highEloThreshold() {
        return highEloThreshold;
    }

    public int highK() {
        return highK;
    }

    public int minGain() {
        return minGain;
    }

    public int minLoss() {
        return minLoss;
    }

    public boolean goalDifferenceEnabled() {
        return goalDifferenceEnabled;
    }

    public double goalDifferenceFactor() {
        return goalDifferenceFactor;
    }

    public double goalDifferenceMaxMultiplier() {
        return goalDifferenceMaxMultiplier;
    }

    public boolean forfeitOnLeave() {
        return forfeitOnLeave;
    }

    public int leaverExtraPenalty() {
        return leaverExtraPenalty;
    }

    public boolean protectTeammates() {
        return protectTeammates;
    }

    public int leaverQueueBanSeconds() {
        return leaverQueueBanSeconds;
    }

    public int queueCheckIntervalSeconds() {
        return queueCheckIntervalSeconds;
    }

    public int queueInitialRange() {
        return queueInitialRange;
    }

    public int queueRangeExpansionPerSecond() {
        return queueRangeExpansionPerSecond;
    }

    public int queueMaxRange() {
        return queueMaxRange;
    }

    public int startTimeoutSeconds() {
        return startTimeoutSeconds;
    }

    public boolean blockManualJoin() {
        return blockManualJoin;
    }

    public int leaderboardPageSize() {
        return leaderboardPageSize;
    }

    public int leaderboardMinMatches() {
        return leaderboardMinMatches;
    }

    public int leaderboardRefreshSeconds() {
        return leaderboardRefreshSeconds;
    }

    public boolean broadcastResults() {
        return broadcastResults;
    }
}
