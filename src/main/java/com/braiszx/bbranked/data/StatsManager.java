package com.braiszx.bbranked.data;

import com.braiszx.bbranked.config.RankedConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache en memoria de las estadisticas de los jugadores conectados, mas la
 * cache del ranking global.
 */
public final class StatsManager {

    private final RankedConfig config;
    private final StatsStorage storage;
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    private volatile List<LeaderboardEntry> leaderboard = Collections.emptyList();
    private volatile long leaderboardUpdatedAt;

    public StatsManager(RankedConfig config, StatsStorage storage) {
        this.config = config;
        this.storage = storage;
    }

    public StatsStorage storage() {
        return storage;
    }

    /**
     * Carga en cache al jugador. Se llama al conectarse.
     */
    public CompletableFuture<PlayerStats> load(Player player) {
        return storage.loadOrCreate(player.getUniqueId(), player.getName())
                .thenApply(stats -> {
                    cache.put(player.getUniqueId(), stats);
                    return stats;
                });
    }

    /**
     * Descarga de cache al jugador guardando antes sus datos.
     */
    public void unload(UUID uuid) {
        PlayerStats stats = cache.remove(uuid);
        if (stats != null) {
            stats.lastSeen(System.currentTimeMillis());
            storage.save(stats);
        }
    }

    /**
     * Estadisticas ya cacheadas. Null si el jugador no esta conectado.
     */
    public PlayerStats cached(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Estadisticas de un jugador conectado; si por lo que sea no estaban en
     * cache, devuelve un objeto por defecto para no romper nada.
     */
    public PlayerStats getOrDefault(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerStats(uuid, player.getName(), config.startingElo()));
    }

    /**
     * Estadisticas de un jugador que participa en una partida. Si por algun
     * motivo no estaban cacheadas, crea unas por defecto para no romper el
     * calculo del Elo.
     */
    public PlayerStats require(UUID uuid) {
        return cache.computeIfAbsent(uuid, key -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(key);
            String name = offline.getName() != null ? offline.getName() : key.toString().substring(0, 8);
            return new PlayerStats(key, name, config.startingElo());
        });
    }

    /**
     * Descarga al jugador solo si ya no esta conectado. Lo usa el gestor de
     * partidas al terminar, porque durante una partida hay que mantener en
     * cache incluso a los que se han desconectado.
     */
    public void unloadIfOffline(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null) {
            unload(uuid);
        }
    }

    /**
     * Busca por nombre: primero en cache, si no en la base de datos.
     */
    public CompletableFuture<PlayerStats> lookup(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerStats stats = cache.get(online.getUniqueId());
            if (stats != null) {
                return CompletableFuture.completedFuture(stats);
            }
        }
        return storage.loadByName(name);
    }

    public void save(PlayerStats stats) {
        storage.save(stats);
    }

    /**
     * Vuelve a leer de la base de datos las estadisticas de todos los
     * conectados. Se usa tras el reset de temporada, que cambia el Elo por
     * SQL y deja la cache desfasada.
     */
    public void reloadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            cache.remove(player.getUniqueId());
            load(player);
        }
    }

    public void saveAll() {
        List<PlayerStats> all = new ArrayList<>(cache.values());
        if (!all.isEmpty()) {
            storage.saveAll(all);
        }
    }

    /**
     * Ranking cacheado. Se refresca solo cuando caduca.
     */
    public List<LeaderboardEntry> leaderboard() {
        long age = System.currentTimeMillis() - leaderboardUpdatedAt;
        if (age > config.leaderboardRefreshSeconds() * 1000L) {
            refreshLeaderboard();
        }
        return leaderboard;
    }

    public void refreshLeaderboard() {
        // Marca ya el tiempo para no lanzar veinte consultas seguidas.
        leaderboardUpdatedAt = System.currentTimeMillis();
        storage.topPlayers(config.leaderboardPageSize() * 20, config.leaderboardMinMatches())
                .thenAccept(entries -> leaderboard = entries);
    }

    /**
     * Posicion en el ranking (1 = primero), o -1 si no aparece.
     */
    public int position(UUID uuid) {
        List<LeaderboardEntry> current = leaderboard;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    public void shutdown() {
        saveAll();
        cache.clear();
    }
}
