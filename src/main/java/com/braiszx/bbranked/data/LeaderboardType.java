package com.braiszx.bbranked.data;

import java.util.Locale;

/**
 * Las distintas tablas de clasificacion.
 *
 * <p>Cada una guarda la expresion SQL por la que ordena. Son valores fijos del
 * enum, nunca texto del usuario, asi que se pueden meter en la consulta sin
 * riesgo de inyeccion.</p>
 */
public enum LeaderboardType {

    ELO("elo", "elo", "Elo", false),
    PEAK("peak", "peak_elo", "Elo maximo", false),
    WINS("wins", "wins", "Victorias", false),
    LOSSES("losses", "losses", "Derrotas", false),
    DRAWS("draws", "draws", "Empates", false),
    GOALS("goals", "goals", "Goles", false),
    MVPS("mvps", "mvps", "MVPs", false),
    MATCHES("matches", "(wins + losses + draws)", "Partidas", false),
    STREAK("streak", "best_streak", "Mejor racha", false),
    WINRATE("winrate", "(wins * 100.0 / CASE WHEN (wins + losses + draws) = 0"
            + " THEN 1 ELSE (wins + losses + draws) END)", "Winrate", true),
    LEAVES("leaves", "leaves", "Abandonos", false);

    private final String id;
    private final String expression;
    private final String displayName;
    private final boolean decimal;

    LeaderboardType(String id, String expression, String displayName, boolean decimal) {
        this.id = id;
        this.expression = expression;
        this.displayName = displayName;
        this.decimal = decimal;
    }

    public String id() {
        return id;
    }

    /**
     * Expresion SQL por la que se ordena. Solo viene del enum.
     */
    public String expression() {
        return expression;
    }

    public String displayName() {
        return displayName;
    }

    public String format(double value) {
        return decimal
                ? String.format(Locale.ROOT, "%.1f%%", value)
                : String.valueOf((long) value);
    }

    /**
     * Busca el tipo por su id. Devuelve null si no existe.
     */
    public static LeaderboardType byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        for (LeaderboardType type : values()) {
            if (type.id.equals(key)) {
                return type;
            }
        }
        return null;
    }
}
