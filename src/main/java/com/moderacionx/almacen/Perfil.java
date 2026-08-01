package com.moderacionx.almacen;

import java.util.UUID;

/** Datos recordados de un jugador para poder sancionarlo estando desconectado. */
public record Perfil(UUID uuid, String nombre, String ip, long primeraConexion, long ultimaConexion) {

    public static Perfil de(UUID uuid, String nombre, String ip) {
        long ahora = System.currentTimeMillis();
        return new Perfil(uuid, nombre, ip, ahora, ahora);
    }
}
