package com.braiszx.bbranked.elo;

import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.PlayerStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Elo por equipos.
 *
 * <p>Se calcula el Elo medio de cada equipo, se saca la probabilidad esperada
 * de victoria con la formula clasica de Elo y cada jugador se mueve segun su
 * propio factor K:</p>
 *
 * <pre>
 *   E_a   = 1 / (1 + 10^((R_b - R_a) / 400))
 *   delta = K * (S - E_a) * multiplicador_de_goles
 * </pre>
 *
 * <p>donde S vale 1 si gana, 0.5 si empata y 0 si pierde.</p>
 */
public final class EloCalculator {

    private final RankedConfig config;

    public EloCalculator(RankedConfig config) {
        this.config = config;
    }

    /**
     * Probabilidad de que el equipo con Elo {@code ratingA} gane al de {@code ratingB}.
     */
    public static double expectedScore(double ratingA, double ratingB) {
        return 1.0D / (1.0D + Math.pow(10.0D, (ratingB - ratingA) / 400.0D));
    }

    public static double averageElo(Collection<PlayerStats> team) {
        if (team.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (PlayerStats stats : team) {
            total += stats.elo();
        }
        return total / team.size();
    }

    /**
     * Calcula los cambios de Elo de una partida entera.
     *
     * @param red        estadisticas del equipo rojo
     * @param blue       estadisticas del equipo azul
     * @param redScore   goles del rojo
     * @param blueScore  goles del azul
     * @param winner     equipo ganador, o null si es empate
     * @param leavers    jugadores que abandonaron
     * @return la lista de cambios, ya aplicados a los objetos PlayerStats
     */
    public List<EloChange> apply(List<PlayerStats> red, List<PlayerStats> blue,
                                 int redScore, int blueScore, MatchWinner winner,
                                 Set<UUID> leavers) {
        double redAverage = averageElo(red);
        double blueAverage = averageElo(blue);
        double redExpected = expectedScore(redAverage, blueAverage);
        double blueExpected = 1.0D - redExpected;

        double redActual = switch (winner) {
            case RED -> 1.0D;
            case BLUE -> 0.0D;
            case DRAW -> 0.5D;
        };
        double blueActual = 1.0D - redActual;

        double multiplier = goalMultiplier(Math.abs(redScore - blueScore), winner);
        boolean draw = winner == MatchWinner.DRAW;

        List<EloChange> changes = new ArrayList<>(red.size() + blue.size());
        changes.addAll(applyTeam(red, redActual, redExpected, multiplier, leavers, draw));
        changes.addAll(applyTeam(blue, blueActual, blueExpected, multiplier, leavers, draw));
        return changes;
    }

    private List<EloChange> applyTeam(List<PlayerStats> team, double actual, double expected,
                                      double multiplier, Set<UUID> leavers, boolean draw) {
        boolean teamHasLeaver = team.stream().anyMatch(stats -> leavers.contains(stats.uuid()));
        List<EloChange> changes = new ArrayList<>(team.size());

        for (PlayerStats stats : team) {
            boolean leaver = leavers.contains(stats.uuid());
            int before = stats.elo();
            int outcome = actual > 0.5D ? 1 : (actual < 0.5D ? -1 : 0);

            int rounded;
            if (leaver) {
                // El que abandona siempre computa derrota y ademas se lleva
                // la penalizacion extra.
                outcome = -1;
                double delta = kFactor(stats) * (0.0D - expected) - config.leaverExtraPenalty();
                rounded = roundDelta(delta, outcome, true);
            } else if (teamHasLeaver && outcome < 0 && config.protectTeammates()) {
                // Sus companeros cuentan la derrota en el historial pero no
                // pagan el Elo de una partida que no pudieron jugar.
                rounded = 0;
            } else if (draw) {
                // El empate cuenta como empate (outcome 0), pero cuesta una
                // parte de lo que habria costado perder. Asi empatar duele
                // menos que perder, en vez de no significar nada.
                double lossDelta = kFactor(stats) * (0.0D - expected);
                double delta = lossDelta * (config.drawLossPercent() / 100.0D);
                // Sin minimos forzados: un empate puede costar 0 Elo.
                rounded = (int) (delta >= 0 ? Math.floor(delta + 0.5D) : Math.ceil(delta - 0.5D));
            } else {
                double delta = kFactor(stats) * (actual - expected) * multiplier;
                rounded = roundDelta(delta, outcome, false);
            }

            int after = Math.max(config.minimumElo(), before + rounded);

            stats.elo(after);
            if (leaver) {
                stats.leaves(stats.leaves() + 1);
            }
            stats.recordResult(outcome);

            changes.add(new EloChange(stats.uuid(), before, after, outcome, leaver));
        }
        return changes;
    }

    /**
     * Suma el Elo extra del MVP encima de lo que ya haya ganado o perdido.
     * Devuelve el cambio actualizado para que el mensaje final cuadre.
     */
    public EloChange applyMvpBonus(PlayerStats stats, EloChange change, int bonus) {
        int after = Math.max(config.minimumElo(), change.after() + bonus);
        stats.elo(after);
        return new EloChange(change.uuid(), change.before(), after, change.outcome(), change.leaver());
    }

    /**
     * Redondea alejandose del cero y respeta las ganancias/perdidas minimas.
     */
    private int roundDelta(double delta, int outcome, boolean leaver) {
        int rounded = (int) (delta >= 0 ? Math.floor(delta + 0.5D) : Math.ceil(delta - 0.5D));

        if (outcome > 0 && rounded < config.minGain()) {
            rounded = config.minGain();
        } else if (outcome < 0 && rounded > -config.minLoss()) {
            rounded = -config.minLoss();
        }
        if (leaver && rounded > -config.minLoss()) {
            rounded = -config.minLoss();
        }
        return rounded;
    }

    /**
     * Factor K: alto en colocacion, bajo en la cima del ranking.
     */
    public int kFactor(PlayerStats stats) {
        if (stats.matches() < config.placementMatches()) {
            return config.placementK();
        }
        if (stats.elo() >= config.highEloThreshold()) {
            return config.highK();
        }
        return config.baseK();
    }

    /**
     * Multiplicador por diferencia de goles. Solo se aplica si hay ganador.
     */
    private double goalMultiplier(int goalDifference, MatchWinner winner) {
        if (!config.goalDifferenceEnabled() || winner == MatchWinner.DRAW || goalDifference <= 0) {
            return 1.0D;
        }
        double multiplier = 1.0D + config.goalDifferenceFactor() * Math.log(1.0D + goalDifference);
        return Math.min(multiplier, config.goalDifferenceMaxMultiplier());
    }
}
