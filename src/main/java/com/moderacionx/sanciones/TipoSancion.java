package com.moderacionx.sanciones;

import java.util.Locale;

/** Tipos de sancion que registra ModeracionX. */
public enum TipoSancion {

    BAN("Baneo", true),
    BANIP("Baneo de IP", true),
    MUTE("Silencio", true),
    KICK("Expulsion", false),
    WARN("Advertencia", true);

    private final String nombre;
    private final boolean persistente;

    TipoSancion(String nombre, boolean persistente) {
        this.nombre = nombre;
        this.persistente = persistente;
    }

    public String nombre() {
        return nombre;
    }

    /** Un kick no se puede "retirar": ocurre y ya esta. */
    public boolean persistente() {
        return persistente;
    }

    public static TipoSancion desde(String valor) {
        try {
            return valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (Exception excepcion) {
            return null;
        }
    }
}
