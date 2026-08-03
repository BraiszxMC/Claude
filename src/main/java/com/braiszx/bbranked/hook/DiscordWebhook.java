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
        send(new DiscordEmbed().title(title).description(description).color(color).withTimestamp());
    }

    /**
     * Manda un embed ya construido.
     */
    public void send(DiscordEmbed embed) {
        if (!config.discordEnabled()) {
            return;
        }

        String payload = "{"
                + "\"username\":\"" + escape(config.discordUsername()) + "\","
                + "\"embeds\":[" + embed.toJson() + "]}";

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

    private static String escape(String input) {
        return DiscordEmbed.escape(input);
    }
}
