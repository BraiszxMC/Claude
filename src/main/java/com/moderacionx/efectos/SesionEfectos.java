package com.moderacionx.efectos;

import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Lo que el staff lleva elegido dentro del menu de /efectos. */
public final class SesionEfectos {

    /** Que se va a hacer al pulsar "aplicar". */
    public enum Modo {
        APLICAR,
        QUITAR,
        QUITAR_TODOS
    }

    /** Como se eligen los jugadores. */
    public enum Seleccion {
        UNO,
        VARIOS,
        TODOS
    }

    private PotionEffectType efecto;
    private Modo modo = Modo.APLICAR;
    private Seleccion seleccion = Seleccion.UNO;
    private final Set<UUID> objetivos = new LinkedHashSet<>();

    private int duracion;
    private int nivel;
    private boolean particulas;
    private boolean permanente;
    private int paginaEfectos;
    private int paginaJugadores;

    public SesionEfectos(int duracionPorDefecto, int nivelPorDefecto, boolean particulasPorDefecto) {
        this.duracion = duracionPorDefecto;
        this.nivel = nivelPorDefecto;
        this.particulas = particulasPorDefecto;
    }

    public PotionEffectType efecto() {
        return efecto;
    }

    public void efecto(PotionEffectType efecto) {
        this.efecto = efecto;
    }

    public Modo modo() {
        return modo;
    }

    public void modo(Modo modo) {
        this.modo = modo;
    }

    public Seleccion seleccion() {
        return seleccion;
    }

    public void seleccion(Seleccion seleccion) {
        this.seleccion = seleccion;
    }

    public Set<UUID> objetivos() {
        return objetivos;
    }

    public boolean alternarObjetivo(UUID uuid) {
        if (objetivos.contains(uuid)) {
            objetivos.remove(uuid);
            return false;
        }
        objetivos.add(uuid);
        return true;
    }

    public int duracion() {
        return duracion;
    }

    /** Suma segundos respetando los limites configurados. */
    public void sumarDuracion(int segundos, int maximo) {
        duracion = Math.max(1, Math.min(maximo, duracion + segundos));
    }

    public void duracion(int segundos) {
        this.duracion = segundos;
    }

    public int nivel() {
        return nivel;
    }

    public void sumarNivel(int cantidad, int maximo) {
        nivel = Math.max(1, Math.min(maximo, nivel + cantidad));
    }

    /** Fija el nivel directamente (botones de minimo y maximo). */
    public void nivel(int valor) {
        this.nivel = Math.max(1, valor);
    }

    public boolean particulas() {
        return particulas;
    }

    public void alternarParticulas() {
        particulas = !particulas;
    }

    public boolean permanente() {
        return permanente;
    }

    public void alternarPermanente() {
        permanente = !permanente;
    }

    public int paginaEfectos() {
        return paginaEfectos;
    }

    public void paginaEfectos(int pagina) {
        this.paginaEfectos = Math.max(0, pagina);
    }

    public int paginaJugadores() {
        return paginaJugadores;
    }

    public void paginaJugadores(int pagina) {
        this.paginaJugadores = Math.max(0, pagina);
    }
}
