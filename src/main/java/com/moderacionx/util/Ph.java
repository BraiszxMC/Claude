package com.moderacionx.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bolsa de placeholders del tipo %clave%.
 * <p>{@link #con(String, Object)} escapa el valor para que un jugador no pueda
 * inyectar etiquetas MiniMessage a traves de una razon o un nombre.
 * {@link #crudo(String, String)} lo inserta tal cual (solo para textos de confianza).
 */
public final class Ph {

    private final Map<String, String> valores = new LinkedHashMap<>();

    private Ph() {
    }

    public static Ph de() {
        return new Ph();
    }

    public static Ph de(String clave, Object valor) {
        return new Ph().con(clave, valor);
    }

    public Ph con(String clave, Object valor) {
        valores.put(clave, Texto.escapar(valor == null ? "" : String.valueOf(valor)));
        return this;
    }

    public Ph crudo(String clave, String valor) {
        valores.put(clave, valor == null ? "" : valor);
        return this;
    }

    public Ph unir(Ph otro) {
        if (otro != null) {
            valores.putAll(otro.valores);
        }
        return this;
    }

    public String aplicar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String resultado = texto;
        for (Map.Entry<String, String> entrada : valores.entrySet()) {
            resultado = resultado.replace("%" + entrada.getKey() + "%", entrada.getValue());
        }
        return resultado;
    }
}
