package com.braiszx.bbranked.listener;

import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.match.RankedMatch;
import com.braiszx.bbranked.party.PartyManager;
import com.braiszx.bbranked.queue.QueueManager;
import com.braiszx.bbranked.ready.ReadyCheckManager;
import com.braiszx.bbranked.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Carga y descarga las estadisticas, y trata la desconexion como abandono si
 * el jugador estaba en una partida ranked.
 */
public final class PlayerConnectionListener implements Listener {

    private final StatsManager stats;
    private final QueueManager queues;
    private final MatchManager matches;
    private final PartyManager parties;
    private final Messages messages;
    private final ReadyCheckManager readyChecks;

    public PlayerConnectionListener(StatsManager stats, QueueManager queues, MatchManager matches,
                                    PartyManager parties, Messages messages,
                                    ReadyCheckManager readyChecks) {
        this.stats = stats;
        this.queues = queues;
        this.matches = matches;
        this.parties = parties;
        this.messages = messages;
        this.readyChecks = readyChecks;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        stats.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        queues.remove(uuid);
        readyChecks.remove(uuid);

        // Al desconectarse se sale de la party: si no, la party se quedaria
        // con un hueco imposible de llenar y nunca podria encolar.
        for (UUID member : parties.leave(uuid)) {
            Player memberPlayer = Bukkit.getPlayer(member);
            if (memberPlayer != null && !member.equals(uuid)) {
                messages.send(memberPlayer, "party.left", Map.of("player", event.getPlayer().getName()));
            }
        }

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
