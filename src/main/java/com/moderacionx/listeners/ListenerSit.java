package com.moderacionx.listeners;

import com.moderacionx.ModeracionX;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Se ocupa de levantar al jugador y de proteger las sillas. */
public final class ListenerSit implements Listener {

    private final ModeracionX plugin;

    public ListenerSit(ModeracionX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alBajarse(EntityDismountEvent evento) {
        if (!(evento.getEntity() instanceof Player jugador) || !plugin.sit().esSilla(evento.getDismounted())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.sit().levantar(jugador));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alSalir(PlayerQuitEvent evento) {
        plugin.sit().levantar(evento.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alTeletransportarse(PlayerTeleportEvent evento) {
        if (plugin.sit().sentado(evento.getPlayer().getUniqueId())) {
            plugin.sit().levantar(evento.getPlayer());
        }
    }

    /** Las sillas no reciben dano de nadie. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void alRecibirDano(EntityDamageEvent evento) {
        if (plugin.sit().esSilla(evento.getEntity())) {
            evento.setCancelled(true);
            return;
        }
        if (!(evento.getEntity() instanceof Player jugador)) {
            return;
        }
        if (plugin.sit().sentado(jugador.getUniqueId())
                && plugin.ajustes().raiz().getBoolean("sit.levantar-al-recibir-dano", true)) {
            plugin.sit().levantar(jugador);
        }
    }

    /** Sentarse con shift + clic derecho, si esta activado en la configuracion. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void alInteractuar(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK
                || evento.getHand() != EquipmentSlot.HAND
                || evento.getClickedBlock() == null) {
            return;
        }
        if (!plugin.ajustes().raiz().getBoolean("sit.clic-derecho", false)) {
            return;
        }
        Player jugador = evento.getPlayer();
        if (plugin.ajustes().raiz().getBoolean("sit.clic-derecho-agachado", true) && !jugador.isSneaking()) {
            return;
        }
        if (!jugador.hasPermission("moderacionx.sit") || plugin.sit().sentado(jugador.getUniqueId())) {
            return;
        }
        if (!jugador.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }

        Block bloque = evento.getClickedBlock();
        if (plugin.sit().motivoNo(jugador, bloque) != null) {
            return;
        }
        evento.setCancelled(true);
        String error = plugin.sit().sentar(jugador, bloque);
        if (error == null) {
            plugin.mensajes().enviar(jugador, "sit.sentado");
        }
    }
}
