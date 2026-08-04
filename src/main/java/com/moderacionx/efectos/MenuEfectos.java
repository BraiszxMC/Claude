package com.moderacionx.efectos;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marca a que pantalla del asistente de /efectos pertenece un inventario. */
public final class MenuEfectos implements InventoryHolder {

    /** Pantallas del asistente. */
    public enum Pantalla {
        EFECTOS,
        OBJETIVOS,
        JUGADORES,
        AJUSTES
    }

    private final Pantalla pantalla;
    private Inventory inventario;

    public MenuEfectos(Pantalla pantalla) {
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
