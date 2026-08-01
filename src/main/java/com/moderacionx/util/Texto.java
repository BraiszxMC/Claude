package com.moderacionx.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Conversion de texto a componentes. Acepta MiniMessage y, por comodidad,
 * los codigos clasicos con &amp; que se traducen a MiniMessage antes de parsear.
 */
public final class Texto {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Character, String> CODIGOS = new HashMap<>();

    static {
        CODIGOS.put('0', "<black>");
        CODIGOS.put('1', "<dark_blue>");
        CODIGOS.put('2', "<dark_green>");
        CODIGOS.put('3', "<dark_aqua>");
        CODIGOS.put('4', "<dark_red>");
        CODIGOS.put('5', "<dark_purple>");
        CODIGOS.put('6', "<gold>");
        CODIGOS.put('7', "<gray>");
        CODIGOS.put('8', "<dark_gray>");
        CODIGOS.put('9', "<blue>");
        CODIGOS.put('a', "<green>");
        CODIGOS.put('b', "<aqua>");
        CODIGOS.put('c', "<red>");
        CODIGOS.put('d', "<light_purple>");
        CODIGOS.put('e', "<yellow>");
        CODIGOS.put('f', "<white>");
        CODIGOS.put('k', "<obfuscated>");
        CODIGOS.put('l', "<bold>");
        CODIGOS.put('m', "<strikethrough>");
        CODIGOS.put('n', "<underlined>");
        CODIGOS.put('o', "<italic>");
        CODIGOS.put('r', "<reset>");
    }

    private Texto() {
    }

    /** Traduce los codigos &amp;c / §c a etiquetas MiniMessage. */
    public static String traducirCodigos(String entrada) {
        if (entrada == null || entrada.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(entrada.length());
        for (int i = 0; i < entrada.length(); i++) {
            char actual = entrada.charAt(i);
            if ((actual == '&' || actual == '§') && i + 1 < entrada.length()) {
                char codigo = Character.toLowerCase(entrada.charAt(i + 1));
                String etiqueta = CODIGOS.get(codigo);
                if (etiqueta != null) {
                    sb.append(etiqueta);
                    i++;
                    continue;
                }
            }
            sb.append(actual);
        }
        return sb.toString();
    }

    public static Component comp(String bruto) {
        return MM.deserialize(traducirCodigos(bruto == null ? "" : bruto));
    }

    public static Component comp(String bruto, Ph placeholders) {
        String texto = traducirCodigos(bruto == null ? "" : bruto);
        if (placeholders != null) {
            texto = placeholders.aplicar(texto);
        }
        return MM.deserialize(texto);
    }

    /** Componente sin cursiva heredada, util para nombres de items. */
    public static Component item(String bruto, Ph placeholders) {
        return comp(bruto, placeholders).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    public static String plano(Component componente) {
        return PlainTextComponentSerializer.plainText().serialize(componente);
    }

    /** Escapa un texto para que no se interprete como MiniMessage. */
    public static String escapar(String bruto) {
        return bruto == null ? "" : MM.escapeTags(bruto);
    }
}
