package com.braiszx.bbranked.ban;

/**
 * Un baneo del ranked, ya sea de un jugador o de una IP.
 *
 * @param target    UUID del jugador (en texto) o la IP, segun {@code type}
 * @param label     nombre legible: el nick del jugador, o la propia IP
 * @param type      si el baneo es por jugador o por IP
 * @param reason    motivo mostrado al afectado
 * @param expiresAt momento (millis) en que caduca, o -1 si es permanente
 * @param bannedBy  quien lo puso
 * @param createdAt cuando se puso
 */
public record BanEntry(String target, String label, BanType type, String reason,
                       long expiresAt, String bannedBy, long createdAt) {

    public enum BanType {
        PLAYER,
        IP
    }

    public boolean permanent() {
        return expiresAt < 0;
    }

    public boolean expired() {
        return !permanent() && System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Texto del tiempo que queda, para los mensajes.
     */
    public String remainingText() {
        if (permanent()) {
            return "permanente";
        }
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) {
            return "caducado";
        }
        return BanManager.formatDuration(remaining);
    }
}
