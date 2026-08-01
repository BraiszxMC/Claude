package com.braiszx.bbranked.elo;

import java.util.UUID;

/**
 * Cambio de Elo de un jugador en una partida.
 *
 * @param uuid    jugador
 * @param before  Elo antes de la partida
 * @param after   Elo despues de la partida
 * @param outcome 1 victoria, 0 empate, -1 derrota
 * @param leaver  true si abandono la partida
 */
public record EloChange(UUID uuid, int before, int after, int outcome, boolean leaver) {

    public int delta() {
        return after - before;
    }
}
