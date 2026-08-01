package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/** /anuncio - mensaje para todo el servidor, con formato, titulo y sonido configurables. */
public final class ComandoAnuncio extends ComandoBase {

    public ComandoAnuncio(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.anuncio")) {
            return;
        }
        if (argumentos.length == 0) {
            uso(emisor, "/anuncio <mensaje>");
            return;
        }

        String mensaje = unir(argumentos, 0);
        Ph placeholders = emisor.hasPermission("moderacionx.anuncio.formato")
                ? Ph.de().crudo("mensaje", Texto.traducirCodigos(mensaje)).con("autor", emisor.getName())
                : Ph.de().con("mensaje", mensaje).con("autor", emisor.getName());

        var configuracion = plugin.ajustes().raiz();
        List<Component> lineas = new java.util.ArrayList<>();
        for (String linea : configuracion.getStringList("anuncio.formato")) {
            lineas.add(Texto.comp(linea, placeholders));
        }
        if (lineas.isEmpty()) {
            lineas.add(Texto.comp("<white>%mensaje%", placeholders));
        }

        for (Player jugador : Bukkit.getOnlinePlayers()) {
            for (Component linea : lineas) {
                jugador.sendMessage(linea);
            }
            if (configuracion.getBoolean("anuncio.titulo.activado", false)) {
                jugador.showTitle(Title.title(
                        Texto.comp(configuracion.getString("anuncio.titulo.titulo", ""), placeholders),
                        Texto.comp(configuracion.getString("anuncio.titulo.subtitulo", ""), placeholders),
                        Title.Times.times(
                                ticks(configuracion.getInt("anuncio.titulo.aparecer-ticks", 10)),
                                ticks(configuracion.getInt("anuncio.titulo.quedarse-ticks", 60)),
                                ticks(configuracion.getInt("anuncio.titulo.desaparecer-ticks", 10)))));
            }
            if (configuracion.getBoolean("anuncio.sonido.activado", true)) {
                String clave = configuracion.getString("anuncio.sonido.clave", "minecraft:block.note_block.pling");
                try {
                    jugador.playSound(Sound.sound(Key.key(clave), Sound.Source.MASTER,
                            (float) configuracion.getDouble("anuncio.sonido.volumen", 1.0D),
                            (float) configuracion.getDouble("anuncio.sonido.tono", 1.0D)));
                } catch (Exception excepcion) {
                    plugin.getLogger().warning("Sonido invalido en anuncio.sonido.clave: " + clave);
                }
            }
        }

        if (configuracion.getBoolean("anuncio.a-consola", true)) {
            for (Component linea : lineas) {
                Bukkit.getConsoleSender().sendMessage(linea);
            }
        }
        plugin.mensajes().enviar(emisor, "anuncio.enviado");
    }

    private Duration ticks(int cantidad) {
        return Duration.ofMillis(Math.max(0L, cantidad) * 50L);
    }
}
