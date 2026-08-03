package com.braiszx.bbranked.hook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Constructor de embeds de Discord.
 *
 * <p>Genera el JSON a mano en vez de arrastrar una libreria entera solo para
 * un punado de campos.</p>
 */
public final class DiscordEmbed {

    /** Un campo del embed. */
    private record Field(String name, String value, boolean inline) {
    }

    private String title;
    private String description;
    private int color = 0x5865F2;
    private String thumbnailUrl;
    private String footer;
    private String authorName;
    private boolean timestamp;
    private final List<Field> fields = new ArrayList<>();

    public DiscordEmbed title(String title) {
        this.title = title;
        return this;
    }

    public DiscordEmbed description(String description) {
        this.description = description;
        return this;
    }

    public DiscordEmbed color(int color) {
        this.color = color;
        return this;
    }

    /**
     * Cabeza del jugador como miniatura. Usa mc-heads.net, que Discord se
     * encarga de descargar: el servidor no hace ninguna peticion.
     */
    public DiscordEmbed playerHead(String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            this.thumbnailUrl = "https://mc-heads.net/avatar/" + playerName.replace(" ", "") + "/128";
        }
        return this;
    }

    public DiscordEmbed thumbnail(String url) {
        this.thumbnailUrl = url;
        return this;
    }

    public DiscordEmbed footer(String footer) {
        this.footer = footer;
        return this;
    }

    public DiscordEmbed author(String authorName) {
        this.authorName = authorName;
        return this;
    }

    public DiscordEmbed withTimestamp() {
        this.timestamp = true;
        return this;
    }

    public DiscordEmbed field(String name, String value, boolean inline) {
        if (name != null && value != null && !value.isBlank()) {
            fields.add(new Field(name, value, inline));
        }
        return this;
    }

    /**
     * Serializa el embed. El objeto de fuera (username, array de embeds) lo
     * pone {@link DiscordWebhook}.
     */
    public String toJson() {
        StringBuilder json = new StringBuilder("{");
        json.append("\"color\":").append(color);

        if (title != null) {
            json.append(",\"title\":\"").append(escape(title)).append('"');
        }
        if (description != null && !description.isBlank()) {
            json.append(",\"description\":\"").append(escape(description)).append('"');
        }
        if (authorName != null) {
            json.append(",\"author\":{\"name\":\"").append(escape(authorName)).append("\"}");
        }
        if (thumbnailUrl != null) {
            json.append(",\"thumbnail\":{\"url\":\"").append(escape(thumbnailUrl)).append("\"}");
        }
        if (footer != null) {
            json.append(",\"footer\":{\"text\":\"").append(escape(footer)).append("\"}");
        }
        if (timestamp) {
            json.append(",\"timestamp\":\"").append(Instant.now().toString()).append('"');
        }

        if (!fields.isEmpty()) {
            json.append(",\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                if (i > 0) {
                    json.append(',');
                }
                json.append("{\"name\":\"").append(escape(field.name()))
                        .append("\",\"value\":\"").append(escape(field.value()))
                        .append("\",\"inline\":").append(field.inline()).append('}');
            }
            json.append(']');
        }

        return json.append('}').toString();
    }

    /**
     * Escapa una cadena para JSON.
     */
    static String escape(String input) {
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
