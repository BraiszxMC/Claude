package com.moderacionx.sanciones;

import com.moderacionx.util.Tiempo;

import java.util.UUID;

/** Una sancion registrada en el historial. */
public final class Sancion {

    private long id;
    private final TipoSancion tipo;
    private final UUID objetivoUuid;
    private final String objetivoNombre;
    private final String objetivoIp;
    private final UUID autorUuid;
    private final String autorNombre;
    private final String razon;
    private final long creada;
    private final long expira;
    private boolean activa;
    private String removidaPor;
    private long removidaEn;
    private String razonRemocion;

    public Sancion(long id, TipoSancion tipo, UUID objetivoUuid, String objetivoNombre, String objetivoIp,
                   UUID autorUuid, String autorNombre, String razon, long creada, long expira, boolean activa,
                   String removidaPor, long removidaEn, String razonRemocion) {
        this.id = id;
        this.tipo = tipo;
        this.objetivoUuid = objetivoUuid;
        this.objetivoNombre = objetivoNombre;
        this.objetivoIp = objetivoIp;
        this.autorUuid = autorUuid;
        this.autorNombre = autorNombre;
        this.razon = razon;
        this.creada = creada;
        this.expira = expira;
        this.activa = activa;
        this.removidaPor = removidaPor;
        this.removidaEn = removidaEn;
        this.razonRemocion = razonRemocion;
    }

    /** Crea una sancion nueva, todavia sin id asignado. */
    public static Sancion nueva(TipoSancion tipo, UUID objetivoUuid, String objetivoNombre, String objetivoIp,
                                UUID autorUuid, String autorNombre, String razon, long duracionMilis) {
        long ahora = System.currentTimeMillis();
        long expira = duracionMilis == Tiempo.PERMANENTE ? Tiempo.PERMANENTE : ahora + duracionMilis;
        return new Sancion(-1L, tipo, objetivoUuid, objetivoNombre, objetivoIp, autorUuid, autorNombre,
                razon, ahora, expira, true, null, 0L, null);
    }

    public long id() {
        return id;
    }

    public void id(long id) {
        this.id = id;
    }

    public TipoSancion tipo() {
        return tipo;
    }

    public UUID objetivoUuid() {
        return objetivoUuid;
    }

    public String objetivoNombre() {
        return objetivoNombre;
    }

    public String objetivoIp() {
        return objetivoIp;
    }

    public UUID autorUuid() {
        return autorUuid;
    }

    public String autorNombre() {
        return autorNombre;
    }

    public String razon() {
        return razon;
    }

    public long creada() {
        return creada;
    }

    public long expira() {
        return expira;
    }

    public boolean permanente() {
        return expira == Tiempo.PERMANENTE;
    }

    public long duracion() {
        return permanente() ? Tiempo.PERMANENTE : expira - creada;
    }

    public boolean activa() {
        return activa;
    }

    public void activa(boolean activa) {
        this.activa = activa;
    }

    /** ¿Sigue en vigor ahora mismo? */
    public boolean vigente() {
        return activa && !caducada();
    }

    public boolean caducada() {
        return !permanente() && System.currentTimeMillis() >= expira;
    }

    public String removidaPor() {
        return removidaPor;
    }

    public long removidaEn() {
        return removidaEn;
    }

    public String razonRemocion() {
        return razonRemocion;
    }

    public void retirar(String autor, String razon) {
        this.activa = false;
        this.removidaPor = autor;
        this.removidaEn = System.currentTimeMillis();
        this.razonRemocion = razon;
    }
}
