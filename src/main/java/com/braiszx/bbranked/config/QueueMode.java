package com.braiszx.bbranked.config;

import java.util.List;

/**
 * Un modo de juego ranked (1v1, 2v2, ...).
 *
 * @param id          clave del modo en el config, tambien lo que se escribe en /ranked join
 * @param displayName nombre bonito para los mensajes
 * @param teamSize    jugadores por equipo
 * @param arenas      arenas de BlockBall reservadas para este modo
 */
public record QueueMode(String id, String displayName, int teamSize, List<String> arenas) {

    /**
     * Jugadores totales necesarios para arrancar una partida.
     */
    public int requiredPlayers() {
        return teamSize * 2;
    }
}
