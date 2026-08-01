package com.braiszx.bbranked.config;

/**
 * Una division del ranking (Hierro, Bronce, ...).
 *
 * @param id          identificador interno, se usa en placeholders
 * @param displayName nombre en formato MiniMessage
 * @param minElo      Elo minimo para pertenecer a esta division
 */
public record RankTier(String id, String displayName, int minElo) {
}
