package com.braiszx.bbranked.queue;

import com.braiszx.bbranked.ban.BanEntry;
import com.braiszx.bbranked.ban.BanManager;
import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.party.Party;
import com.braiszx.bbranked.party.PartyManager;
import com.braiszx.bbranked.util.Messages;
import com.braiszx.bbranked.util.Sounds;
import net.kyori.adventure.text.Component;
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
 * ensancha cuanto mas espera el ticket.
 *
 * <p>La cola guarda {@link QueueTicket}, no jugadores sueltos: asi una party
 * entra y sale de la cola como una unidad y siempre acaba en el mismo
 * equipo.</p>
 */
public final class QueueManager {

    private final RankedConfig config;
    private final Messages messages;
    private final StatsManager stats;
    private final MatchManager matches;
    private final BanManager bans;
    private final PartyManager parties;
    private final Sounds sounds;

    /** modo -> tickets en cola, en orden de llegada. */
    private final Map<String, List<QueueTicket>> queues = new ConcurrentHashMap<>();
    /** jugador -> modo en el que esta encolado. */
    private final Map<UUID, String> playerQueue = new ConcurrentHashMap<>();
    /** jugador -> momento (millis) hasta el que no puede encolar por abandono. */
    private final Map<UUID, Long> penalties = new ConcurrentHashMap<>();

    public QueueManager(RankedConfig config, Messages messages, StatsManager stats, MatchManager matches,
                        BanManager bans, PartyManager parties, Sounds sounds) {
        this.config = config;
        this.messages = messages;
        this.stats = stats;
        this.matches = matches;
        this.bans = bans;
        this.parties = parties;
        this.sounds = sounds;
    }

    // ------------------------------------------------------------------
    //  Entrada y salida
    // ------------------------------------------------------------------

    /**
     * Mete al jugador en la cola. Si esta en una party, entra la party entera
     * y solo puede hacerlo el lider.
     */
    public boolean join(Player player, QueueMode mode) {
        UUID uuid = player.getUniqueId();
        Party party = config.partyEnabled() ? parties.partyOf(uuid) : null;

        if (party != null && party.size() > 1) {
            if (!party.isLeader(uuid)) {
                messages.send(player, "party.only-leader-queues");
                return false;
            }
            return joinAsParty(player, party, mode);
        }
        return joinAsSolo(player, mode);
    }

    private boolean joinAsSolo(Player player, QueueMode mode) {
        UUID uuid = player.getUniqueId();
        if (!canQueue(player, player)) {
            return false;
        }

        String currentQueue = playerQueue.get(uuid);
        if (currentQueue != null) {
            if (currentQueue.equals(mode.id())) {
                messages.send(player, "queue.already-in-queue", Map.of("mode", mode.displayName()));
                return false;
            }
            remove(uuid);
        }

        QueueEntry entry = new QueueEntry(uuid, stats.getOrDefault(player).elo(), BanManager.ipOf(player));
        addTicket(mode, QueueTicket.solo(entry));

        messages.send(player, "queue.joined", Map.of(
                "mode", mode.displayName(),
                "current", String.valueOf(queuedPlayers(mode)),
                "needed", String.valueOf(mode.requiredPlayers())));

        sounds.play(player, "queue-join");
        warnIfNoArena(player, mode);
        tryMatch(mode);
        return true;
    }

    private boolean joinAsParty(Player leader, Party party, QueueMode mode) {
        // Una party de 3 no cabe en un equipo de 2: no tiene sentido encolar.
        if (party.size() > mode.teamSize()) {
            messages.send(leader, "party.too-big-for-mode", Map.of(
                    "mode", mode.displayName(),
                    "size", String.valueOf(party.size()),
                    "teamsize", String.valueOf(mode.teamSize())));
            return false;
        }

        List<Player> online = new ArrayList<>();
        for (UUID member : party.members()) {
            Player memberPlayer = Bukkit.getPlayer(member);
            if (memberPlayer == null) {
                messages.send(leader, "party.member-offline");
                return false;
            }
            if (!canQueue(memberPlayer, leader)) {
                return false;
            }
            online.add(memberPlayer);
        }

        // Si ya estaban en cola, se sacan primero para no duplicar.
        for (Player memberPlayer : online) {
            remove(memberPlayer.getUniqueId());
        }

        List<QueueEntry> entries = new ArrayList<>(online.size());
        for (Player memberPlayer : online) {
            entries.add(new QueueEntry(memberPlayer.getUniqueId(),
                    stats.getOrDefault(memberPlayer).elo(), BanManager.ipOf(memberPlayer)));
        }
        addTicket(mode, new QueueTicket(entries, party.id()));

        Map<String, String> placeholders = Map.of(
                "mode", mode.displayName(),
                "current", String.valueOf(queuedPlayers(mode)),
                "needed", String.valueOf(mode.requiredPlayers()),
                "size", String.valueOf(party.size()));
        for (Player memberPlayer : online) {
            messages.send(memberPlayer, "party.queued", placeholders);
            sounds.play(memberPlayer, "queue-join");
        }

        warnIfNoArena(leader, mode);
        tryMatch(mode);
        return true;
    }

    /**
     * Comprobaciones comunes. Los avisos van a {@code notify}, que en una
     * party es el lider.
     */
    private boolean canQueue(Player player, Player notify) {
        UUID uuid = player.getUniqueId();

        if (matches.isInMatch(uuid)) {
            messages.send(notify, player == notify ? "queue.already-in-match" : "party.member-in-match",
                    Map.of("player", player.getName()));
            return false;
        }

        BanEntry ban = bans.activeBanFor(player);
        if (ban != null) {
            if (player == notify) {
                messages.send(notify, ban.type() == BanEntry.BanType.IP
                                ? "queue.ip-banned" : "queue.ranked-banned",
                        Map.of("reason", ban.reason(), "expires", ban.remainingText()));
            } else {
                messages.send(notify, "party.member-banned", Map.of("player", player.getName()));
            }
            return false;
        }

        long remaining = penaltySecondsLeft(uuid);
        if (remaining > 0) {
            if (player == notify) {
                messages.send(notify, "queue.banned", Map.of("seconds", String.valueOf(remaining)));
            } else {
                messages.send(notify, "party.member-penalized", Map.of(
                        "player", player.getName(), "seconds", String.valueOf(remaining)));
            }
            return false;
        }
        return true;
    }

    private void warnIfNoArena(Player player, QueueMode mode) {
        if (matches.freeArenas(mode) <= 0) {
            messages.send(player, "queue.no-arena", Map.of("mode", mode.displayName()));
        }
    }

    private void addTicket(QueueMode mode, QueueTicket ticket) {
        List<QueueTicket> queue = queues.computeIfAbsent(mode.id(),
                key -> Collections.synchronizedList(new ArrayList<>()));
        queue.add(ticket);
        for (UUID uuid : ticket.uuids()) {
            playerQueue.put(uuid, mode.id());
        }
    }

    public boolean leave(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        QueueTicket ticket = ticketOf(uuid);
        boolean removed = remove(uuid);

        if (notify) {
            messages.send(player, removed ? "queue.left" : "queue.not-in-queue");
            sounds.play(player, removed ? "queue-leave" : "error");
        }
        // Si se sale un miembro de una party encolada, la party entera sale.
        if (removed && ticket != null && ticket.isParty()) {
            for (UUID member : ticket.uuids()) {
                Player memberPlayer = Bukkit.getPlayer(member);
                if (memberPlayer != null && !member.equals(uuid)) {
                    messages.send(memberPlayer, "party.queue-cancelled");
                }
            }
        }
        return removed;
    }

    private QueueTicket ticketOf(UUID uuid) {
        String modeId = playerQueue.get(uuid);
        if (modeId == null) {
            return null;
        }
        List<QueueTicket> queue = queues.get(modeId);
        if (queue == null) {
            return null;
        }
        synchronized (queue) {
            for (QueueTicket ticket : queue) {
                if (ticket.contains(uuid)) {
                    return ticket;
                }
            }
        }
        return null;
    }

    /**
     * Saca al jugador de la cola. Si iba en una party, saca al ticket entero.
     */
    public boolean remove(UUID uuid) {
        String modeId = playerQueue.remove(uuid);
        if (modeId == null) {
            return false;
        }
        List<QueueTicket> queue = queues.get(modeId);
        if (queue == null) {
            return true;
        }

        synchronized (queue) {
            QueueTicket found = null;
            for (QueueTicket ticket : queue) {
                if (ticket.contains(uuid)) {
                    found = ticket;
                    break;
                }
            }
            if (found != null) {
                queue.remove(found);
                for (UUID member : found.uuids()) {
                    playerQueue.remove(member);
                }
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

    /**
     * Jugadores (no tickets) esperando en un modo.
     */
    public int queuedPlayers(QueueMode mode) {
        List<QueueTicket> queue = queues.get(mode.id());
        if (queue == null) {
            return 0;
        }
        int total = 0;
        synchronized (queue) {
            for (QueueTicket ticket : queue) {
                total += ticket.size();
            }
        }
        return total;
    }

    public int size(QueueMode mode) {
        return queuedPlayers(mode);
    }

    // ------------------------------------------------------------------
    //  Penalizaciones
    // ------------------------------------------------------------------

    public void penalize(UUID uuid) {
        if (config.leaverQueueBanSeconds() > 0) {
            penalties.put(uuid, System.currentTimeMillis() + config.leaverQueueBanSeconds() * 1000L);
        }
    }

    /**
     * Segundos que le quedan de penalizacion, o 0 si no tiene.
     */
    public long penaltySecondsLeft(UUID uuid) {
        long until = penalties.getOrDefault(uuid, 0L);
        long remaining = until - System.currentTimeMillis();
        return remaining <= 0 ? 0 : remaining / 1000L + 1;
    }

    /**
     * Quita la penalizacion por abandono. Devuelve true si tenia una activa.
     */
    public boolean clearPenalty(UUID uuid) {
        Long until = penalties.remove(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Quita todas las penalizaciones. Devuelve cuantas habia activas.
     */
    public int clearAllPenalties() {
        long now = System.currentTimeMillis();
        int active = 0;
        for (Long until : penalties.values()) {
            if (until > now) {
                active++;
            }
        }
        penalties.clear();
        return active;
    }

    public Map<String, Integer> sizes() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (QueueMode mode : config.modes().values()) {
            result.put(mode.displayName(), queuedPlayers(mode));
        }
        return result;
    }

    // ------------------------------------------------------------------
    //  Emparejamiento
    // ------------------------------------------------------------------

    /**
     * Refresca la barra de accion de todos los que estan esperando en cola.
     * Se llama mas a menudo que {@link #tick()}, solo pinta texto.
     */
    public void updateActionbars() {
        if (!config.actionbarQueueEnabled()) {
            return;
        }

        for (QueueMode mode : config.modes().values()) {
            List<QueueTicket> queue = queues.get(mode.id());
            if (queue == null) {
                continue;
            }

            List<QueueTicket> snapshot;
            synchronized (queue) {
                if (queue.isEmpty()) {
                    continue;
                }
                snapshot = new ArrayList<>(queue);
            }

            int current = queuedPlayers(mode);
            for (QueueTicket ticket : snapshot) {
                Component text = messages.raw("actionbar.queue", Map.of(
                        "mode", mode.displayName(),
                        "current", String.valueOf(current),
                        "needed", String.valueOf(mode.requiredPlayers()),
                        "time", formatWait(ticket.waitedSeconds())));

                for (UUID uuid : ticket.uuids()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.sendActionBar(text);
                    }
                }
            }
        }
    }

    private static String formatWait(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Se llama periodicamente desde el hilo principal.
     */
    public void tick() {
        parties.purgeExpiredInvites();
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
     * @return true si se ha lanzado una partida
     */
    private boolean tryMatch(QueueMode mode) {
        List<QueueTicket> queue = queues.get(mode.id());
        if (queue == null) {
            return false;
        }

        List<QueueTicket> snapshot;
        synchronized (queue) {
            snapshot = new ArrayList<>(queue);
        }

        int totalPlayers = 0;
        for (QueueTicket ticket : snapshot) {
            totalPlayers += ticket.size();
        }
        if (totalPlayers < mode.requiredPlayers()) {
            return false;
        }

        Selection selection = findCompatibleGroup(snapshot, mode);
        if (selection == null) {
            return false;
        }

        List<Player> players = new ArrayList<>();
        for (QueueTicket ticket : selection.tickets()) {
            for (UUID uuid : ticket.uuids()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    // Se ha ido sin avisar: se limpia y se reintenta al siguiente tick.
                    remove(uuid);
                    return false;
                }
                players.add(player);
            }
        }

        if (matches.freeArenas(mode) <= 0) {
            return false;
        }

        // Se sacan de la cola antes de arrancar para que no entren dos veces.
        for (QueueTicket ticket : selection.tickets()) {
            for (UUID uuid : ticket.uuids()) {
                remove(uuid);
            }
        }

        List<Player> red = toPlayers(selection.split().red());
        List<Player> blue = toPlayers(selection.split().blue());
        if (red == null || blue == null) {
            requeue(mode, selection.tickets());
            return false;
        }

        // startMatch mete a los jugadores en la arena fuera del hilo principal
        // (BlockBall lo exige) y solo se sabe si ha ido bien mas tarde: por
        // eso el reintento va en un callback en vez de en el valor devuelto.
        boolean dispatched = matches.startMatch(mode, red, blue, () -> requeue(mode, selection.tickets()));
        if (!dispatched) {
            requeue(mode, selection.tickets());
            return false;
        }
        return true;
    }

    private List<Player> toPlayers(List<UUID> uuids) {
        List<Player> players = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                return null;
            }
            players.add(player);
        }
        return players;
    }

    /**
     * Devuelve unos tickets a la cola conservando su Elo.
     */
    private void requeue(QueueMode mode, List<QueueTicket> tickets) {
        List<QueueTicket> target = queues.computeIfAbsent(mode.id(),
                key -> Collections.synchronizedList(new ArrayList<>()));
        for (QueueTicket ticket : tickets) {
            boolean allOnline = true;
            for (UUID uuid : ticket.uuids()) {
                if (Bukkit.getPlayer(uuid) == null) {
                    allOnline = false;
                    break;
                }
            }
            if (allOnline) {
                target.add(ticket);
                for (UUID uuid : ticket.uuids()) {
                    playerQueue.put(uuid, mode.id());
                }
            }
        }
    }

    /**
     * Un grupo de tickets que suma justo los jugadores necesarios, junto con
     * el reparto en equipos que se usara.
     */
    private record Selection(List<QueueTicket> tickets, TeamSplit split) {
    }

    /**
     * Busca el primer grupo de tickets compatible entre si que ademas se pueda
     * repartir en dos equipos sin partir ninguna party.
     */
    private Selection findCompatibleGroup(List<QueueTicket> queue, QueueMode mode) {
        List<QueueTicket> sorted = new ArrayList<>(queue);
        // Mas tiempo esperando primero.
        sorted.sort((a, b) -> Long.compare(b.waitedSeconds(), a.waitedSeconds()));

        int needed = mode.requiredPlayers();

        for (int i = 0; i < sorted.size(); i++) {
            QueueTicket anchor = sorted.get(i);
            if (anchor.size() > mode.teamSize()) {
                continue;
            }

            List<QueueTicket> group = new ArrayList<>();
            group.add(anchor);
            int total = anchor.size();

            // Los candidatos mas cercanos en Elo al ancla.
            List<QueueTicket> candidates = new ArrayList<>(sorted);
            candidates.remove(anchor);
            candidates.sort((a, b) -> Integer.compare(
                    Math.abs(a.averageElo() - anchor.averageElo()),
                    Math.abs(b.averageElo() - anchor.averageElo())));

            for (QueueTicket candidate : candidates) {
                if (total == needed) {
                    break;
                }
                if (candidate.size() > mode.teamSize() || total + candidate.size() > needed) {
                    continue;
                }
                if (fitsInGroup(candidate, group)) {
                    group.add(candidate);
                    total += candidate.size();
                }
            }

            if (total != needed) {
                continue;
            }

            // Puede que el grupo sume bien pero no se pueda repartir sin
            // partir una party (p. ej. dos parties de 2 en un 3v3).
            TeamSplit split = TeamSplit.best(group, mode.teamSize());
            if (split != null) {
                return new Selection(group, split);
            }
        }
        return null;
    }

    /**
     * El candidato entra si su ventana y la de todos los del grupo se aceptan
     * mutuamente.
     */
    private boolean fitsInGroup(QueueTicket candidate, List<QueueTicket> group) {
        int initial = config.queueInitialRange();
        int perSecond = config.queueRangeExpansionPerSecond();
        int max = config.queueMaxRange();

        int candidateRange = candidate.acceptedRange(initial, perSecond, max);
        for (QueueTicket member : group) {
            // Anti-multicuentas: nadie comparte partida con su propia IP.
            // Se comprueba contra todo el grupo, no solo contra el equipo
            // rival, porque los equipos se reparten despues segun el Elo.
            if (config.blockSameIp() && candidate.sharesIpWith(member)) {
                return false;
            }
            int difference = Math.abs(member.averageElo() - candidate.averageElo());
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
