package com.braiszx.bbranked.data;

import java.util.UUID;

/**
 * Una fila de cualquier tabla de clasificacion.
 *
 * @param uuid  jugador
 * @param name  nombre
 * @param value valor por el que se ordena (Elo, goles, MVPs...)
 */
public record TopEntry(UUID uuid, String name, double value) {

    /**
     * El valor formateado segun el tipo de tabla.
     */
    public String display(LeaderboardType type) {
        return type.format(value);
    }
}
