package com.moderacionx.espia;

import java.util.UUID;

/**
 * Un comando ejecutado y guardado para poder buscarlo despues con /spyX.
 *
 * @param uuid    quien lo ejecuto, o null si fue la consola
 * @param nombre  nombre de quien lo ejecuto
 * @param comando comando raiz en minusculas y sin barra (ej: "attribute")
 * @param linea   la linea completa tal cual se escribio
 * @param mundo   mundo donde estaba, o null
 * @param fecha   momento en milisegundos
 */
public record RegistroComando(long id, UUID uuid, String nombre, String comando,
                              String linea, String mundo, long fecha) {

    public static RegistroComando nuevo(UUID uuid, String nombre, String comando, String linea, String mundo) {
        return new RegistroComando(-1L, uuid, nombre, comando, linea, mundo, System.currentTimeMillis());
    }
}
