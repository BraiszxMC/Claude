package com.braiszx.bbranked.season;

import com.braiszx.bbranked.config.RankTier;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.LeaderboardEntry;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.data.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Temporadas: cierre, premios y reset suave del Elo.
 */
public final class SeasonManager {

    private final Plugin plugin;
    private final RankedConfig config;
    private final StatsStorage storage;
    private final StatsManager stats;

    private volatile Season current;
    private volatile boolean closing;

    public SeasonManager(Plugin plugin, RankedConfig config, StatsStorage storage, StatsManager stats) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.stats = stats;
    }

    /**
     * Carga la temporada abierta, creando la primera si hace falta. Bloquea a
     * proposito: se llama en onEnable.
     */
    public void load() {
        if (!config.seasonEnabled()) {
            return;
        }
        this.current = storage.currentSeasonBlocking();
    }

    public Season current() {
        return current;
    }

    public boolean closing() {
        return closing;
    }

    /**
     * Cierra la temporada: guarda el ranking final, reparte premios, aplica el
     * reset suave y abre la siguiente.
     *
     * @param nextName nombre de la temporada nueva
     * @param feedback recibe mensajes de progreso en el hilo principal
     */
    public void closeSeason(String nextName, BiConsumer<String, List<LeaderboardEntry>> feedback) {
        if (!config.seasonEnabled()) {
            feedback.accept("Las temporadas estan desactivadas en el config.", List.of());
            return;
        }
        Season season = current;
        if (season == null) {
            feedback.accept("No hay ninguna temporada abierta.", List.of());
            return;
        }
        if (closing) {
            feedback.accept("Ya se esta cerrando la temporada, espera.", List.of());
            return;
        }
        closing = true;

        // Se guardan primero los datos que hay en memoria, o el ranking final
        // saldria desactualizado.
        stats.saveAll();

        storage.runAsync(() -> {
            List<LeaderboardEntry> ranking = storage.allRankedPlayersBlocking();
            storage.saveSeasonResultsBlocking(season.id(), ranking);
            storage.closeSeasonBlocking(season.id());
            storage.softResetAllBlocking(config.seasonResetTarget(), config.seasonResetFactor());
            Season next = storage.startSeasonBlocking(nextName);

            Bukkit.getScheduler().runTask(plugin, () -> {
                this.current = next;
                this.closing = false;

                // La cache en memoria tiene el Elo viejo: hay que recargarla.
                stats.reloadOnlinePlayers();
                stats.refreshLeaderboard();

                giveRewards(ranking);
                feedback.accept("Temporada '" + season.name() + "' cerrada con "
                        + ranking.size() + " jugadores. Empieza '" + nextName + "'.", ranking);
            });
        });
    }

    /**
     * Ejecuta los comandos de premio. Va en el hilo principal porque
     * dispatchCommand no se puede llamar desde otro sitio.
     */
    private void giveRewards(List<LeaderboardEntry> ranking) {
        for (int i = 0; i < ranking.size(); i++) {
            LeaderboardEntry entry = ranking.get(i);
            int position = i + 1;

            List<String> byPosition = config.seasonTopRewards().get(position);
            if (byPosition != null) {
                run(byPosition, entry, position);
            }

            RankTier tier = config.rankOf(entry.elo());
            List<String> byRank = config.seasonRankRewards().get(tier.id());
            if (byRank != null) {
                run(byRank, entry, position);
            }
        }
    }

    private void run(List<String> commands, LeaderboardEntry entry, int position) {
        Season season = current;
        for (String command : commands) {
            String parsed = command
                    .replace("{player}", entry.name())
                    .replace("{position}", String.valueOf(position))
                    .replace("{elo}", String.valueOf(entry.elo()))
                    .replace("{season}", season == null ? "?" : season.name());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Error ejecutando el premio de temporada '"
                        + parsed + "': " + exception.getMessage());
            }
        }
    }
}
