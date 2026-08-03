package com.braiszx.bbranked.data;

import com.braiszx.bbranked.config.RankedConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Baja el Elo de quien lleva mucho sin jugar.
 *
 * <p>Todo el trabajo va en SQL sobre el hilo de la base de datos: puede tocar
 * a miles de filas y no puede pasar por la cache ni por el hilo principal.</p>
 */
public final class DecayService {

    private final Plugin plugin;
    private final RankedConfig config;
    private final StatsManager stats;

    public DecayService(Plugin plugin, RankedConfig config, StatsManager stats) {
        this.plugin = plugin;
        this.config = config;
        this.stats = stats;
    }

    /**
     * Pasa el cobro de inactividad.
     */
    public void run() {
        if (!config.decayEnabled() || config.decayEloPerDay() <= 0) {
            return;
        }

        // Primero se vuelca lo que hay en memoria: si no, al recargar despues
        // se perderian los cambios de los que estan jugando ahora.
        stats.saveAll();

        int minMatches = config.decayRequirePlacements() ? config.placementMatches() : 0;
        stats.storage().applyDecay(config.decayInactiveDays(), config.decayEloPerDay(),
                        config.decayFloor(), minMatches)
                .thenAccept(affected -> {
                    if (affected <= 0) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().info("Decaimiento por inactividad aplicado a "
                                + affected + " jugador(es).");
                        // Los conectados no deberian verse afectados, pero por
                        // si acaso se recarga su cache.
                        stats.reloadOnlinePlayers();
                        stats.refreshLeaderboard();
                    });
                });
    }
}
