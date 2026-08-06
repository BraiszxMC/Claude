package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.espia.RegistroComando;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * /spyX - busca en el registro de comandos.
 * <p>Ejemplos:
 * <pre>
 *   /spyX attribute                 todo el que haya usado algo con "attribute"
 *   /spyX attribute jugador:Pepe    solo lo de Pepe
 *   /spyX attribute dias:7          solo la ultima semana
 *   /spyX * jugador:Pepe            todo lo que ha escrito Pepe
 *   /spyX attribute pagina:2
 * </pre>
 */
public final class ComandoSpyX extends ComandoBase {

    public ComandoSpyX(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.spy.registro")) {
            return;
        }

        List<String> textos = new ArrayList<>();
        String nombreJugador = null;
        int dias = 0;
        int pagina = 1;

        for (String argumento : argumentos) {
            String bajo = argumento.toLowerCase(Locale.ROOT);
            if (bajo.startsWith("jugador:") || bajo.startsWith("player:")) {
                nombreJugador = argumento.substring(argumento.indexOf(':') + 1);
            } else if (bajo.startsWith("dias:") || bajo.startsWith("days:")) {
                dias = entero(argumento, 0);
            } else if (bajo.startsWith("pagina:") || bajo.startsWith("page:")) {
                pagina = Math.max(1, entero(argumento, 1));
            } else {
                textos.add(argumento);
            }
        }

        String busqueda = String.join(" ", textos).trim();
        if ("*".equals(busqueda) || "todo".equalsIgnoreCase(busqueda)) {
            busqueda = "";
        }
        if (busqueda.isEmpty() && nombreJugador == null && argumentos.length > 0) {
            uso(emisor, "/spyX <texto> [jugador:<nombre>] [dias:<n>] [pagina:<n>]");
            return;
        }

        final String textoFinal = busqueda;
        final String nombreFinal = nombreJugador;
        final int diasFinal = dias;
        final int paginaFinal = pagina;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = null;
            if (nombreFinal != null) {
                Optional<Perfil> perfil = plugin.sanciones().resolver(nombreFinal);
                if (perfil.isEmpty()) {
                    plugin.mensajes().enviar(emisor, "general.jugador-no-encontrado",
                            Ph.de("jugador", nombreFinal));
                    return;
                }
                uuid = perfil.get().uuid();
            }

            long desde = diasFinal > 0 ? System.currentTimeMillis() - (long) diasFinal * 86_400_000L : 0L;
            int porPagina = Math.max(1, plugin.ajustes().raiz().getInt("espia.registro.lineas-por-pagina", 10));

            try {
                // lo que hay en la cola todavia no esta en la base de datos
                plugin.espias().volcarAhora();

                int total = plugin.sanciones().almacen().contarComandos(textoFinal, uuid, desde);
                if (total == 0) {
                    plugin.mensajes().enviar(emisor, "espia.registro.vacio", Ph.de()
                            .con("busqueda", textoFinal.isEmpty() ? "*" : textoFinal)
                            .con("jugador", nombreFinal == null ? "-" : nombreFinal));
                    return;
                }
                int paginas = (int) Math.ceil(total / (double) porPagina);
                int actual = Math.min(paginaFinal, paginas);
                List<RegistroComando> registros = plugin.sanciones().almacen()
                        .buscarComandos(textoFinal, uuid, desde, porPagina, (actual - 1) * porPagina);

                mostrar(emisor, registros, textoFinal, nombreFinal, total, actual, paginas);
            } catch (Exception excepcion) {
                plugin.mensajes().enviar(emisor, "general.error",
                        Ph.de("error", String.valueOf(excepcion.getMessage())));
                plugin.getLogger().warning("Error buscando en el registro de comandos: " + excepcion);
            }
        });
    }

    private void mostrar(CommandSender emisor, List<RegistroComando> registros, String busqueda,
                         String jugador, int total, int pagina, int paginas) {
        String formato = plugin.ajustes().formatoFecha();
        String zona = plugin.ajustes().zonaHoraria();

        plugin.mensajes().enviar(emisor, "espia.registro.cabecera", Ph.de()
                .con("busqueda", busqueda.isEmpty() ? "*" : busqueda)
                .con("jugador", jugador == null ? "-" : jugador)
                .con("total", total)
                .con("pagina", pagina)
                .con("paginas", paginas));

        for (RegistroComando registro : registros) {
            Ph datos = Ph.de()
                    .con("jugador", registro.nombre())
                    .con("comando", registro.linea())
                    .con("raiz", registro.comando())
                    .con("mundo", registro.mundo() == null ? "-" : registro.mundo())
                    .con("uuid", registro.uuid() == null ? "-" : registro.uuid().toString())
                    .con("fecha", Tiempo.fecha(registro.fecha(), formato, zona))
                    .con("hace", Tiempo.formatear(System.currentTimeMillis() - registro.fecha(), plugin.mensajes()));

            Component linea = plugin.mensajes().comp("espia.registro.linea", datos)
                    .hoverEvent(HoverEvent.showText(plugin.mensajes().bloque("espia.registro.linea-hover", datos)))
                    .clickEvent(ClickEvent.suggestCommand(registro.linea()));
            emisor.sendMessage(linea);
        }

        if (pagina < paginas) {
            plugin.mensajes().enviar(emisor, "espia.registro.pie", Ph.de()
                    .con("busqueda", busqueda.isEmpty() ? "*" : busqueda)
                    .con("siguiente", pagina + 1));
        }
    }

    private int entero(String argumento, int porDefecto) {
        try {
            return Integer.parseInt(argumento.substring(argumento.indexOf(':') + 1));
        } catch (NumberFormatException excepcion) {
            return porDefecto;
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        String actual = argumentos.length == 0 ? "" : argumentos[argumentos.length - 1];
        if (actual.toLowerCase(Locale.ROOT).startsWith("jugador:")) {
            List<String> nombres = new ArrayList<>();
            for (var jugador : Bukkit.getOnlinePlayers()) {
                nombres.add("jugador:" + jugador.getName());
            }
            return filtrar(nombres, actual);
        }
        if (argumentos.length <= 1) {
            List<String> sugerencias = new ArrayList<>(List.of("*", "op", "gamemode", "give", "attribute", "tp"));
            sugerencias.add("jugador:");
            return filtrar(sugerencias, actual);
        }
        return filtrar(List.of("jugador:", "dias:7", "dias:30", "pagina:2"), actual);
    }
}
