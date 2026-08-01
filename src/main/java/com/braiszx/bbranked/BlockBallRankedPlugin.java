package com.braiszx.bbranked;

import com.braiszx.bbranked.command.RankedCommand;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.data.StatsStorage;
import com.braiszx.bbranked.elo.EloCalculator;
import com.braiszx.bbranked.hook.RankedPlaceholderExpansion;
import com.braiszx.bbranked.listener.BlockBallListener;
import com.braiszx.bbranked.listener.PlayerConnectionListener;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.queue.QueueManager;
import com.braiszx.bbranked.util.Messages;
import com.github.shynixn.blockball.contract.GameService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Plugin de partidas rankeds con Elo montado encima de BlockBall.
 */
public final class BlockBallRankedPlugin extends JavaPlugin {

    private RankedConfig rankedConfig;
    private Messages messages;
    private StatsStorage storage;
    private StatsManager statsManager;
    private EloCalculator eloCalculator;
    private MatchManager matchManager;
    private QueueManager queueManager;

    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("BlockBall") == null) {
            getLogger().severe("No se ha encontrado BlockBall. El plugin se desactiva.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.rankedConfig = new RankedConfig(this);
        this.messages = new Messages(this);

        this.storage = new StatsStorage(this, rankedConfig);
        try {
            storage.initialize();
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "No se ha podido conectar con la base de datos.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.statsManager = new StatsManager(rankedConfig, storage);
        this.eloCalculator = new EloCalculator(rankedConfig);
        this.matchManager = new MatchManager(this, rankedConfig, messages, statsManager, eloCalculator);
        this.queueManager = new QueueManager(rankedConfig, messages, statsManager, matchManager);

        registerListeners();
        registerCommand();
        registerPlaceholders();
        startTasks();

        // Al recargar el plugin en caliente los jugadores ya estan dentro.
        for (Player player : Bukkit.getOnlinePlayers()) {
            statsManager.load(player);
        }

        statsManager.refreshLeaderboard();
        getLogger().info("BlockBallRanked activado con " + rankedConfig.modes().size() + " modo(s).");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();

        if (matchManager != null) {
            matchManager.shutdown();
        }
        if (statsManager != null) {
            statsManager.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new BlockBallListener(rankedConfig, messages, matchManager, queueManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(statsManager, queueManager, matchManager), this);
    }

    private void registerCommand() {
        PluginCommand command = getCommand("ranked");
        if (command == null) {
            getLogger().severe("El comando /ranked no esta declarado en plugin.yml.");
            return;
        }
        RankedCommand executor = new RankedCommand(this, rankedConfig, messages, statsManager,
                queueManager, matchManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new RankedPlaceholderExpansion(this, rankedConfig, statsManager, queueManager).register();
            getLogger().info("Placeholders de PlaceholderAPI registrados (%bbranked_...%).");
        } catch (Throwable throwable) {
            getLogger().log(Level.WARNING, "No se han podido registrar los placeholders.", throwable);
        }
    }

    private void startTasks() {
        // Vigilancia de las partidas: cada segundo.
        tasks.add(getServer().getScheduler().runTaskTimer(this, matchManager::tick, 20L, 20L));

        // Emparejamiento.
        long queueInterval = rankedConfig.queueCheckIntervalSeconds() * 20L;
        tasks.add(getServer().getScheduler().runTaskTimer(this, queueManager::tick, queueInterval, queueInterval));

        // Refresco del ranking.
        long leaderboardInterval = rankedConfig.leaderboardRefreshSeconds() * 20L;
        tasks.add(getServer().getScheduler().runTaskTimer(this, statsManager::refreshLeaderboard,
                leaderboardInterval, leaderboardInterval));

        // Guardado periodico por si el servidor se cae de golpe.
        tasks.add(getServer().getScheduler().runTaskTimer(this, statsManager::saveAll, 6000L, 6000L));
    }

    /**
     * Recarga config.yml y messages.yml sin reiniciar el servidor. Los cambios
     * de base de datos si necesitan reinicio.
     */
    public void reloadEverything() {
        rankedConfig.reload();
        messages.reload();
        statsManager.refreshLeaderboard();
    }

    /**
     * Servicio de BlockBall, resuelto en cada llamada porque BlockBall lo
     * vuelve a registrar cuando se recarga.
     */
    public GameService gameService() {
        RegisteredServiceProvider<GameService> provider =
                getServer().getServicesManager().getRegistration(GameService.class);
        return provider == null ? null : provider.getProvider();
    }

    public MatchManager matchManager() {
        return matchManager;
    }

    public QueueManager queueManager() {
        return queueManager;
    }

    public StatsManager statsManager() {
        return statsManager;
    }

    public RankedConfig rankedConfig() {
        return rankedConfig;
    }

    public Messages messages() {
        return messages;
    }
}
