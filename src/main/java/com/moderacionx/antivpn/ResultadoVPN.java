package com.moderacionx.antivpn;

/**
 * Respuesta de una comprobacion anti-VPN.
 *
 * @param valido    true si algun proveedor respondio correctamente
 * @param vpn       true si la IP es una VPN o un proxy
 * @param hosting   true si la IP pertenece a un datacenter
 * @param proveedor nombre del proveedor que respondio
 */
public record ResultadoVPN(boolean valido, boolean vpn, boolean hosting, String proveedor) {

    public static ResultadoVPN sinDatos() {
        return new ResultadoVPN(false, false, false, "-");
    }

    public static ResultadoVPN limpia(String proveedor) {
        return new ResultadoVPN(true, false, false, proveedor);
    }

    /** ¿Hay que bloquear con la configuracion actual? */
    public boolean bloquear(boolean bloquearHosting) {
        return valido && (vpn || (bloquearHosting && hosting));
    }
}
