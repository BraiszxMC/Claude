package com.moderacionx.almacen;

import com.moderacionx.espia.RegistroComando;
import com.moderacionx.sanciones.Sancion;
import com.moderacionx.sanciones.TipoSancion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Contrato de persistencia de ModeracionX. */
public interface Almacen {

    void inicializar() throws Exception;

    void cerrar();

    /** Nombre legible del backend (SQLite / YAML). */
    String nombre();

    /** Guarda la sancion y le asigna su id. */
    void insertar(Sancion sancion) throws Exception;

    /** Persiste los cambios de estado (retirada, caducidad). */
    void actualizar(Sancion sancion) throws Exception;

    List<Sancion> historial(UUID uuid) throws Exception;

    List<Sancion> historialPorNombre(String nombre) throws Exception;

    List<Sancion> historialPorIp(String ip) throws Exception;

    /** Todas las sanciones marcadas como activas (incluidas las ya caducadas por tiempo). */
    List<Sancion> activas() throws Exception;

    List<Sancion> activasDe(UUID uuid, TipoSancion tipo) throws Exception;

    int total() throws Exception;

    void guardarPerfil(Perfil perfil) throws Exception;

    Optional<Perfil> perfilPorUuid(UUID uuid) throws Exception;

    Optional<Perfil> perfilPorNombre(String nombre) throws Exception;

    // ------------------------------------------------- registro de comandos

    /** Guarda de golpe varios comandos ejecutados. */
    void registrarComandos(List<RegistroComando> registros) throws Exception;

    /**
     * Busca en el registro de comandos.
     *
     * @param texto   texto a buscar dentro del comando, o null para no filtrar
     * @param jugador uuid del autor, o null para todos
     * @param desde   solo comandos posteriores a esta fecha (0 = sin limite)
     */
    List<RegistroComando> buscarComandos(String texto, UUID jugador, long desde,
                                         int limite, int desplazamiento) throws Exception;

    int contarComandos(String texto, UUID jugador, long desde) throws Exception;

    /** Borra los comandos anteriores a esa fecha. Devuelve cuantos ha quitado. */
    int limpiarComandos(long antesDe) throws Exception;

    int totalComandos() throws Exception;
}
