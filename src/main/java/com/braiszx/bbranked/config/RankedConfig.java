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
    private double drawLossPercent;
    private boolean mvpEnabled;
    private int mvpBonus;
    private int mvpMinTeamSize;
    private boolean mvpShareOnTie;

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
    private boolean blockSameIp;

    // --- parties ---
    private boolean partyEnabled;
    private int partyMaxSize;
    private int partyMaxEloDifference;
    private int partyInviteTimeoutSeconds;

    // --- leaderboard ---
    private int leaderboardPageSize;
    private int leaderboardMinMatches;
    private int leaderboardRefreshSeconds;

    private boolean broadcastResults;

    // --- barra de accion ---
    private boolean actionbarQueueEnabled;
    private int actionbarIntervalTicks;
    private boolean actionbarMvpEnabled;
    private int actionbarMvpSeconds;

    // --- anti-boosting ---
    private boolean repeatOpponentEnabled;
    private int repeatFreeMatches;
    private double repeatReductionPerMatch;
    private double repeatMinMultiplier;
    private int repeatResetHours;
    private int repeatAlertAfter;

    // --- confirmacion de partida ---
    private boolean readyCheckEnabled;
    private int readyCheckSeconds;
    private int readyCheckPenaltySeconds;

    // --- decaimiento ---
    private boolean decayEnabled;
    private int decayInactiveDays;
    private int decayEloPerDay;
    private int decayFloor;
    private boolean decayRequirePlacements;
    private int decayCheckIntervalMinutes;

    // --- temporadas ---
    private boolean seasonEnabled;
    private int seasonResetTarget;
    private double seasonResetFactor;
    private Map<Integer, List<String>> seasonTopRewards = Collections.emptyMap();
    private Map<String, List<String>> seasonRankRewards = Collections.emptyMap();

    // --- menu ---
    private boolean menuEnabled;
    private String menuTitle;
    private Map<String, String> menuModeIcons = Collections.emptyMap();
    private String menuDefaultIcon;

    // --- discord ---
    private boolean discordEnabled;
    private String discordWebhookUrl;
    private String discordUsername;
    private boolean discordMatchResults;
    private boolean discordRankChanges;
    private boolean discordSeasonEnd;
    private boolean discordBoostingAlerts;

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

        drawLossPercent = Math.max(0.0D, c.getDouble("elo.draw.loss-percent", 40.0D));
        mvpEnabled = c.getBoolean("elo.mvp.enabled", true);
        mvpBonus = c.getInt("elo.mvp.bonus", 5);
        mvpMinTeamSize = c.getInt("elo.mvp.min-team-size", 2);
        mvpShareOnTie = c.getBoolean("elo.mvp.share-on-tie", true);

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
        blockSameIp = c.getBoolean("queue.block-same-ip", true);

        partyEnabled = c.getBoolean("party.enabled", true);
        partyMaxSize = Math.max(2, c.getInt("party.max-size", 3));
        partyMaxEloDifference = Math.max(0, c.getInt("party.max-elo-difference", 600));
        partyInviteTimeoutSeconds = Math.max(10, c.getInt("party.invite-timeout-seconds", 60));

        leaderboardPageSize = Math.max(1, c.getInt("leaderboard.page-size", 10));
        leaderboardMinMatches = c.getInt("leaderboard.min-matches", 5);
        leaderboardRefreshSeconds = Math.max(5, c.getInt("leaderboard.refresh-seconds", 60));

        broadcastResults = c.getBoolean("broadcast-results", false);

        actionbarQueueEnabled = c.getBoolean("actionbar.queue-enabled", true);
        actionbarIntervalTicks = Math.max(5, c.getInt("actionbar.update-interval-ticks", 20));
        actionbarMvpEnabled = c.getBoolean("actionbar.mvp-enabled", true);
        actionbarMvpSeconds = Math.max(1, c.getInt("actionbar.mvp-seconds", 5));

        repeatOpponentEnabled = c.getBoolean("elo.repeat-opponent.enabled", true);
        repeatFreeMatches = Math.max(0, c.getInt("elo.repeat-opponent.free-matches", 2));
        repeatReductionPerMatch = c.getDouble("elo.repeat-opponent.reduction-per-match", 0.25D);
        repeatMinMultiplier = Math.max(0.0D, c.getDouble("elo.repeat-opponent.min-multiplier", 0.15D));
        repeatResetHours = Math.max(1, c.getInt("elo.repeat-opponent.reset-hours", 24));
        repeatAlertAfter = c.getInt("elo.repeat-opponent.alert-after", 6);

        readyCheckEnabled = c.getBoolean("ready-check.enabled", true);
        readyCheckSeconds = Math.max(5, c.getInt("ready-check.seconds", 20));
        readyCheckPenaltySeconds = Math.max(0, c.getInt("ready-check.penalty-seconds", 120));

        decayEnabled = c.getBoolean("decay.enabled", false);
        decayInactiveDays = Math.max(1, c.getInt("decay.inactive-days", 14));
        decayEloPerDay = Math.max(0, c.getInt("decay.elo-per-day", 10));
        decayFloor = c.getInt("decay.floor", 1200);
        decayRequirePlacements = c.getBoolean("decay.require-placements", true);
        decayCheckIntervalMinutes = Math.max(5, c.getInt("decay.check-interval-minutes", 60));

        seasonEnabled = c.getBoolean("season.enabled", true);
        seasonResetTarget = c.getInt("season.soft-reset.target", 1000);
        seasonResetFactor = Math.max(0.0D, Math.min(1.0D, c.getDouble("season.soft-reset.factor", 0.5D)));
        seasonTopRewards = loadTopRewards(c);
        seasonRankRewards = loadRankRewards(c);

        menuEnabled = c.getBoolean("menu.enabled", true);
        menuTitle = c.getString("menu.title", "<gold>Ranked</gold>");
        menuModeIcons = loadModeIcons(c);
        menuDefaultIcon = c.getString("menu.default-mode-icon", "LEATHER_BOOTS");

        discordEnabled = c.getBoolean("discord.enabled", false);
        discordWebhookUrl = c.getString("discord.webhook-url", "");
        discordUsername = c.getString("discord.username", "Ranked");
        discordMatchResults = c.getBoolean("discord.send-match-results", true);
        discordRankChanges = c.getBoolean("discord.send-rank-changes", true);
        discordSeasonEnd = c.getBoolean("discord.send-season-end", true);
        discordBoostingAlerts = c.getBoolean("discord.send-boosting-alerts", true);

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

    private Map<Integer, List<String>> loadTopRewards(FileConfiguration c) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("season.rewards.top");
        if (section == null) {
            return Collections.emptyMap();
        }
        for (String key : section.getKeys(false)) {
            try {
                result.put(Integer.parseInt(key), List.copyOf(section.getStringList(key)));
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Premio de temporada con puesto invalido: '" + key + "'.");
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, List<String>> loadRankRewards(FileConfiguration c) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("season.rewards.ranks");
        if (section == null) {
            return Collections.emptyMap();
        }
        for (String key : section.getKeys(false)) {
            result.put(key.toLowerCase(Locale.ROOT), List.copyOf(section.getStringList(key)));
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, String> loadModeIcons(FileConfiguration c) {
        Map<String, String> result = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("menu.mode-icons");
        if (section == null) {
            return Collections.emptyMap();
        }
        for (String key : section.getKeys(false)) {
            result.put(key.toLowerCase(Locale.ROOT), section.getString(key, "LEATHER_BOOTS"));
        }
        return Collections.unmodifiableMap(result);
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

    public boolean blockSameIp() {
        return blockSameIp;
    }

    public boolean partyEnabled() {
        return partyEnabled;
    }

    public int partyMaxSize() {
        return partyMaxSize;
    }

    public int partyMaxEloDifference() {
        return partyMaxEloDifference;
    }

    public int partyInviteTimeoutSeconds() {
        return partyInviteTimeoutSeconds;
    }

    public double drawLossPercent() {
        return drawLossPercent;
    }

    public boolean mvpEnabled() {
        return mvpEnabled;
    }

    public int mvpBonus() {
        return mvpBonus;
    }

    public int mvpMinTeamSize() {
        return mvpMinTeamSize;
    }

    public boolean mvpShareOnTie() {
        return mvpShareOnTie;
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

    public boolean actionbarQueueEnabled() {
        return actionbarQueueEnabled;
    }

    public int actionbarIntervalTicks() {
        return actionbarIntervalTicks;
    }

    public boolean actionbarMvpEnabled() {
        return actionbarMvpEnabled;
    }

    public int actionbarMvpSeconds() {
        return actionbarMvpSeconds;
    }

    public boolean repeatOpponentEnabled() {
        return repeatOpponentEnabled;
    }

    public int repeatFreeMatches() {
        return repeatFreeMatches;
    }

    public double repeatReductionPerMatch() {
        return repeatReductionPerMatch;
    }

    public double repeatMinMultiplier() {
        return repeatMinMultiplier;
    }

    public int repeatResetHours() {
        return repeatResetHours;
    }

    public long repeatResetMillis() {
        return repeatResetHours * 3_600_000L;
    }

    public int repeatAlertAfter() {
        return repeatAlertAfter;
    }

    public boolean readyCheckEnabled() {
        return readyCheckEnabled;
    }

    public int readyCheckSeconds() {
        return readyCheckSeconds;
    }

    public int readyCheckPenaltySeconds() {
        return readyCheckPenaltySeconds;
    }

    public boolean decayEnabled() {
        return decayEnabled;
    }

    public int decayInactiveDays() {
        return decayInactiveDays;
    }

    public int decayEloPerDay() {
        return decayEloPerDay;
    }

    public int decayFloor() {
        return decayFloor;
    }

    public boolean decayRequirePlacements() {
        return decayRequirePlacements;
    }

    public int decayCheckIntervalMinutes() {
        return decayCheckIntervalMinutes;
    }

    public boolean seasonEnabled() {
        return seasonEnabled;
    }

    public int seasonResetTarget() {
        return seasonResetTarget;
    }

    public double seasonResetFactor() {
        return seasonResetFactor;
    }

    public Map<Integer, List<String>> seasonTopRewards() {
        return seasonTopRewards;
    }

    public Map<String, List<String>> seasonRankRewards() {
        return seasonRankRewards;
    }

    public boolean menuEnabled() {
        return menuEnabled;
    }

    public String menuTitle() {
        return menuTitle;
    }

    public String menuIconFor(String modeId) {
        return menuModeIcons.getOrDefault(modeId.toLowerCase(Locale.ROOT), menuDefaultIcon);
    }

    public boolean discordEnabled() {
        return discordEnabled && discordWebhookUrl != null && !discordWebhookUrl.isBlank();
    }

    public String discordWebhookUrl() {
        return discordWebhookUrl;
    }

    public String discordUsername() {
        return discordUsername;
    }

    public boolean discordMatchResults() {
        return discordMatchResults;
    }

    public boolean discordRankChanges() {
        return discordRankChanges;
    }

    public boolean discordSeasonEnd() {
        return discordSeasonEnd;
    }

    public boolean discordBoostingAlerts() {
        return discordBoostingAlerts;
    }
}
