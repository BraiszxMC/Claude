package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.antivpn.ResultadoVPN;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** /moderacionx - administracion del plugin. */
public final class ComandoPrincipal extends ComandoBase {

    public ComandoPrincipal(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.admin")) {
            return;
        }
        if (argumentos.length == 0) {
            cabecera(emisor);
            uso(emisor, "/mx <recargar|info|vpn|efectos|version>");
            return;
        }

        switch (argumentos[0].toLowerCase(Locale.ROOT)) {
            case "recargar", "reload", "rl" -> recargar(emisor);
            case "info" -> info(emisor, argumentos);
            case "vpn", "antivpn" -> vpn(emisor, argumentos);
            case "efectos" -> listarEfectos(emisor);
            case "version", "ver" -> cabecera(emisor);
            default -> uso(emisor, "/mx <recargar|info|vpn|efectos|version>");
        }
    }

    private void cabecera(CommandSender emisor) {
        emisor.sendMessage(plugin.mensajes().comp("admin.cabecera",
                Ph.de("version", plugin.getPluginMeta().getVersion())));
    }

    private void recargar(CommandSender emisor) {
        long inicio = System.currentTimeMillis();
        try {
            plugin.recargarTodo();
            plugin.mensajes().enviar(emisor, "general.recargado",
                    Ph.de("ms", System.currentTimeMillis() - inicio));
        } catch (Exception excepcion) {
            plugin.mensajes().enviar(emisor, "general.error-recarga",
                    Ph.de("error", String.valueOf(excepcion.getMessage())));
            plugin.getLogger().severe("Error recargando: " + excepcion);
        }
    }

    private void info(CommandSender emisor, String[] argumentos) {
        if (argumentos.length == 1) {
            cabecera(emisor);
            int total;
            try {
                total = plugin.sanciones().almacen().total();
            } catch (Exception excepcion) {
                total = -1;
            }
            plugin.mensajes().enviar(emisor, "admin.info", Ph.de()
                    .con("almacen", plugin.sanciones().almacen().nombre())
                    .con("total", total)
                    .con("baneos", plugin.sanciones().baneosActivos() + plugin.sanciones().baneosIpActivos())
                    .con("mutes", plugin.sanciones().silenciosActivos())
                    .con("antivpn", plugin.antivpn().activado() ? plugin.antivpn().accion() : "OFF")
                    .con("cache", plugin.antivpn().enCache())
                    .con("espias", plugin.espias().cantidad())
                    .con("secretos", plugin.secretos().cantidad()));
            return;
        }

        String nombre = argumentos[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<Perfil> encontrado = plugin.sanciones().resolver(nombre);
            if (encontrado.isEmpty()) {
                plugin.mensajes().enviar(emisor, "general.jugador-no-encontrado", Ph.de("jugador", nombre));
                return;
            }
            Perfil perfil = encontrado.get();
            int sanciones = plugin.sanciones().historial(perfil).size();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.mensajes().enviar(emisor, "admin.info-jugador", Ph.de()
                    .con("jugador", perfil.nombre())
                    .con("uuid", perfil.uuid())
                    .con("ip", perfil.ip() == null ? "-" : perfil.ip())
                    .con("conexion", perfil.ultimaConexion() == 0 ? "-" : Tiempo.fecha(perfil.ultimaConexion(),
                            plugin.ajustes().formatoFecha(), plugin.ajustes().zonaHoraria()))
                    .crudo("baneado", plugin.mensajes().bruto(
                            plugin.sanciones().banDe(perfil.uuid()).isPresent() ? "admin.afirmativo" : "admin.negativo"))
                    .crudo("silenciado", plugin.mensajes().bruto(
                            plugin.sanciones().muteDe(perfil.uuid()).isPresent() ? "admin.afirmativo" : "admin.negativo"))
                    .con("sanciones", sanciones)
                    .con("warns", plugin.sanciones().advertenciasActivas(perfil.uuid()))));
        });
    }

    /** Lista los efectos que ve el menu de /efectos, util para revisar los nombres del config. */
    private void listarEfectos(CommandSender emisor) {
        var tipos = plugin.efectos().tipos();
        List<String> nombres = new java.util.ArrayList<>();
        for (var tipo : tipos) {
            nombres.add(plugin.efectos().nombreDe(tipo));
        }
        plugin.mensajes().enviar(emisor, "admin.efectos", Ph.de()
                .con("cantidad", tipos.size())
                .con("lista", String.join(", ", nombres)));
    }

    private void vpn(CommandSender emisor, String[] argumentos) {
        if (argumentos.length < 2) {
            uso(emisor, "/mx vpn <check|whitelist|cache> ...");
            return;
        }
        switch (argumentos[1].toLowerCase(Locale.ROOT)) {
            case "check", "comprobar" -> {
                if (argumentos.length < 3) {
                    uso(emisor, "/mx vpn check <ip|jugador>");
                    return;
                }
                String objetivo = argumentos[2];
                String ip = ComandoBanIP.esIp(objetivo)
                        ? objetivo
                        : plugin.sanciones().resolver(objetivo).map(Perfil::ip).orElse(null);
                if (ip == null) {
                    plugin.mensajes().enviar(emisor, "banip.sin-ip", Ph.de("jugador", objetivo));
                    return;
                }
                plugin.mensajes().enviar(emisor, "antivpn.comprobando", Ph.de("ip", ip));
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    ResultadoVPN resultado = plugin.antivpn().comprobar(ip);
                    String texto = !resultado.valido()
                            ? "antivpn.sin-datos"
                            : (resultado.bloquear(plugin.antivpn().bloquearHosting()) ? "antivpn.es-vpn" : "antivpn.no-es-vpn");
                    plugin.mensajes().enviar(emisor, "antivpn.resultado", Ph.de()
                            .con("ip", ip)
                            .crudo("resultado", plugin.mensajes().bruto(texto))
                            .con("proveedor", resultado.proveedor()));
                });
            }
            case "whitelist", "lista" -> {
                if (argumentos.length < 4) {
                    uso(emisor, "/mx vpn whitelist <add|remove> <ip|jugador>");
                    return;
                }
                boolean anadir = List.of("add", "anadir", "add ", "+").contains(argumentos[2].toLowerCase(Locale.ROOT));
                String valor = argumentos[3];
                if (ComandoBanIP.esIp(valor)) {
                    plugin.antivpn().whitelistIp(valor, anadir);
                } else {
                    plugin.antivpn().whitelistJugador(valor, anadir);
                }
                plugin.mensajes().enviar(emisor, anadir ? "antivpn.whitelist-anadida" : "antivpn.whitelist-quitada",
                        Ph.de("valor", valor));
            }
            case "cache" -> {
                plugin.antivpn().limpiarCache();
                plugin.mensajes().enviar(emisor, "general.recargado", Ph.de("ms", 0));
            }
            default -> uso(emisor, "/mx vpn <check|whitelist|cache>");
        }
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            return filtrar(List.of("recargar", "info", "vpn", "efectos", "version"), argumentos[0]);
        }
        if (argumentos.length == 2 && "vpn".equalsIgnoreCase(argumentos[0])) {
            return filtrar(List.of("check", "whitelist", "cache"), argumentos[1]);
        }
        if (argumentos.length == 2 && "info".equalsIgnoreCase(argumentos[0])) {
            return nombresConectados(emisor, argumentos[1]);
        }
        if (argumentos.length == 3 && "vpn".equalsIgnoreCase(argumentos[0])
                && "whitelist".equalsIgnoreCase(argumentos[1])) {
            return filtrar(List.of("add", "remove"), argumentos[2]);
        }
        return List.of();
    }
}
