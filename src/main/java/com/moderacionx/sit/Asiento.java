package com.moderacionx.sit;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

/** Un jugador sentado: la entidad que le sirve de silla y donde estaba antes. */
public record Asiento(ArmorStand silla, Location origen) {
}
