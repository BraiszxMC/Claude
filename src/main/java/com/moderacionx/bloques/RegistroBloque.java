package com.moderacionx.bloques;

import java.util.UUID;

/**
 * Un cambio de bloque registrado.
 *
 * @param id       id en la base de datos
 * @param uuid     quien lo hizo (null si fue algo del entorno)
 * @param nombre   nombre de quien lo hizo
 * @param accion   0 = rotura, 1 = colocacion
 * @param material material del bloque (el que habia al romper, o el que se puso)
 * @param mundo    nombre del mundo
 * @param x        coordenada x del bloque
 * @param y        coordenada y del bloque
 * @param z        coordenada z del bloque
 * @param fecha    momento en milisegundos
 */
public record RegistroBloque(long id, UUID uuid, String nombre, int accion, String material,
                             String mundo, int x, int y, int z, long fecha) {

    public static final int ROTURA = 0;
    public static final int COLOCACION = 1;

    public static RegistroBloque nuevo(UUID uuid, String nombre, int accion, String material,
                                       String mundo, int x, int y, int z) {
        return new RegistroBloque(-1L, uuid, nombre, accion, material, mundo, x, y, z,
                System.currentTimeMillis());
    }

    public boolean rotura() {
        return accion == ROTURA;
    }
}
