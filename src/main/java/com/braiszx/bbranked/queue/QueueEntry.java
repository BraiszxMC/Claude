package com.braiszx.bbranked.queue;

import java.util.UUID;

/**
 * Un jugador esperando en la cola.
 */
public final class QueueEntry {

    private final UUID uuid;
    private final int elo;
    private final String ip;
    private final long joinedAt;

    public QueueEntry(UUID uuid, int elo, String ip) {
        this.uuid = uuid;
        this.elo = elo;
        this.ip = ip;
        this.joinedAt = System.currentTimeMillis();
    }

    public UUID uuid() {
        return uuid;
    }

    public int elo() {
        return elo;
    }

    /**
     * IP con la que entro en cola. Puede ser null si no se pudo leer.
     */
    public String ip() {
        return ip;
    }

    /**
     * True si comparte IP con la otra entrada. Si alguna IP es desconocida se
     * asume que no comparten, para no bloquear a gente por error.
     */
    public boolean sharesIpWith(QueueEntry other) {
        return ip != null && other.ip != null && ip.equalsIgnoreCase(other.ip);
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
