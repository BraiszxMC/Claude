package com.moderacionx.config;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Diccionario;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Carga y entrega los textos de mensajes.yml. */
public final class Mensajes implements Diccionario {

    private final ModeracionX plugin;
    private FileConfiguration config;

    public Mensajes(ModeracionX plugin) {
        this.plugin = plugin;
        recargar();
    }

    public void recargar() {
        File archivo = new File(plugin.getDataFolder(), "mensajes.yml");
        if (!archivo.exists()) {
            plugin.saveResource("mensajes.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(archivo);
        try (InputStream recurso = plugin.getResource("mensajes.yml")) {
            if (recurso != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(recurso, StandardCharsets.UTF_8)));
            }
        } catch (Exception excepcion) {
            plugin.getLogger().warning("No se pudieron cargar los valores por defecto de mensajes.yml: " + excepcion.getMessage());
        }
    }

    public FileConfiguration raiz() {
        return config;
    }

    /** Texto en bruto, con %prefijo% ya sustituido. */
    public String bruto(String ruta) {
        String valor = config.getString(ruta);
        if (valor == null) {
            return "<red>[falta el mensaje: " + ruta + "]";
        }
        return valor.replace("%prefijo%", plugin.ajustes().prefijo());
    }

    /** Lista en bruto (o lista de un solo elemento si la ruta es un texto). */
    public List<String> brutoLista(String ruta) {
        List<String> lista = config.getStringList(ruta);
        if (lista.isEmpty()) {
            String unico = config.getString(ruta);
            if (unico != null) {
                lista = List.of(unico);
            }
        }
        List<String> resultado = new ArrayList<>(lista.size());
        for (String linea : lista) {
            resultado.add(linea.replace("%prefijo%", plugin.ajustes().prefijo()));
        }
        return resultado;
    }

    @Override
    public String palabra(String clave) {
        String valor = config.getString(clave);
        return valor == null ? clave : valor;
    }

    public Component comp(String ruta) {
        return comp(ruta, null);
    }

    public Component comp(String ruta, Ph placeholders) {
        return Texto.comp(bruto(ruta), placeholders);
    }

    public List<Component> lista(String ruta, Ph placeholders) {
        List<Component> componentes = new ArrayList<>();
        for (String linea : brutoLista(ruta)) {
            componentes.add(Texto.comp(linea, placeholders));
        }
        return componentes;
    }

    /** Une una lista de mensajes en un unico componente con saltos de linea (pantallas de kick). */
    public Component bloque(String ruta, Ph placeholders) {
        return Component.join(JoinConfiguration.newlines(), lista(ruta, placeholders));
    }

    public void enviar(CommandSender destino, String ruta) {
        enviar(destino, ruta, null);
    }

    public void enviar(CommandSender destino, String ruta, Ph placeholders) {
        Object valor = config.get(ruta);
        if (valor instanceof String texto) {
            if (texto.isBlank()) {
                return;
            }
            destino.sendMessage(Texto.comp(texto.replace("%prefijo%", plugin.ajustes().prefijo()), placeholders));
            return;
        }
        List<String> lineas = brutoLista(ruta);
        if (lineas.isEmpty()) {
            destino.sendMessage(Texto.comp("<red>[falta el mensaje: " + ruta + "]"));
            return;
        }
        for (String linea : lineas) {
            destino.sendMessage(Texto.comp(linea, placeholders));
        }
    }
}
