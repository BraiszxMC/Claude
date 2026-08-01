package com.braiszx.bbranked.listener;

import com.braiszx.bbranked.ban.BanEntry;
import com.braiszx.bbranked.ban.BanManager;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.match.RankedMatch;
import com.braiszx.bbranked.queue.QueueManager;
import com.braiszx.bbranked.util.Messages;
import com.github.shynixn.blockball.event.GameEndEvent;
import com.github.shynixn.blockball.event.GameGoalEvent;
import com.github.shynixn.blockball.event.GameJoinEvent;
import com.github.shynixn.blockball.event.GameLeaveEvent;
import com.github.shynixn.blockball.event.GameStartEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/**
 * Puente entre los eventos de BlockBall y el sistema ranked.
 */
public final class BlockBallListener implements Listener {

    private final RankedConfig config;
    private final Messages messages;
    private final MatchManager matches;
    private final QueueManager queues;
    private final BanManager bans;

    public BlockBallListener(RankedConfig config, Messages messages, MatchManager matches,
                             QueueManager queues, BanManager bans) {
        this.config = config;
        this.messages = messages;
        this.matches = matches;
        this.queues = queues;
        this.bans = bans;
    }

    /**
     * Impide que alguien entre a mano a una arena reservada para el ranked.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameJoin(GameJoinEvent event) {
        if (!config.blockManualJoin()) {
            return;
        }
        Player player = event.getPlayer();
        if (matches.isAuthorizedJoin(player.getUniqueId())) {
            return;
        }
        if (matches.isRankedArena(event.getGame().getArena().getName())) {
            event.setCancelled(true);
            // Un baneado que intente colarse en la arena recibe el motivo real
            // en vez del mensaje generico.
            BanEntry ban = bans.activeBanFor(player);
            if (ban != null) {
                messages.send(player, ban.type() == BanEntry.BanType.IP
                                ? "queue.ip-banned" : "queue.ranked-banned",
                        Map.of("reason", ban.reason(), "expires", ban.remainingText()));
                return;
            }
            messages.send(player, "match.blocked-join");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameStart(GameStartEvent event) {
        matches.onGameStarted(event.getGame());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGoal(GameGoalEvent event) {
        matches.onGoal(event.getGame(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameEnd(GameEndEvent event) {
        matches.onGameEnd(event.getGame(), event.getWinningTeam());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameLeave(GameLeaveEvent event) {
        Player player = event.getPlayer();
        RankedMatch match = matches.matchOf(player.getUniqueId());
        if (match == null || match.settled()) {
            return;
        }
        if (match.started()) {
            queues.penalize(player.getUniqueId());
        }
        matches.onPlayerLeft(player.getUniqueId());
    }
}
