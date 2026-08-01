package com.braiszx.bbranked.match;

import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.elo.MatchWinner;
import com.github.shynixn.blockball.enumeration.Team;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Una partida ranked en curso. Guarda su propia plantilla porque la de
 * BlockBall se vacia cuando la partida termina.
 */
public final class RankedMatch {

    private final UUID id = UUID.randomUUID();
    private final QueueMode mode;
    private final String arenaName;
    private final List<UUID> red;
    private final List<UUID> blue;
    private final long createdAt = System.currentTimeMillis();
    private final Set<UUID> leavers = new LinkedHashSet<>();
    private final Map<UUID, Integer> goals = new LinkedHashMap<>();

    private boolean started;
    private boolean settled;
    private MatchWinner pendingWinner;
    private int redScore;
    private int blueScore;

    public RankedMatch(QueueMode mode, String arenaName, List<UUID> red, List<UUID> blue) {
        this.mode = mode;
        this.arenaName = arenaName;
        this.red = List.copyOf(red);
        this.blue = List.copyOf(blue);
    }

    public UUID id() {
        return id;
    }

    public QueueMode mode() {
        return mode;
    }

    public String arenaName() {
        return arenaName;
    }

    public List<UUID> red() {
        return red;
    }

    public List<UUID> blue() {
        return blue;
    }

    public List<UUID> allPlayers() {
        List<UUID> all = new ArrayList<>(red.size() + blue.size());
        all.addAll(red);
        all.addAll(blue);
        return all;
    }

    public Team teamOf(UUID uuid) {
        if (red.contains(uuid)) {
            return Team.RED;
        }
        if (blue.contains(uuid)) {
            return Team.BLUE;
        }
        return null;
    }

    public long createdAt() {
        return createdAt;
    }

    public long ageSeconds() {
        return (System.currentTimeMillis() - createdAt) / 1000L;
    }

    public boolean started() {
        return started;
    }

    public void started(boolean started) {
        this.started = started;
    }

    public boolean settled() {
        return settled;
    }

    public void settled(boolean settled) {
        this.settled = settled;
    }

    public MatchWinner pendingWinner() {
        return pendingWinner;
    }

    public void pendingWinner(MatchWinner pendingWinner) {
        this.pendingWinner = pendingWinner;
    }

    public int redScore() {
        return redScore;
    }

    public int blueScore() {
        return blueScore;
    }

    /**
     * Guarda el marcador visto en el ultimo tick, porque BlockBall lo resetea
     * al cerrar la partida.
     */
    public void updateScore(int redScore, int blueScore) {
        this.redScore = redScore;
        this.blueScore = blueScore;
    }

    public Set<UUID> leavers() {
        return leavers;
    }

    public void addLeaver(UUID uuid) {
        leavers.add(uuid);
    }

    public Map<UUID, Integer> goals() {
        return goals;
    }

    public void addGoal(UUID uuid) {
        goals.merge(uuid, 1, Integer::sum);
    }

    /**
     * Ganador segun el marcador guardado, para cuando BlockBall no dispara
     * GameEndEvent (por ejemplo en los empates).
     */
    public MatchWinner winnerFromScore() {
        if (redScore > blueScore) {
            return MatchWinner.RED;
        }
        if (blueScore > redScore) {
            return MatchWinner.BLUE;
        }
        return MatchWinner.DRAW;
    }
}
