package com.moderacionx.invsee;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Marca un inventario como "foto" de solo lectura del inventario de alguien. */
public final class VistaDetallada implements InventoryHolder {

    private final UUID objetivo;
    private Inventory inventario;

    public VistaDetallada(UUID objetivo) {
        this.objetivo = objetivo;
    }

    public UUID objetivo() {
        return objetivo;
    }

    public void inventario(Inventory inventario) {
        this.inventario = inventario;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventario;
    }
}
