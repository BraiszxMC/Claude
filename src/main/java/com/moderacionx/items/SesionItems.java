package com.moderacionx.items;

/** Estado del editor de items de un jugador. */
public final class SesionItems {

    /** Texto que el editor esta esperando por el chat. */
    public enum Entrada {
        NINGUNA,
        NOMBRE,
        LORE
    }

    private int nivel;
    private int pagina;
    private Entrada esperando = Entrada.NINGUNA;

    public SesionItems(int nivelPorDefecto) {
        this.nivel = nivelPorDefecto;
    }

    public int nivel() {
        return nivel;
    }

    public void sumarNivel(int cantidad, int maximo) {
        nivel = Math.max(1, Math.min(maximo, nivel + cantidad));
    }

    public void nivel(int valor) {
        this.nivel = Math.max(1, valor);
    }

    public int pagina() {
        return pagina;
    }

    public void pagina(int valor) {
        this.pagina = Math.max(0, valor);
    }

    public Entrada esperando() {
        return esperando;
    }

    public void esperando(Entrada entrada) {
        this.esperando = entrada;
    }
}
