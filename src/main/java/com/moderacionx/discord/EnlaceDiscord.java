package com.moderacionx.discord;

import java.util.Set;

/**
 * Un webhook de Discord configurado: a donde manda, con que aspecto y que eventos escucha.
 *
 * @param clave   nombre de la seccion en config.yml (para los logs)
 * @param url     URL del webhook de Discord
 * @param nombre  nombre que aparece como autor del mensaje
 * @param avatar  URL del avatar, o vacio
 * @param eventos tipos de evento que este webhook reenvia
 * @param embed   true = mensajes con embed; false = texto plano
 * @param color   color de la barra del embed (entero RGB)
 */
public record EnlaceDiscord(String clave, String url, String nombre, String avatar,
                            Set<String> eventos, boolean embed, int color) {

    public boolean escucha(String evento) {
        return eventos.contains(evento) || eventos.contains("*") || eventos.contains("todo");
    }

    public boolean valido() {
        return url != null && url.startsWith("https://") && url.contains("discord");
    }
}
