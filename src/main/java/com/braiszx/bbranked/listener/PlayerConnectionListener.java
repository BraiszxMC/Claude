package com.braiszx.bbranked.listener;

import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.match.RankedMatch;
import com.braiszx.bbranked.queue.QueueManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Carga y descarga las estadisticas, y trata la desconexion como abandono si
 * el jugador estaba en una partida ranked.
 */
public final class PlayerConnectionListener implements Listener {

    private final StatsManager stats;
    private final QueueManager queues;
    private final MatchManager matches;

    public PlayerConnectionListener(StatsManager stats, QueueManager queues, MatchManager matches) {
        this.stats = stats;
        this.queues = queues;
        this.matches = matches;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        stats.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        queues.remove(uuid);

        RankedMatch match = matches.matchOf(uuid);
        if (match != null) {
            if (match.started() && !match.settled()) {
                queues.penalize(uuid);
            }
            matches.onPlayerLeft(uuid);
            // No se descargan las estadisticas: hacen falta para liquidar el Elo.
            return;
        }
        stats.unload(uuid);
    }
}
