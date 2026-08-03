package com.braiszx.bbranked.hook;

import com.braiszx.bbranked.config.RankedConfig;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;

/**
 * Envia avisos a un canal de Discord con un webhook.
 *
 * <p>Todo va de forma asincrona: una peticion HTTP nunca puede bloquear el
 * hilo del servidor.</p>
 */
public final class DiscordWebhook {

    /** Colores de los embeds. */
    public static final int COLOR_GREEN = 0x2ECC71;
    public static final int COLOR_RED = 0xE74C3C;
    public static final int COLOR_GOLD = 0xF1C40F;
    public static final int COLOR_BLUE = 0x3498DB;

    private final Plugin plugin;
    private final RankedConfig config;
    private final HttpClient client;

    public DiscordWebhook(Plugin plugin, RankedConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Manda un embed simple.
     */
    public void send(String title, String description, int color) {
        if (!config.discordEnabled()) {
            return;
        }

        String payload = "{"
                + "\"username\":\"" + escape(config.discordUsername()) + "\","
                + "\"embeds\":[{"
                + "\"title\":\"" + escape(title) + "\","
                + "\"description\":\"" + escape(description) + "\","
                + "\"color\":" + color
                + "}]}";

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(config.discordWebhookUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("La URL del webhook de Discord no es valida.");
            return;
        }

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "No se ha podido avisar a Discord: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * Escapa el texto para meterlo en JSON a mano. Se hace asi para no
     * arrastrar una libreria entera solo por esto.
     */
    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
