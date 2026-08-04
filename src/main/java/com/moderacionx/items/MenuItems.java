package com.moderacionx.items;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marca a que pantalla del editor de items pertenece un inventario. */
public final class MenuItems implements InventoryHolder {

    /** Pantallas del editor. */
    public enum Pantalla {
        PRINCIPAL,
        ENCANTAMIENTOS
    }

    private final Pantalla pantalla;
    private Inventory inventario;

    public MenuItems(Pantalla pantalla) {
        this.pantalla = pantalla;
    }

    public Pantalla pantalla() {
        return pantalla;
    }

    public void inventario(Inventory inventario) {
        this.inventario = inventario;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventario;
    }
}
