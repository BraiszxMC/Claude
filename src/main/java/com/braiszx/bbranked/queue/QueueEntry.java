package com.braiszx.bbranked.queue;

import java.util.UUID;

/**
 * Un jugador esperando en la cola.
 */
public final class QueueEntry {

    private final UUID uuid;
    private final int elo;
    private final long joinedAt;

    public QueueEntry(UUID uuid, int elo) {
        this.uuid = uuid;
        this.elo = elo;
        this.joinedAt = System.currentTimeMillis();
    }

    public UUID uuid() {
        return uuid;
    }

    public int elo() {
        return elo;
    }

    public long waitedSeconds() {
        return (System.currentTimeMillis() - joinedAt) / 1000L;
    }

    /**
     * Diferencia de Elo que este jugador acepta ahora mismo. Crece con la espera.
     */
    public int acceptedRange(int initial, int perSecond, int max) {
        long range = initial + waitedSeconds() * perSecond;
        return (int) Math.min(range, max);
    }
}
