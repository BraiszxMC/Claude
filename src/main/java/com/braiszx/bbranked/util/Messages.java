package com.braiszx.bbranked.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carga messages.yml y lo convierte a componentes de Adventure con MiniMessage.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;
    private FileConfiguration config;
    private String prefix = "";

    public Messages(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);

        // Rellena las claves que falten con las del jar, para que actualizar el
        // plugin no rompa los messages.yml antiguos.
        InputStream defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            this.config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        this.prefix = config.getString("prefix", "");
    }

    /**
     * Devuelve el mensaje de {@code path} como componente, sin prefijo.
     */
    public Component raw(String path, Map<String, String> placeholders) {
        String value = config.getString(path);
        if (value == null) {
            return Component.text("<falta el mensaje: " + path + ">");
        }
        return MINI.deserialize(apply(value, placeholders));
    }

    public Component raw(String path) {
        return raw(path, Map.of());
    }

    /**
     * Devuelve el mensaje de {@code path} con el prefijo delante.
     */
    public Component get(String path, Map<String, String> placeholders) {
        String value = config.getString(path);
        if (value == null) {
            return Component.text("<falta el mensaje: " + path + ">");
        }
        return MINI.deserialize(prefix + apply(value, placeholders));
    }

    public Component get(String path) {
        return get(path, Map.of());
    }

    public List<Component> list(String path, Map<String, String> placeholders) {
        List<Component> out = new ArrayList<>();
        for (String line : config.getStringList(path)) {
            out.add(MINI.deserialize(apply(line, placeholders)));
        }
        return out;
    }

    public void send(CommandSender to, String path, Map<String, String> placeholders) {
        to.sendMessage(get(path, placeholders));
    }

    public void send(CommandSender to, String path) {
        send(to, path, Map.of());
    }

    /**
     * Envia el mensaje sin prefijo (para cabeceras y listados).
     */
    public void sendRaw(CommandSender to, String path, Map<String, String> placeholders) {
        to.sendMessage(raw(path, placeholders));
    }

    public void sendRaw(CommandSender to, String path) {
        sendRaw(to, path, Map.of());
    }

    public void sendList(CommandSender to, String path) {
        for (Component line : list(path, Map.of())) {
            to.sendMessage(line);
        }
    }

    /**
     * Convierte un texto suelto de MiniMessage (por ejemplo el nombre de una
     * division sacado del config) a componente.
     */
    public static Component mini(String miniMessage) {
        return MINI.deserialize(miniMessage);
    }

    private static String apply(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
