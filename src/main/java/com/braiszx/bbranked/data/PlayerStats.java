package com.braiszx.bbranked.data;

import java.util.UUID;

/**
 * Estadisticas ranked de un jugador. Mutable, vive en cache mientras esta online.
 */
public final class PlayerStats {

    private final UUID uuid;
    private String name;
    private int elo;
    private int peakElo;
    private int wins;
    private int losses;
    private int draws;
    private int goals;
    private int leaves;
    private int winStreak;
    private int bestStreak;
    private long lastSeen;

    public PlayerStats(UUID uuid, String name, int elo) {
        this.uuid = uuid;
        this.name = name;
        this.elo = elo;
        this.peakElo = elo;
        this.lastSeen = System.currentTimeMillis();
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public int elo() {
        return elo;
    }

    public void elo(int elo) {
        this.elo = elo;
        if (elo > peakElo) {
            this.peakElo = elo;
        }
    }

    public int peakElo() {
        return peakElo;
    }

    public void peakElo(int peakElo) {
        this.peakElo = peakElo;
    }

    public int wins() {
        return wins;
    }

    public void wins(int wins) {
        this.wins = wins;
    }

    public int losses() {
        return losses;
    }

    public void losses(int losses) {
        this.losses = losses;
    }

    public int draws() {
        return draws;
    }

    public void draws(int draws) {
        this.draws = draws;
    }

    public int goals() {
        return goals;
    }

    public void goals(int goals) {
        this.goals = goals;
    }

    public void addGoals(int amount) {
        this.goals += amount;
    }

    public int leaves() {
        return leaves;
    }

    public void leaves(int leaves) {
        this.leaves = leaves;
    }

    public int winStreak() {
        return winStreak;
    }

    public void winStreak(int winStreak) {
        this.winStreak = winStreak;
        if (winStreak > bestStreak) {
            this.bestStreak = winStreak;
        }
    }

    public int bestStreak() {
        return bestStreak;
    }

    public void bestStreak(int bestStreak) {
        this.bestStreak = bestStreak;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public int matches() {
        return wins + losses + draws;
    }

    public double winRate() {
        int played = matches();
        return played == 0 ? 0.0D : (wins * 100.0D) / played;
    }

    /**
     * Anota el resultado de una partida (sin tocar el Elo, de eso se encarga
     * el calculador).
     */
    public void recordResult(int outcome) {
        if (outcome > 0) {
            wins++;
            winStreak(winStreak + 1);
        } else if (outcome < 0) {
            losses++;
            winStreak(0);
        } else {
            draws++;
        }
    }
}
