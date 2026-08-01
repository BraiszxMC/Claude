package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;
import com.moderacionx.util.Ph;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** /historial - registro completo de sanciones de un jugador. */
public final class ComandoHistorial extends ComandoBase {

    public ComandoHistorial(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        String nombre;
        if (argumentos.length == 0) {
            Player jugador = comoJugador(emisor);
            if (jugador == null) {
                return;
            }
            if (!puede(emisor, "moderacionx.historial.propio")) {
                return;
            }
            nombre = jugador.getName();
        } else {
            if (!puede(emisor, "moderacionx.historial")) {
                return;
            }
            nombre = argumentos[0];
        }

        int pagina = 1;
        if (argumentos.length >= 2) {
            try {
                pagina = Math.max(1, Integer.parseInt(argumentos[1]));
            } catch (NumberFormatException excepcion) {
                plugin.mensajes().enviar(emisor, "general.numero-invalido", Ph.de("valor", argumentos[1]));
                return;
            }
        }

        final int paginaFinal = pagina;
        final String buscado = nombre;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<Perfil> encontrado = plugin.sanciones().resolver(buscado);
            if (encontrado.isEmpty()) {
                plugin.mensajes().enviar(emisor, "general.jugador-no-encontrado", Ph.de("jugador", buscado));
                return;
            }
            Perfil perfil = encontrado.get();
            List<Sancion> historial = plugin.sanciones().historial(perfil);
            if (!plugin.ajustes().raiz().getBoolean("historial.mostrar-inactivas", true)) {
                historial = new ArrayList<>(historial.stream().filter(Sancion::vigente).toList());
            }
            List<Sancion> lista = historial;
            Bukkit.getScheduler().runTask(plugin, () -> mostrar(emisor, perfil, lista, paginaFinal));
        });
    }

    private void mostrar(CommandSender emisor, Perfil perfil, List<Sancion> historial, int pagina) {
        if (historial.isEmpty()) {
            plugin.mensajes().enviar(emisor, "historial.vacio", Ph.de("jugador", perfil.nombre()));
            return;
        }

        int porPagina = Math.max(1, plugin.ajustes().raiz().getInt("historial.lineas-por-pagina", 8));
        int paginas = (int) Math.ceil(historial.size() / (double) porPagina);
        int actual = Math.min(pagina, paginas);
        int desde = (actual - 1) * porPagina;
        int hasta = Math.min(historial.size(), desde + porPagina);

        emisor.sendMessage(plugin.mensajes().comp("historial.cabecera", Ph.de()
                .con("jugador", perfil.nombre())
                .con("pagina", actual)
                .con("paginas", paginas)));

        emisor.sendMessage(plugin.mensajes().comp("historial.resumen", Ph.de()
                .con("baneos", contar(historial, TipoSancion.BAN) + contar(historial, TipoSancion.BANIP))
                .con("mutes", contar(historial, TipoSancion.MUTE))
                .con("kicks", contar(historial, TipoSancion.KICK))
                .con("warns", contar(historial, TipoSancion.WARN))));

        for (int i = desde; i < hasta; i++) {
            Sancion sancion = historial.get(i);
            Ph datos = plugin.sanciones().datos(sancion).crudo("estado",
                    plugin.mensajes().bruto(sancion.vigente() ? "historial.estado-activa" : "historial.estado-inactiva"));
            Component linea = plugin.mensajes().comp("historial.linea", datos)
                    .hoverEvent(HoverEvent.showText(plugin.mensajes().bloque("historial.linea-hover", datos)));
            emisor.sendMessage(linea);
        }

        if (actual < paginas) {
            emisor.sendMessage(plugin.mensajes().comp("historial.pie", Ph.de("jugador", perfil.nombre())));
        }
    }

    private int contar(List<Sancion> historial, TipoSancion tipo) {
        int total = 0;
        for (Sancion sancion : historial) {
            if (sancion.tipo() == tipo) {
                total++;
            }
        }
        return total;
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        return argumentos.length == 1 ? nombresConectados(emisor, argumentos[0]) : List.of();
    }
}
