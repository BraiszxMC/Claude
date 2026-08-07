package com.moderacionx.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reenvia lo que pasa en el servidor a Discord mediante webhooks.
 * <p>Solo va en un sentido (Minecraft -&gt; Discord), que es lo que permite un webhook.
 * Se pueden configurar varios webhooks, cada uno apuntando a un canal distinto y
 * escuchando los eventos que quieras.
 */
public final class GestorDiscord {

    private final ModeracionX plugin;
    private final HttpClient http;
    private List<EnlaceDiscord> webhooks = List.of();

    public GestorDiscord(ModeracionX plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        recargar();
    }

    public boolean activado() {
        return plugin.ajustes().raiz().getBoolean("discord.activado", false);
    }

    public int cantidad() {
        return webhooks.size();
    }

    public List<EnlaceDiscord> webhooks() {
        return webhooks;
    }

    public void recargar() {
        List<EnlaceDiscord> lista = new ArrayList<>();
        ConfigurationSection raiz = plugin.ajustes().raiz().getConfigurationSection("discord.webhooks");
        if (raiz != null) {
            for (String clave : raiz.getKeys(false)) {
                ConfigurationSection seccion = raiz.getConfigurationSection(clave);
                if (seccion == null) {
                    continue;
                }
                String url = seccion.getString("url", "");
                Set<String> eventos = new HashSet<>();
                for (String evento : seccion.getStringList("eventos")) {
                    eventos.add(evento.toLowerCase(Locale.ROOT));
                }
                EnlaceDiscord enlace = new EnlaceDiscord(clave, url,
                        seccion.getString("nombre", "Servidor"),
                        seccion.getString("avatar", ""),
                        eventos, seccion.getBoolean("embed", true),
                        color(seccion.getString("color", "#5865F2")));
                if (enlace.valido()) {
                    lista.add(enlace);
                } else if (!url.isBlank()) {
                    plugin.getLogger().warning("Webhook de Discord '" + clave + "' con URL invalida, se ignora.");
                }
            }
        }
        webhooks = List.copyOf(lista);
    }

    // ------------------------------------------------------------------ enviar

    /**
     * Manda un evento a todos los webhooks que lo escuchen.
     *
     * @param evento      nombre del evento (chat, entrada, salida, muerte, ban, kick...)
     * @param placeholders valores para el texto configurado
     */
    public void evento(String evento, Ph placeholders) {
        if (!activado() || webhooks.isEmpty()) {
            return;
        }
        String tipo = evento.toLowerCase(Locale.ROOT);
        String formato = plugin.ajustes().raiz().getString("discord.formatos." + tipo);
        if (formato == null || formato.isBlank()) {
            return;
        }
        String texto = placeholders == null ? formato : placeholders.aplicar(formato);
        String titulo = plugin.ajustes().raiz().getString("discord.titulos." + tipo, "");
        if (placeholders != null && !titulo.isBlank()) {
            titulo = placeholders.aplicar(titulo);
        }

        for (EnlaceDiscord webhook : webhooks) {
            if (webhook.escucha(tipo)) {
                mandar(webhook, titulo, texto);
            }
        }
    }

    private void mandar(EnlaceDiscord webhook, String titulo, String texto) {
        JsonObject cuerpo = new JsonObject();
        if (!webhook.nombre().isBlank()) {
            cuerpo.addProperty("username", webhook.nombre());
        }
        if (!webhook.avatar().isBlank()) {
            cuerpo.addProperty("avatar_url", webhook.avatar());
        }
        // no permitir que un jugador haga @everyone desde el chat del juego
        JsonObject sinMenciones = new JsonObject();
        sinMenciones.add("parse", new JsonArray());
        cuerpo.add("allowed_mentions", sinMenciones);

        if (webhook.embed()) {
            JsonObject embed = new JsonObject();
            embed.addProperty("description", recortar(texto, 4000));
            embed.addProperty("color", webhook.color());
            if (!titulo.isBlank()) {
                embed.addProperty("title", recortar(titulo, 250));
            }
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            cuerpo.add("embeds", embeds);
        } else {
            String contenido = titulo.isBlank() ? texto : "**" + titulo + "**\n" + texto;
            cuerpo.addProperty("content", recortar(contenido, 1900));
        }

        enviarJson(webhook, cuerpo.toString());
    }

    private void enviarJson(EnlaceDiscord webhook, String json) {
        Runnable tarea = () -> {
            try {
                HttpRequest peticion = HttpRequest.newBuilder()
                        .uri(URI.create(webhook.url()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "ModeracionX/" + plugin.getPluginMeta().getVersion())
                        .POST(HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
                if (respuesta.statusCode() >= 400) {
                    plugin.getLogger().warning("Webhook '" + webhook.clave() + "' devolvio "
                            + respuesta.statusCode() + ": " + recortar(respuesta.body(), 200));
                }
            } catch (Exception excepcion) {
                plugin.getLogger().warning("No se pudo enviar a Discord (" + webhook.clave() + "): "
                        + excepcion.getMessage());
            }
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, tarea);
        } else {
            tarea.run();
        }
    }

    // ------------------------------------------------------------------ utiles

    private int color(String hex) {
        try {
            String limpio = hex.startsWith("#") ? hex.substring(1) : hex;
            return Integer.parseInt(limpio, 16);
        } catch (Exception excepcion) {
            return 0x5865F2;
        }
    }

    private String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
