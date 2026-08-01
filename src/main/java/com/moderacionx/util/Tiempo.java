package com.moderacionx.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analiza y formatea duraciones del estilo {@code 7d}, {@code 12h30m}, {@code 1mo}, {@code permanente}.
 */
public final class Tiempo {

    /** Valor que representa "para siempre". */
    public static final long PERMANENTE = -1L;

    private static final Pattern PATRON = Pattern.compile("(?i)(\\d+)\\s*(mo|ms|s|m|h|d|w|y|seg|min|hor|dia|sem|mes|ano|year)");

    private static final Set<String> PALABRAS_PERMANENTE = Set.of(
            "perm", "permanente", "permanent", "forever", "siempre", "infinito", "nunca", "never", "0", "-1");

    private Tiempo() {
    }

    /** ¿El texto se puede interpretar como una duracion? */
    public static boolean esDuracion(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        try {
            parsear(texto);
            return true;
        } catch (IllegalArgumentException excepcion) {
            return false;
        }
    }

    /**
     * @return duracion en milisegundos, o {@link #PERMANENTE}
     * @throws IllegalArgumentException si el texto no es una duracion valida
     */
    public static long parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("vacio");
        }
        String limpio = texto.trim().toLowerCase(Locale.ROOT);
        if (PALABRAS_PERMANENTE.contains(limpio)) {
            return PERMANENTE;
        }
        Matcher matcher = PATRON.matcher(limpio);
        long total = 0L;
        int consumidos = 0;
        while (matcher.find()) {
            long cantidad;
            try {
                cantidad = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException excepcion) {
                throw new IllegalArgumentException(texto);
            }
            total += cantidad * milisPorUnidad(matcher.group(2));
            consumidos += matcher.group().length();
        }
        if (total <= 0 || consumidos != limpio.length()) {
            throw new IllegalArgumentException(texto);
        }
        return total;
    }

    private static long milisPorUnidad(String unidad) {
        return switch (unidad.toLowerCase(Locale.ROOT)) {
            case "ms" -> 1L;
            case "s", "seg" -> 1000L;
            case "m", "min" -> 60_000L;
            case "h", "hor" -> 3_600_000L;
            case "d", "dia" -> 86_400_000L;
            case "w", "sem" -> 604_800_000L;
            case "mo", "mes" -> 2_592_000_000L;
            case "y", "ano", "year" -> 31_536_000_000L;
            default -> throw new IllegalArgumentException(unidad);
        };
    }

    /** Formatea una duracion en milisegundos usando las palabras del diccionario. */
    public static String formatear(long milis, Diccionario diccionario) {
        if (milis == PERMANENTE) {
            return diccionario.palabra("general.permanente");
        }
        if (milis <= 0) {
            return "0 " + diccionario.palabra("tiempo.segundos");
        }

        long restante = milis / 1000L;
        long anos = restante / 31_536_000L;
        restante %= 31_536_000L;
        long meses = restante / 2_592_000L;
        restante %= 2_592_000L;
        long dias = restante / 86_400L;
        restante %= 86_400L;
        long horas = restante / 3_600L;
        restante %= 3_600L;
        long minutos = restante / 60L;
        long segundos = restante % 60L;

        List<String> partes = new ArrayList<>();
        anadir(partes, anos, "tiempo.ano", "tiempo.anos", diccionario);
        anadir(partes, meses, "tiempo.mes", "tiempo.meses", diccionario);
        anadir(partes, dias, "tiempo.dia", "tiempo.dias", diccionario);
        anadir(partes, horas, "tiempo.hora", "tiempo.horas", diccionario);
        anadir(partes, minutos, "tiempo.minuto", "tiempo.minutos", diccionario);
        if (partes.isEmpty()) {
            anadir(partes, segundos, "tiempo.segundo", "tiempo.segundos", diccionario);
        }
        if (partes.isEmpty()) {
            return "0 " + diccionario.palabra("tiempo.segundos");
        }
        return String.join(" ", partes.subList(0, Math.min(3, partes.size())));
    }

    private static void anadir(List<String> destino, long cantidad, String singular, String plural, Diccionario diccionario) {
        if (cantidad > 0) {
            destino.add(cantidad + " " + diccionario.palabra(cantidad == 1 ? singular : plural));
        }
    }

    /** Tiempo que falta hasta una fecha absoluta. */
    public static String restante(long expiraEn, Diccionario diccionario) {
        if (expiraEn == PERMANENTE) {
            return diccionario.palabra("general.permanente");
        }
        return formatear(Math.max(0L, expiraEn - System.currentTimeMillis()), diccionario);
    }

    /** Fecha absoluta legible. */
    public static String fecha(long epocaMilis, String formato, String zona) {
        if (epocaMilis == PERMANENTE) {
            return "-";
        }
        ZoneId zonaHoraria;
        try {
            zonaHoraria = ZoneId.of(zona);
        } catch (Exception excepcion) {
            zonaHoraria = ZoneId.systemDefault();
        }
        return DateTimeFormatter.ofPattern(formato)
                .withZone(zonaHoraria)
                .format(Instant.ofEpochMilli(epocaMilis));
    }
}
