package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /help - menu de ayuda propio, filtrado por permisos y editable en config.yml. */
public final class ComandoAyuda extends ComandoBase {

    public ComandoAyuda(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!plugin.ajustes().ayudaSustituirVanilla() && "help".equals(etiqueta)) {
            emisor.getServer().dispatchCommand(emisor, "minecraft:help " + unir(argumentos, 0));
            return;
        }

        int pagina = 1;
        if (argumentos.length > 0) {
            try {
                pagina = Math.max(1, Integer.parseInt(argumentos[0]));
            } catch (NumberFormatException excepcion) {
                plugin.mensajes().enviar(emisor, "general.numero-invalido", Ph.de("valor", argumentos[0]));
                return;
            }
        }

        List<Map<?, ?>> entradas = plugin.ajustes().raiz().getMapList("ayuda.entradas");
        List<net.kyori.adventure.text.Component> lineas = new ArrayList<>();
        String formato = plugin.ajustes().raiz().getString("ayuda.formato-linea",
                "<white>%comando% <dark_gray>- <gray>%descripcion%");

        for (Map<?, ?> entrada : entradas) {
            Object permiso = entrada.get("permiso");
            if (permiso instanceof String texto && !texto.isBlank() && !emisor.hasPermission(texto)) {
                continue;
            }
            Object comando = entrada.get("comando");
            Object descripcion = entrada.get("descripcion");
            lineas.add(Texto.comp(formato, Ph.de()
                    .con("comando", comando == null ? "" : comando)
                    .con("descripcion", descripcion == null ? "" : descripcion)));
        }

        if (lineas.isEmpty()) {
            plugin.mensajes().enviar(emisor, "general.sin-permiso");
            return;
        }

        int porPagina = plugin.ajustes().ayudaLineasPorPagina();
        int paginas = (int) Math.ceil(lineas.size() / (double) porPagina);
        int actual = Math.min(pagina, paginas);
        int desde = (actual - 1) * porPagina;
        int hasta = Math.min(lineas.size(), desde + porPagina);

        emisor.sendMessage(Texto.comp(plugin.ajustes().raiz().getString("ayuda.cabecera", ""), Ph.de()
                .con("pagina", actual)
                .con("paginas", paginas)));
        for (int i = desde; i < hasta; i++) {
            emisor.sendMessage(lineas.get(i));
        }
        if (actual < paginas) {
            emisor.sendMessage(Texto.comp(plugin.ajustes().raiz().getString("ayuda.pie", ""), null));
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return List.of();
    }
}
