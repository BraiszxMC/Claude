package com.braiszx.bbranked;

import com.braiszx.bbranked.ban.BanManager;
import com.braiszx.bbranked.command.RankedCommand;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.DecayService;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.data.StatsStorage;
import com.braiszx.bbranked.elo.EloCalculator;
import com.braiszx.bbranked.hook.DiscordWebhook;
import com.braiszx.bbranked.hook.RankedPlaceholderExpansion;
import com.braiszx.bbranked.listener.BlockBallListener;
import com.braiszx.bbranked.listener.PlayerConnectionListener;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.menu.MenuListener;
import com.braiszx.bbranked.party.PartyManager;
import com.braiszx.bbranked.queue.QueueManager;
import com.braiszx.bbranked.ready.ReadyCheckManager;
import com.braiszx.bbranked.season.SeasonManager;
import com.braiszx.bbranked.util.Messages;
import com.braiszx.bbranked.util.Sounds;
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
    private Sounds sounds;
    private StatsStorage storage;
    private StatsManager statsManager;
    private BanManager banManager;
    private PartyManager partyManager;
    private EloCalculator eloCalculator;
    private MatchManager matchManager;
    private QueueManager queueManager;
    private ReadyCheckManager readyCheckManager;
    private SeasonManager seasonManager;
    private DecayService decayService;
    private DiscordWebhook discord;

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
        this.sounds = new Sounds(this);

        this.storage = new StatsStorage(this, rankedConfig);
        try {
            storage.initialize();
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "No se ha podido conectar con la base de datos.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.statsManager = new StatsManager(rankedConfig, storage);
        this.banManager = new BanManager(storage);
        this.banManager.load();
        this.eloCalculator = new EloCalculator(rankedConfig);
        this.discord = new DiscordWebhook(this, rankedConfig);
        this.partyManager = new PartyManager(rankedConfig, statsManager);
        this.matchManager = new MatchManager(this, rankedConfig, messages, statsManager,
                eloCalculator, sounds, discord);
        this.queueManager = new QueueManager(rankedConfig, messages, statsManager, matchManager,
                banManager, partyManager, sounds);

        this.readyCheckManager = new ReadyCheckManager(rankedConfig, messages, sounds, matchManager);
        this.readyCheckManager.wire(queueManager::returnToQueue, queueManager::penalizeReadyCheck);
        this.queueManager.readyChecks(readyCheckManager);

        this.seasonManager = new SeasonManager(this, rankedConfig, storage, statsManager);
        this.seasonManager.load();
        this.decayService = new DecayService(this, rankedConfig, statsManager);

        registerListeners();
        registerCommand();
        registerPlaceholders();
        startTasks();

        // Al recargar el plugin en caliente los jugadores ya estan dentro.
        for (Player player : Bukkit.getOnlinePlayers()) {
            statsManager.load(player);
        }

        statsManager.refreshLeaderboard();

        if (rankedConfig.modes().isEmpty()) {
            getLogger().severe("=================================================");
            getLogger().severe(" No se ha cargado NINGUN modo de juego.");
            if (getConfig().getKeys(false).isEmpty()) {
                getLogger().severe(" El config.yml no se ha podido leer: revisa el");
                getLogger().severe(" error de YAML de mas arriba en esta consola, o");
                getLogger().severe(" borra el archivo para regenerarlo.");
            } else {
                getLogger().severe(" Falta la seccion 'modes' en el config.yml.");
            }
            getLogger().severe(" Hasta que se arregle, /ranked join no funciona.");
            getLogger().severe("=================================================");
        } else {
            getLogger().info("BlockBallRanked activado con " + rankedConfig.modes().size()
                    + " modo(s): " + String.join(", ", rankedConfig.modes().keySet()));
        }

        // BlockBall carga sus arenas de forma asincrona, asi que la revision se
        // hace unos segundos despues de arrancar.
        getServer().getScheduler().runTaskLater(this, this::logArenaProblems, 100L);
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();

        if (readyCheckManager != null) {
            readyCheckManager.clear();
        }
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
                new BlockBallListener(rankedConfig, messages, matchManager, queueManager, banManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(statsManager, queueManager, matchManager, partyManager,
                        messages, readyCheckManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(queueManager), this);
    }

    private void registerCommand() {
        PluginCommand command = getCommand("ranked");
        if (command == null) {
            getLogger().severe("El comando /ranked no esta declarado en plugin.yml.");
            return;
        }
        RankedCommand executor = new RankedCommand(this, rankedConfig, messages, statsManager,
                queueManager, matchManager, banManager, partyManager, sounds,
                readyCheckManager, seasonManager);
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

        // Barra de accion de la cola.
        long actionbarInterval = rankedConfig.actionbarIntervalTicks();
        tasks.add(getServer().getScheduler().runTaskTimer(this, queueManager::updateActionbars,
                actionbarInterval, actionbarInterval));

        // Confirmacion de partida: cuenta atras cada segundo.
        tasks.add(getServer().getScheduler().runTaskTimer(this, readyCheckManager::tick, 20L, 20L));

        // Decaimiento por inactividad.
        if (rankedConfig.decayEnabled()) {
            long decayInterval = rankedConfig.decayCheckIntervalMinutes() * 60L * 20L;
            tasks.add(getServer().getScheduler().runTaskTimer(this, decayService::run, 600L, decayInterval));
        }

        // Refresco del ranking.
        long leaderboardInterval = rankedConfig.leaderboardRefreshSeconds() * 20L;
        tasks.add(getServer().getScheduler().runTaskTimer(this, statsManager::refreshLeaderboard,
                leaderboardInterval, leaderboardInterval));

        // Guardado periodico por si el servidor se cae de golpe.
        tasks.add(getServer().getScheduler().runTaskTimer(this, statsManager::saveAll, 6000L, 6000L));
    }

    /**
     * Avisa por consola de las arenas mal configuradas. Sin esto, un config con
     * arenas inexistentes deja a los jugadores en una cola que nunca arranca y
     * sin ninguna pista de por que.
     */
    private void logArenaProblems() {
        List<String> problems = matchManager.validateArenas();
        if (problems.isEmpty()) {
            getLogger().info("Arenas ranked verificadas: todo correcto.");
            return;
        }
        getLogger().warning("Hay " + problems.size() + " problema(s) con las arenas ranked:");
        for (String problem : problems) {
            getLogger().warning("  - " + problem);
        }
        getLogger().warning("Mientras no se arreglen, las colas afectadas no podran arrancar partidas.");
    }

    /**
     * Recarga config.yml y messages.yml sin reiniciar el servidor. Los cambios
     * de base de datos si necesitan reinicio.
     */
    public void reloadEverything() {
        rankedConfig.reload();
        messages.reload();
        sounds.reload();
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

    public SeasonManager seasonManager() {
        return seasonManager;
    }

    public DiscordWebhook discord() {
        return discord;
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
