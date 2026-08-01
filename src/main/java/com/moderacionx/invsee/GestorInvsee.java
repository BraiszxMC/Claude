package com.moderacionx.invsee;

import com.moderacionx.ModeracionX;
import com.moderacionx.sanciones.GestorSanciones;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Abre y controla las vistas de inventario del staff. */
public final class GestorInvsee {

    private final ModeracionX plugin;
    /** staff -> objetivo (solo vistas en vivo). */
    private final Map<UUID, UUID> enVivo = new ConcurrentHashMap<>();

    public GestorInvsee(ModeracionX plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------- en vivo

    public void abrir(Player staff, Player objetivo) {
        enVivo.put(staff.getUniqueId(), objetivo.getUniqueId());
        staff.openInventory(objetivo.getInventory());
        plugin.mensajes().enviar(staff, "invsee.abriendo", Ph.de("jugador", objetivo.getName()));
        if (plugin.ajustes().invseeSoloLectura() && !staff.hasPermission("moderacionx.invsee.editar")) {
            plugin.mensajes().enviar(staff, "invsee.solo-lectura");
        }
        if (plugin.ajustes().invseeAvisarObjetivo()) {
            plugin.mensajes().enviar(objetivo, "invsee.avisado", Ph.de("autor", staff.getName()));
        }
    }

    public void abrirEnder(Player staff, Player objetivo) {
        enVivo.put(staff.getUniqueId(), objetivo.getUniqueId());
        staff.openInventory(objetivo.getEnderChest());
        plugin.mensajes().enviar(staff, "invsee.echest", Ph.de("jugador", objetivo.getName()));
        if (plugin.ajustes().invseeAvisarObjetivo()) {
            plugin.mensajes().enviar(objetivo, "invsee.avisado", Ph.de("autor", staff.getName()));
        }
    }

    public boolean viendo(UUID staff) {
        return enVivo.containsKey(staff);
    }

    public UUID objetivoDe(UUID staff) {
        return enVivo.get(staff);
    }

    public void olvidar(UUID staff) {
        enVivo.remove(staff);
    }

    /** Cierra las vistas abiertas sobre un jugador que se desconecta. */
    public void cerrarVistasDe(UUID objetivo) {
        if (!plugin.ajustes().invseeCerrarAlDesconectar()) {
            return;
        }
        for (Map.Entry<UUID, UUID> entrada : Map.copyOf(enVivo).entrySet()) {
            if (objetivo.equals(entrada.getValue())) {
                Player staff = Bukkit.getPlayer(entrada.getKey());
                if (staff != null) {
                    staff.closeInventory();
                }
                enVivo.remove(entrada.getKey());
            }
        }
    }

    // ----------------------------------------------------------- detallada

    /** Vista completa de solo lectura: inventario, armadura, mano izquierda y datos del jugador. */
    public void abrirDetallada(Player staff, Player objetivo) {
        VistaDetallada holder = new VistaDetallada(objetivo.getUniqueId());
        Inventory vista = Bukkit.createInventory(holder, 54,
                Texto.comp(plugin.ajustes().invseeTituloDetallado(), Ph.de("jugador", objetivo.getName())));
        holder.inventario(vista);

        PlayerInventory inventario = objetivo.getInventory();
        // filas 1-3: inventario principal (indices 9..35)
        for (int i = 9; i < 36; i++) {
            vista.setItem(i - 9, inventario.getItem(i));
        }
        // fila 4: barra rapida (indices 0..8)
        for (int i = 0; i < 9; i++) {
            vista.setItem(27 + i, inventario.getItem(i));
        }
        // fila 5: armadura y mano izquierda
        ItemStack[] armadura = inventario.getArmorContents();
        for (int i = 0; i < armadura.length && i < 4; i++) {
            vista.setItem(36 + i, armadura[armadura.length - 1 - i]);
        }
        vista.setItem(40, inventario.getItemInOffHand());

        ItemStack relleno = relleno();
        for (int i : new int[]{41, 42, 43, 44, 45, 46, 47, 48, 50, 51, 52, 53}) {
            vista.setItem(i, relleno);
        }
        vista.setItem(49, cabeza(objetivo));

        staff.openInventory(vista);
        plugin.mensajes().enviar(staff, "invsee.abriendo", Ph.de("jugador", objetivo.getName()));
        if (plugin.ajustes().invseeAvisarObjetivo()) {
            plugin.mensajes().enviar(objetivo, "invsee.avisado", Ph.de("autor", staff.getName()));
        }
    }

    private ItemStack relleno() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Texto.item(plugin.mensajes().bruto("invsee.detallado.nombre-vacio"), null));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack cabeza(Player objetivo) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta calavera) {
            calavera.setOwningPlayer(objetivo);
        }
        if (meta != null) {
            double vida = objetivo.getHealth();
            double maxima = 20.0D;
            var atributo = objetivo.getAttribute(Attribute.MAX_HEALTH);
            if (atributo != null) {
                maxima = atributo.getValue();
            }
            Ph placeholders = Ph.de()
                    .con("jugador", objetivo.getName())
                    .con("uuid", objetivo.getUniqueId().toString())
                    .con("ip", String.valueOf(GestorSanciones.ipDe(objetivo)))
                    .con("vida", String.format("%.1f/%.1f", vida, maxima))
                    .con("hambre", objetivo.getFoodLevel() + "/20")
                    .con("modo", objetivo.getGameMode().name())
                    .con("nivel", objetivo.getLevel())
                    .con("ping", objetivo.getPing())
                    .con("mundo", objetivo.getWorld().getName())
                    .con("x", objetivo.getLocation().getBlockX())
                    .con("y", objetivo.getLocation().getBlockY())
                    .con("z", objetivo.getLocation().getBlockZ())
                    .con("sanciones", plugin.sanciones().advertenciasActivas(objetivo.getUniqueId()));

            meta.displayName(Texto.item(plugin.mensajes().bruto("invsee.detallado.nombre-info"), placeholders));
            List<net.kyori.adventure.text.Component> descripcion = new ArrayList<>();
            for (String linea : plugin.mensajes().brutoLista("invsee.detallado.info")) {
                descripcion.add(Texto.item(linea, placeholders));
            }
            meta.lore(descripcion);
            item.setItemMeta(meta);
        }
        return item;
    }
}
