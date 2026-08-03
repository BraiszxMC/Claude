package com.braiszx.bbranked.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sonidos configurables asociados a los eventos del ranked.
 */
public final class Sounds {

    private final Plugin plugin;
    private final Map<String, SoundEffect> effects = new HashMap<>();
    private boolean enabled;

    public Sounds(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        effects.clear();
        this.enabled = plugin.getConfig().getBoolean("sounds.enabled", true);

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sounds");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (key.equals("enabled")) {
                continue;
            }
            SoundEffect effect = SoundEffect.parse(section.getString(key));
            if (effect != null) {
                effects.put(key, effect);
            }
        }
    }

    public void play(Player player, String key) {
        if (!enabled || player == null) {
            return;
        }
        SoundEffect effect = effects.get(key);
        if (effect != null) {
            effect.play(player);
        }
    }

    /**
     * Reproduce el sonido a varios jugadores a la vez.
     */
    public void play(Iterable<UUID> uuids, String key) {
        if (!enabled) {
            return;
        }
        SoundEffect effect = effects.get(key);
        if (effect == null) {
            return;
        }
        for (UUID uuid : uuids) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                effect.play(player);
            }
        }
    }
}
