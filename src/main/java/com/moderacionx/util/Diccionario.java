package com.moderacionx.util;

/** Fuente de palabras sueltas (singular/plural de las unidades de tiempo). */
@FunctionalInterface
public interface Diccionario {

    String palabra(String clave);
}
