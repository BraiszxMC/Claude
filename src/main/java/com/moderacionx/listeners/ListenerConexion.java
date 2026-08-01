package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import com.moderacionx.almacen.Perfil;
import com.moderacionx.antivpn.ResultadoVPN;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Tiempo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;

/** Comprueba baneos, baneos de IP y VPN antes de dejar entrar a nadie. */
public final class ListenerConexion implements Listener {

    private final ModeracionX plugin;

    public ListenerConexion(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void alConectar(AsyncPlayerPreLoginEvent evento) {
        UUID uuid = evento.getUniqueId();
        String nombre = evento.getName();
        String ip = evento.getAddress() == null ? null : evento.getAddress().getHostAddress();

        plugin.sanciones().guardarPerfil(new Perfil(uuid, nombre, ip,
                System.currentTimeMillis(), System.currentTimeMillis()));

        Optional<Sancion> ban = plugin.sanciones().banDe(uuid);
        if (ban.isPresent()) {
            evento.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
            evento.kickMessage(plugin.sanciones().pantalla(ban.get()));
            return;
        }

        Optional<Sancion> banIp = plugin.sanciones().banIpDe(ip);
        if (banIp.isPresent()) {
            evento.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
            evento.kickMessage(plugin.sanciones().pantalla(banIp.get()));
            return;
        }

        comprobarVpn(evento, uuid, nombre, ip);
    }

    private void comprobarVpn(AsyncPlayerPreLoginEvent evento, UUID uuid, String nombre, String ip) {
        var antivpn = plugin.antivpn();
        if (!antivpn.activado() || ip == null || antivpn.exento(uuid, nombre, ip)) {
            return;
        }

        ResultadoVPN resultado = antivpn.comprobar(ip);
        if (!resultado.valido()) {
            if (!antivpn.fallarAbierto()) {
                evento.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                evento.kickMessage(plugin.mensajes().bloque("antivpn.pantalla", Ph.de("ip", ip)));
            }
            return;
        }
        if (!resultado.bloquear(antivpn.bloquearHosting())) {
            return;
        }

        if (antivpn.avisarStaff()) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.sanciones().avisarStaff("antivpn.aviso-staff", Ph.de()
                    .con("jugador", nombre)
                    .con("ip", ip)
                    .con("proveedor", resultado.proveedor())));
        }

        switch (antivpn.accion()) {
            case "AVISAR" -> {
                // solo se avisa, el jugador entra igualmente
            }
            case "BAN" -> {
                Sancion sancion = plugin.sanciones().registrar(Sancion.nueva(TipoSancion.BAN, uuid, nombre, ip,
                        null, "AntiVPN", plugin.mensajes().palabra("antivpn.razon-ban"), Tiempo.PERMANENTE));
                evento.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
                evento.kickMessage(plugin.sanciones().pantalla(sancion));
            }
            default -> {
                evento.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                evento.kickMessage(plugin.mensajes().bloque("antivpn.pantalla", Ph.de("ip", ip)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alEntrar(PlayerJoinEvent evento) {
        Player jugador = evento.getPlayer();
        plugin.antivpn().recordarBypass(jugador.getUniqueId(),
                jugador.hasPermission("moderacionx.antivpn.bypass"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alSalir(PlayerQuitEvent evento) {
        UUID uuid = evento.getPlayer().getUniqueId();
        plugin.invsee().cerrarVistasDe(uuid);
        plugin.invsee().olvidar(uuid);
        plugin.secretos().limpiar(uuid);
    }
}
