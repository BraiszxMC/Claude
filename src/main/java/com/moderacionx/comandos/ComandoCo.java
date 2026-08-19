package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.bloques.RegistroBloque;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /co - registro de bloques (estilo CoreProtect).
 * <pre>
 *   /co i                 activa/desactiva el inspector (toca un bloque y ves su historial)
 *   /co lookup &lt;jugador&gt;   que ha roto/puesto un jugador
 *   /co help
 * </pre>
 */
public final class ComandoCo extends ComandoBase {

    public ComandoCo(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.bloques")) {
            return;
        }
        if (!plugin.bloques().disponible()) {
            plugin.mensajes().enviar(emisor, "bloques.desactivado");
            return;
        }
        if (argumentos.length == 0) {
            ayuda(emisor);
            return;
        }

        switch (argumentos[0].toLowerCase(Locale.ROOT)) {
            case "i", "inspect", "inspector" -> inspector(emisor);
            case "lookup", "l", "buscar" -> lookup(emisor, argumentos);
            case "help", "ayuda" -> ayuda(emisor);
            default -> ayuda(emisor);
        }
    }

    private void inspector(CommandSender emisor) {
        Player jugador = comoJugador(emisor);
        if (jugador == null) {
            return;
        }
        boolean activo = plugin.bloques().alternarInspector(jugador.getUniqueId());
        plugin.mensajes().enviar(emisor, activo ? "bloques.inspector-on" : "bloques.inspector-off");
    }

    private void lookup(CommandSender emisor, String[] argumentos) {
        if (argumentos.length < 2) {
            uso(emisor, "/co lookup <jugador> [dias:<n>] [pagina:<n>]");
            return;
        }
        String nombre = argumentos[1];
        int dias = 0;
        int pagina = 1;
        for (int i = 2; i < argumentos.length; i++) {
            String bajo = argumentos[i].toLowerCase(Locale.ROOT);
            if (bajo.startsWith("dias:") || bajo.startsWith("days:")) {
                dias = entero(argumentos[i]);
            } else if (bajo.startsWith("pagina:") || bajo.startsWith("page:")) {
                pagina = Math.max(1, entero(argumentos[i]));
            }
        }

        final int diasF = dias;
        final int paginaF = pagina;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<Perfil> perfil = plugin.sanciones().resolver(nombre);
            if (perfil.isEmpty()) {
                plugin.mensajes().enviar(emisor, "general.jugador-no-encontrado", Ph.de("jugador", nombre));
                return;
            }
            var uuid = perfil.get().uuid();
            long desde = diasF > 0 ? System.currentTimeMillis() - (long) diasF * 86_400_000L : 0L;
            int porPagina = Math.max(1, plugin.ajustes().raiz().getInt("bloques.lineas-por-consulta", 10));

            plugin.bloques().volcarAhora();
            int total = plugin.bloques().contarJugador(uuid, desde);
            if (total == 0) {
                plugin.mensajes().enviar(emisor, "bloques.lookup-vacio", Ph.de("jugador", perfil.get().nombre()));
                return;
            }
            int paginas = (int) Math.ceil(total / (double) porPagina);
            int actual = Math.min(paginaF, paginas);
            List<RegistroBloque> lista = plugin.bloques().historialJugador(
                    uuid, desde, porPagina, (actual - 1) * porPagina);

            plugin.mensajes().enviar(emisor, "bloques.lookup-cabecera", Ph.de()
                    .con("jugador", perfil.get().nombre())
                    .con("total", total)
                    .con("pagina", actual)
                    .con("paginas", paginas));
            for (RegistroBloque r : lista) {
                plugin.mensajes().enviar(emisor, "bloques.lookup-linea", Ph.de()
                        .con("hace", Tiempo.formatear(System.currentTimeMillis() - r.fecha(), plugin.mensajes()))
                        .crudo("accion", plugin.mensajes().bruto(r.rotura() ? "bloques.rotura" : "bloques.colocacion"))
                        .con("material", r.material())
                        .con("mundo", r.mundo())
                        .con("x", r.x()).con("y", r.y()).con("z", r.z()));
            }
            if (actual < paginas) {
                plugin.mensajes().enviar(emisor, "bloques.lookup-pie", Ph.de()
                        .con("jugador", perfil.get().nombre())
                        .con("siguiente", actual + 1));
            }
        });
    }

    private void ayuda(CommandSender emisor) {
        plugin.mensajes().enviar(emisor, "bloques.ayuda");
    }

    private int entero(String argumento) {
        try {
            return Integer.parseInt(argumento.substring(argumento.indexOf(':') + 1));
        } catch (NumberFormatException excepcion) {
            return 0;
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            return filtrar(List.of("i", "lookup", "help"), argumentos[0]);
        }
        if (argumentos.length == 2 && ("lookup".equalsIgnoreCase(argumentos[0]) || "l".equalsIgnoreCase(argumentos[0]))) {
            return nombresConectados(emisor, argumentos[1]);
        }
        return List.of();
    }
}
