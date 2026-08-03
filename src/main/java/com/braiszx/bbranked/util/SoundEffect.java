package com.braiszx.bbranked.util;

import org.bukkit.entity.Player;

/**
 * Un sonido de Minecraft con su volumen y tono.
 *
 * <p>Se guarda el nombre como texto y se reproduce con la sobrecarga de
 * {@code playSound} que acepta String. Asi no dependemos del enum
 * {@code org.bukkit.Sound}, que cambia entre versiones de Minecraft y
 * romperia el plugin al actualizar.</p>
 */
public record SoundEffect(String sound, float volume, float pitch) {

    /**
     * Formato aceptado: {@code "nombre"}, {@code "nombre volumen"} o
     * {@code "nombre volumen tono"}.
     *
     * @return el sonido, o null si la linea esta vacia o desactivada
     */
    public static SoundEffect parse(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("none")) {
            return null;
        }

        String[] parts = input.trim().split("\\s+");
        String sound = parts[0].toLowerCase(java.util.Locale.ROOT);
        float volume = 1.0F;
        float pitch = 1.0F;

        if (parts.length >= 2) {
            volume = parseFloat(parts[1], 1.0F);
        }
        if (parts.length >= 3) {
            pitch = parseFloat(parts[2], 1.0F);
        }
        // Minecraft solo admite tonos entre 0.5 y 2.0.
        pitch = Math.max(0.5F, Math.min(2.0F, pitch));
        return new SoundEffect(sound, volume, pitch);
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public void play(Player player) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
