package com.braiszx.bbranked.queue;

import com.braiszx.bbranked.ban.BanEntry;
import com.braiszx.bbranked.ban.BanManager;
import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Colas de emparejamiento. Una cola por modo, con ventana de Elo que se
 * ensancha cuanto mas espera el jugador.
 */
public final class QueueManager {

    private final RankedConfig config;
    private final Messages messages;
    private final StatsManager stats;
    private final MatchManager matches;
    private final BanManager bans;

    /** modo -> jugadores en cola, en orden de llegada. */
    private final Map<String, List<QueueEntry>> queues = new ConcurrentHashMap<>();
    /** jugador -> modo en el que esta encolado. */
    private final Map<UUID, String> playerQueue = new ConcurrentHashMap<>();
    /** jugador -> momento (millis) hasta el que no puede encolar por abandono. */
    private final Map<UUID, Long> penalties = new ConcurrentHashMap<>();

    public QueueManager(RankedConfig config, Messages messages, StatsManager stats, MatchManager matches,
                        BanManager bans) {
        this.config = config;
        this.messages = messages;
        this.stats = stats;
        this.matches = matches;
        this.bans = bans;
    }

    // ------------------------------------------------------------------
    //  Entrada y salida
    // ------------------------------------------------------------------

    public boolean join(Player player, QueueMode mode) {
        UUID uuid = player.getUniqueId();

        if (matches.isInMatch(uuid)) {
            messages.send(player, "queue.already-in-match");
            return false;
        }

        BanEntry ban = bans.activeBanFor(player);
        if (ban != null) {
            messages.send(player, ban.type() == BanEntry.BanType.IP ? "queue.ip-banned" : "queue.ranked-banned",
                    Map.of("reason", ban.reason(), "expires", ban.remainingText()));
            return false;
        }

        long penaltyUntil = penalties.getOrDefault(uuid, 0L);
        if (penaltyUntil > System.currentTimeMillis()) {
            long remaining = (penaltyUntil - System.currentTimeMillis()) / 1000L + 1;
            messages.send(player, "queue.banned", Map.of("seconds", String.valueOf(remaining)));
            return false;
        }

        String currentQueue = playerQueue.get(uuid);
        if (currentQueue != null) {
            if (currentQueue.equals(mode.id())) {
                messages.send(player, "queue.already-in-queue", Map.of("mode", mode.displayName()));
                return false;
            }
            leave(player, false);
        }

        List<QueueEntry> queue = queues.computeIfAbsent(mode.id(), key -> Collections.synchronizedList(new ArrayList<>()));
        queue.add(new QueueEntry(uuid, stats.getOrDefault(player).elo(), BanManager.ipOf(player)));
        playerQueue.put(uuid, mode.id());

        messages.send(player, "queue.joined", Map.of(
                "mode", mode.displayName(),
                "current", String.valueOf(queue.size()),
                "needed", String.valueOf(mode.requiredPlayers())));

        // Avisar ya si no hay donde jugar, en vez de dejarle esperando sin
        // saber que la cola no puede arrancar nada.
        if (matches.freeArenas(mode) <= 0) {
            messages.send(player, "queue.no-arena", Map.of("mode", mode.displayName()));
        }

        // Emparejar ya, por si con este jugador ya se completa una partida.
        tryMatch(mode);
        return true;
    }

    public boolean leave(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        boolean removed = remove(uuid);
        if (notify) {
            messages.send(player, removed ? "queue.left" : "queue.not-in-queue");
        }
        return removed;
    }

    /**
     * Saca al jugador de cualquier cola. Se usa tambien al desconectarse.
     */
    public boolean remove(UUID uuid) {
        String modeId = playerQueue.remove(uuid);
        if (modeId == null) {
            return false;
        }
        List<QueueEntry> queue = queues.get(modeId);
        if (queue != null) {
            synchronized (queue) {
                queue.removeIf(entry -> entry.uuid().equals(uuid));
            }
        }
        return true;
    }

    public boolean isQueued(UUID uuid) {
        return playerQueue.containsKey(uuid);
    }

    public String queuedMode(UUID uuid) {
        return playerQueue.get(uuid);
    }

    public int size(QueueMode mode) {
        List<QueueEntry> queue = queues.get(mode.id());
        return queue == null ? 0 : queue.size();
    }

    /**
     * Aplica la penalizacion por abandono.
     */
    public void penalize(UUID uuid) {
        if (config.leaverQueueBanSeconds() > 0) {
            penalties.put(uuid, System.currentTimeMillis() + config.leaverQueueBanSeconds() * 1000L);
        }
    }

    public Map<String, Integer> sizes() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (QueueMode mode : config.modes().values()) {
            result.put(mode.displayName(), size(mode));
        }
        return result;
    }

    // ------------------------------------------------------------------
    //  Emparejamiento
    // ------------------------------------------------------------------

    /**
     * Se llama periodicamente desde el hilo principal.
     */
    public void tick() {
        for (QueueMode mode : config.modes().values()) {
            // Un modo puede llenar varias arenas seguidas si hay gente de sobra.
            while (tryMatch(mode)) {
                // sigue mientras se puedan formar partidas
            }
        }
    }

    /**
     * Intenta formar una partida en el modo dado.
     *
     * @return true si se ha creado una partida
     */
    private boolean tryMatch(QueueMode mode) {
        List<QueueEntry> queue = queues.get(mode.id());
        if (queue == null) {
            return false;
        }

        List<QueueEntry> snapshot;
        synchronized (queue) {
            if (queue.size() < mode.requiredPlayers()) {
                return false;
            }
            snapshot = new ArrayList<>(queue);
        }

        List<QueueEntry> group = findCompatibleGroup(snapshot, mode.requiredPlayers());
        if (group == null) {
            return false;
        }

        List<Player> players = new ArrayList<>(group.size());
        for (QueueEntry entry : group) {
            Player player = Bukkit.getPlayer(entry.uuid());
            if (player == null || !player.isOnline()) {
                // Se ha ido sin avisar: se limpia y se reintenta al siguiente tick.
                remove(entry.uuid());
                return false;
            }
            players.add(player);
        }

        if (matches.freeArenas(mode) <= 0) {
            // No avisamos cada tick: solo cuando alguien lleva un rato esperando.
            if (group.get(0).waitedSeconds() % 30 == 0) {
                for (Player player : players) {
                    messages.send(player, "queue.no-arena", Map.of("mode", mode.displayName()));
                }
            }
            return false;
        }

        // Se sacan de la cola antes de arrancar para que no entren dos veces.
        for (QueueEntry entry : group) {
            remove(entry.uuid());
        }

        // startMatch mete a los jugadores en la arena fuera del hilo principal
        // (BlockBall lo exige) y solo se sabe si ha ido bien mas tarde: por
        // eso el reintento va en un callback en vez de en el valor devuelto.
        boolean dispatched = matches.startMatch(mode, players, () -> requeue(mode, group));
        if (!dispatched) {
            requeue(mode, group);
            return false;
        }
        return true;
    }

    /**
     * Devuelve un grupo a la cola conservando su Elo y su tiempo de espera
     * original.
     */
    private void requeue(QueueMode mode, List<QueueEntry> group) {
        List<QueueEntry> target = queues.computeIfAbsent(mode.id(),
                key -> Collections.synchronizedList(new ArrayList<>()));
        for (QueueEntry entry : group) {
            if (Bukkit.getPlayer(entry.uuid()) != null) {
                target.add(entry);
                playerQueue.put(entry.uuid(), mode.id());
            }
        }
    }

    /**
     * Busca el primer grupo de {@code needed} jugadores cuyos rangos de Elo
     * sean compatibles entre si. Se prioriza a los que llevan mas esperando.
     */
    private List<QueueEntry> findCompatibleGroup(List<QueueEntry> queue, int needed) {
        List<QueueEntry> sorted = new ArrayList<>(queue);
        // Mas tiempo esperando primero.
        sorted.sort((a, b) -> Long.compare(b.waitedSeconds(), a.waitedSeconds()));

        for (int i = 0; i <= sorted.size() - needed; i++) {
            QueueEntry anchor = sorted.get(i);
            List<QueueEntry> group = new ArrayList<>(needed);
            group.add(anchor);

            // Los candidatos mas cercanos en Elo al ancla.
            List<QueueEntry> candidates = new ArrayList<>(sorted.subList(i + 1, sorted.size()));
            candidates.sort((a, b) -> Integer.compare(
                    Math.abs(a.elo() - anchor.elo()), Math.abs(b.elo() - anchor.elo())));

            for (QueueEntry candidate : candidates) {
                if (group.size() == needed) {
                    break;
                }
                if (fitsInGroup(candidate, group)) {
                    group.add(candidate);
                }
            }

            if (group.size() == needed) {
                return group;
            }
        }
        return null;
    }

    /**
     * El candidato entra si su ventana y la de todos los del grupo se aceptan
     * mutuamente.
     */
    private boolean fitsInGroup(QueueEntry candidate, List<QueueEntry> group) {
        int initial = config.queueInitialRange();
        int perSecond = config.queueRangeExpansionPerSecond();
        int max = config.queueMaxRange();

        int candidateRange = candidate.acceptedRange(initial, perSecond, max);
        for (QueueEntry member : group) {
            // Anti-multicuentas: nadie comparte partida con su propia IP.
            // Se comprueba contra todo el grupo, no solo contra el equipo
            // rival, porque los equipos se reparten despues segun el Elo.
            if (config.blockSameIp() && candidate.sharesIpWith(member)) {
                return false;
            }
            int difference = Math.abs(member.elo() - candidate.elo());
            if (difference > candidateRange) {
                return false;
            }
            if (difference > member.acceptedRange(initial, perSecond, max)) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        queues.clear();
        playerQueue.clear();
    }
}
