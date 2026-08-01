package com.braiszx.bbranked.data;

import java.util.UUID;

/**
 * Una fila del ranking global.
 */
public record LeaderboardEntry(UUID uuid, String name, int elo, int wins, int losses, int draws) {

    public int matches() {
        return wins + losses + draws;
    }
}
