package com.braiszx.bbranked.season;

/**
 * Una temporada del ranking.
 *
 * @param id        identificador en la base de datos
 * @param name      nombre visible
 * @param startedAt cuando empezo
 * @param endedAt   cuando acabo, o 0 si sigue abierta
 */
public record Season(int id, String name, long startedAt, long endedAt) {

    public boolean active() {
        return endedAt <= 0;
    }

    /**
     * Dias que lleva abierta.
     */
    public long daysRunning() {
        long end = active() ? System.currentTimeMillis() : endedAt;
        return (end - startedAt) / 86_400_000L;
    }
}
