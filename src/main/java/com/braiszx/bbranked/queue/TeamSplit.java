package com.braiszx.bbranked.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reparto de los tickets de una partida en dos equipos.
 *
 * <p>Un ticket nunca se parte: los miembros de una party siempre caen juntos
 * en el mismo equipo.</p>
 */
public record TeamSplit(List<UUID> red, List<UUID> blue, int eloDifference) {

    /**
     * Busca el mejor reparto posible de {@code tickets} en dos equipos de
     * {@code teamSize} jugadores cada uno, minimizando la diferencia de Elo
     * total entre equipos.
     *
     * <p>Prueba todas las combinaciones de tickets. Son como mucho 6
     * jugadores y por tanto 6 tickets, asi que el coste es trivial.</p>
     *
     * @return el mejor reparto, o null si no hay ninguno valido (por ejemplo
     *         dos parties de 3 en un 2v2)
     */
    public static TeamSplit best(List<QueueTicket> tickets, int teamSize) {
        int count = tickets.size();
        if (count == 0 || count > 20) {
            return null;
        }

        TeamSplit best = null;

        for (int mask = 0; mask < (1 << count); mask++) {
            int redPlayers = 0;
            int redElo = 0;
            int blueElo = 0;

            for (int i = 0; i < count; i++) {
                QueueTicket ticket = tickets.get(i);
                if ((mask & (1 << i)) != 0) {
                    redPlayers += ticket.size();
                    redElo += ticket.totalElo();
                } else {
                    blueElo += ticket.totalElo();
                }
            }

            // Solo valen los repartos que llenan exactamente los dos equipos.
            if (redPlayers != teamSize) {
                continue;
            }

            int difference = Math.abs(redElo - blueElo);
            if (best != null && difference >= best.eloDifference()) {
                continue;
            }

            List<UUID> red = new ArrayList<>(teamSize);
            List<UUID> blue = new ArrayList<>(teamSize);
            for (int i = 0; i < count; i++) {
                QueueTicket ticket = tickets.get(i);
                ((mask & (1 << i)) != 0 ? red : blue).addAll(ticket.uuids());
            }

            if (blue.size() != teamSize) {
                continue;
            }
            best = new TeamSplit(red, blue, difference);
        }

        return best;
    }
}
