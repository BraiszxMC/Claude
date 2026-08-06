package com.moderacionx.sit;

import org.bukkit.Location;

/** Zona en la que no se puede uno sentar (una arena de BlockBall, por ejemplo). */
public record ZonaSit(String nombre, String mundo,
                      int minX, int minY, int minZ,
                      int maxX, int maxY, int maxZ) {

    public static ZonaSit de(String nombre, String mundo, Location a, Location b) {
        return new ZonaSit(nombre, mundo,
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    public boolean contiene(Location ubicacion) {
        if (ubicacion == null || ubicacion.getWorld() == null) {
            return false;
        }
        if (!ubicacion.getWorld().getName().equalsIgnoreCase(mundo)) {
            return false;
        }
        int x = ubicacion.getBlockX();
        int y = ubicacion.getBlockY();
        int z = ubicacion.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public String coordenadas() {
        return mundo + " (" + minX + ", " + minY + ", " + minZ + ") -> (" + maxX + ", " + maxY + ", " + maxZ + ")";
    }
}
