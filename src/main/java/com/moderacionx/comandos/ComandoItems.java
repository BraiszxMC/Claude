package com.moderacionx.comandos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /customitem - editor de items por comandos y por menu. */
public final class ComandoItems extends ComandoBase {

    public ComandoItems(ModeracionX plugin) {
        super(plugin);
    }

    @Override
    protected void ejecutar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (!puede(emisor, "moderacionx.items")) {
            return;
        }
        Player staff = comoJugador(emisor);
        if (staff == null) {
            return;
        }
        if (argumentos.length == 0) {
            plugin.items().abrirPrincipal(staff);
            return;
        }

        switch (argumentos[0].toLowerCase(Locale.ROOT)) {
            case "encantar", "enchant" -> encantar(staff, argumentos);
            case "nombre", "name", "renombrar" -> {
                if (!puede(emisor, "moderacionx.items.nombre")) {
                    return;
                }
                plugin.items().renombrar(staff, unir(argumentos, 1));
            }
            case "lore", "descripcion" -> {
                if (!puede(emisor, "moderacionx.items.lore")) {
                    return;
                }
                String resto = unir(argumentos, 1);
                if (resto.isBlank() || "limpiar".equalsIgnoreCase(resto)) {
                    plugin.items().limpiarLore(staff);
                } else {
                    plugin.items().anadirLore(staff, resto);
                }
            }
            case "irrompible", "unbreakable" -> {
                if (!puede(emisor, "moderacionx.items.atributos")) {
                    return;
                }
                plugin.items().alternarIrrompible(staff);
            }
            case "flags", "ocultar" -> {
                if (!puede(emisor, "moderacionx.items.atributos")) {
                    return;
                }
                plugin.items().alternarFlags(staff);
            }
            case "limpiar", "desencantar" -> plugin.items().quitarEncantamientos(staff);
            case "menu", "gui" -> plugin.items().abrirPrincipal(staff);
            default -> uso(emisor, "/customitem [encantar|nombre|lore|irrompible|flags|limpiar]");
        }
    }

    private void encantar(Player staff, String[] argumentos) {
        if (!puede(staff, "moderacionx.items.encantar")) {
            return;
        }
        if (argumentos.length < 2) {
            uso(staff, "/customitem encantar <encantamiento> [nivel]");
            return;
        }
        Enchantment encantamiento = plugin.items().buscar(argumentos[1]);
        if (encantamiento == null) {
            plugin.mensajes().enviar(staff, "items.encantamiento-desconocido",
                    Ph.de("encantamiento", argumentos[1]));
            return;
        }
        int nivel = plugin.items().nivelMaximo();
        if (argumentos.length >= 3) {
            try {
                nivel = Integer.parseInt(argumentos[2]);
            } catch (NumberFormatException excepcion) {
                plugin.mensajes().enviar(staff, "general.numero-invalido", Ph.de("valor", argumentos[2]));
                return;
            }
        }
        plugin.items().encantar(staff, encantamiento, nivel);
    }

    @Override
    protected List<String> completar(CommandSender emisor, String etiqueta, String[] argumentos) {
        if (argumentos.length == 1) {
            return filtrar(List.of("encantar", "nombre", "lore", "irrompible", "flags", "limpiar", "menu"),
                    argumentos[0]);
        }
        if (argumentos.length == 2 && "encantar".equalsIgnoreCase(argumentos[0])) {
            List<String> nombres = new ArrayList<>();
            for (Enchantment encantamiento : plugin.items().encantamientos()) {
                nombres.add(encantamiento.getKey().getKey());
            }
            return filtrar(nombres, argumentos[1]);
        }
        if (argumentos.length == 3 && "encantar".equalsIgnoreCase(argumentos[0])) {
            return filtrar(List.of("1", "5", "10", "50", "100", "255"), argumentos[2]);
        }
        return List.of();
    }
}
