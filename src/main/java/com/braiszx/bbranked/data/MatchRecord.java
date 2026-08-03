package com.braiszx.bbranked.data;

import java.util.Map;
import java.util.UUID;

/**
 * Una partida guardada en el historial.
 *
 * @param mode       modo en el que se jugo
 * @param arena      arena usada
 * @param redScore   goles del rojo
 * @param blueScore  goles del azul
 * @param winner     RED, BLUE o DRAW
 * @param redTeam    uuids del equipo rojo
 * @param blueTeam   uuids del equipo azul
 * @param eloChanges uuid -> Elo ganado o perdido
 * @param playedAt   cuando se jugo
 */
public record MatchRecord(String mode, String arena, int redScore, int blueScore, String winner,
                          java.util.List<UUID> redTeam, java.util.List<UUID> blueTeam,
                          Map<UUID, Integer> eloChanges, long playedAt) {

    /**
     * Resultado desde el punto de vista de un jugador: 1 gano, 0 empate,
     * -1 perdio.
     */
    public int outcomeFor(UUID uuid) {
        if ("DRAW".equals(winner)) {
            return 0;
        }
        boolean inRed = redTeam.contains(uuid);
        boolean redWon = "RED".equals(winner);
        return inRed == redWon ? 1 : -1;
    }

    public int eloChangeFor(UUID uuid) {
        return eloChanges.getOrDefault(uuid, 0);
    }
}
