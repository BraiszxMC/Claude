package com.moderacionx.efectos;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import com.moderacionx.util.Tiempo;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Construye y maneja el menu de /efectos. */
public final class GestorEfectos {

    public static final int POR_PAGINA = 45;

    // Menu de efectos y de jugadores (54 huecos)
    public static final int SLOT_ANTERIOR = 45;
    public static final int SLOT_VOLVER = 46;
    public static final int SLOT_QUITAR_TODOS = 47;
    public static final int SLOT_CERRAR = 49;
    public static final int SLOT_CONFIRMAR = 50;
    public static final int SLOT_SIGUIENTE = 53;

    // Menu de objetivos (27 huecos)
    public static final int SLOT_UNO = 11;
    public static final int SLOT_VARIOS = 13;
    public static final int SLOT_TODOS = 15;
    public static final int SLOT_OBJETIVOS_VOLVER = 22;

    // Menu de ajustes (45 huecos)
    public static final int SLOT_DUR_MENOS_LARGO = 10;
    public static final int SLOT_DUR_MENOS_CORTO = 11;
    public static final int SLOT_DUR_INFO = 13;
    public static final int SLOT_DUR_MAS_CORTO = 15;
    public static final int SLOT_DUR_MAS_LARGO = 16;
    public static final int SLOT_NIVEL_MENOS = 20;
    public static final int SLOT_NIVEL_INFO = 22;
    public static final int SLOT_NIVEL_MAS = 24;
    public static final int SLOT_PARTICULAS = 29;
    public static final int SLOT_PERMANENTE = 31;
    public static final int SLOT_INFO_OBJETIVOS = 33;
    public static final int SLOT_AJUSTES_VOLVER = 36;
    public static final int SLOT_APLICAR = 40;
    public static final int SLOT_AJUSTES_CERRAR = 44;

    private final ModeracionX plugin;
    private final Map<UUID, SesionEfectos> sesiones = new ConcurrentHashMap<>();

    public GestorEfectos(ModeracionX plugin) {
        this.plugin = plugin;
    }

    public SesionEfectos sesion(Player jugador) {
        return sesiones.computeIfAbsent(jugador.getUniqueId(), clave -> new SesionEfectos(
                config().getInt("efectos.duracion-por-defecto", 30),
                config().getInt("efectos.nivel-por-defecto", 1),
                config().getBoolean("efectos.particulas-por-defecto", true)));
    }

    public SesionEfectos sesionSiExiste(Player jugador) {
        return sesiones.get(jugador.getUniqueId());
    }

    public void olvidar(UUID uuid) {
        sesiones.remove(uuid);
    }

    private org.bukkit.configuration.file.FileConfiguration config() {
        return plugin.ajustes().raiz();
    }

    // ------------------------------------------------------- lista de efectos

    /** Todos los efectos del servidor, ordenados por nombre interno. */
    public List<PotionEffectType> tipos() {
        List<PotionEffectType> lista = new ArrayList<>();
        for (PotionEffectType tipo : Registry.EFFECT) {
            lista.add(tipo);
        }
        lista.sort(Comparator.comparing(tipo -> tipo.getKey().getKey()));
        return lista;
    }

    public String nombreDe(PotionEffectType tipo) {
        String personalizado = config().getString("efectos.nombres." + tipo.getKey().getKey());
        if (personalizado != null && !personalizado.isBlank()) {
            return personalizado;
        }
        String clave = tipo.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(clave.charAt(0)) + clave.substring(1);
    }

    // -------------------------------------------------------------- pantallas

    public void abrirEfectos(Player staff) {
        SesionEfectos sesion = sesion(staff);
        List<PotionEffectType> tipos = tipos();
        int paginas = Math.max(1, (int) Math.ceil(tipos.size() / (double) POR_PAGINA));
        int pagina = Math.min(sesion.paginaEfectos(), paginas - 1);
        sesion.paginaEfectos(pagina);

        Inventory vista = crear(MenuEfectos.Pantalla.EFECTOS, 54, "efectos.titulo-efectos", Ph.de()
                .con("pagina", pagina + 1)
                .con("paginas", paginas));

        for (int i = 0; i < POR_PAGINA; i++) {
            int indice = pagina * POR_PAGINA + i;
            if (indice >= tipos.size()) {
                break;
            }
            vista.setItem(i, itemEfecto(tipos.get(indice)));
        }

        rellenar(vista, 45, 54);
        if (pagina > 0) {
            vista.setItem(SLOT_ANTERIOR, item(material("anterior", Material.ARROW), "item-anterior", null));
        }
        if (pagina + 1 < paginas) {
            vista.setItem(SLOT_SIGUIENTE, item(material("siguiente", Material.ARROW), "item-siguiente", null));
        }
        vista.setItem(SLOT_QUITAR_TODOS, item(material("quitar-todos", Material.MILK_BUCKET), "item-quitar-todos", null));
        vista.setItem(SLOT_CERRAR, item(material("cerrar", Material.BARRIER), "item-cerrar", null));

        staff.openInventory(vista);
    }

    public void abrirObjetivos(Player staff) {
        SesionEfectos sesion = sesion(staff);
        Inventory vista = crear(MenuEfectos.Pantalla.OBJETIVOS, 27, "efectos.titulo-objetivos",
                Ph.de("efecto", descripcionAccion(sesion)));

        rellenar(vista, 0, 27);
        vista.setItem(SLOT_UNO, item(material("uno", Material.PLAYER_HEAD), "item-uno", null));
        vista.setItem(SLOT_VARIOS, item(material("varios", Material.CHEST), "item-varios", null));
        vista.setItem(SLOT_TODOS, item(material("todos", Material.BEACON), "item-todos", null));
        vista.setItem(SLOT_OBJETIVOS_VOLVER, item(material("volver", Material.OAK_DOOR), "item-volver", null));

        staff.openInventory(vista);
    }

    /** Jugadores que el staff puede ver, en el mismo orden que se pintan en el menu. */
    public List<Player> visibles(Player staff) {
        List<Player> conectados = new ArrayList<>();
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            if (staff.canSee(jugador)) {
                conectados.add(jugador);
            }
        }
        conectados.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return conectados;
    }

    public void abrirJugadores(Player staff) {
        SesionEfectos sesion = sesion(staff);
        List<Player> conectados = visibles(staff);

        int paginas = Math.max(1, (int) Math.ceil(conectados.size() / (double) POR_PAGINA));
        int pagina = Math.min(sesion.paginaJugadores(), paginas - 1);
        sesion.paginaJugadores(pagina);

        Inventory vista = crear(MenuEfectos.Pantalla.JUGADORES, 54, "efectos.titulo-jugadores", Ph.de()
                .con("pagina", pagina + 1)
                .con("paginas", paginas));

        for (int i = 0; i < POR_PAGINA; i++) {
            int indice = pagina * POR_PAGINA + i;
            if (indice >= conectados.size()) {
                break;
            }
            Player objetivo = conectados.get(indice);
            vista.setItem(i, itemJugador(objetivo, sesion.objetivos().contains(objetivo.getUniqueId())));
        }

        rellenar(vista, 45, 54);
        if (pagina > 0) {
            vista.setItem(SLOT_ANTERIOR, item(material("anterior", Material.ARROW), "item-anterior", null));
        }
        if (pagina + 1 < paginas) {
            vista.setItem(SLOT_SIGUIENTE, item(material("siguiente", Material.ARROW), "item-siguiente", null));
        }
        vista.setItem(SLOT_VOLVER, item(material("volver", Material.OAK_DOOR), "item-volver", null));
        vista.setItem(SLOT_CERRAR, item(material("cerrar", Material.BARRIER), "item-cerrar", null));
        if (sesion.seleccion() == SesionEfectos.Seleccion.VARIOS) {
            vista.setItem(SLOT_CONFIRMAR, item(material("confirmar", Material.LIME_DYE), "item-confirmar",
                    Ph.de("cantidad", sesion.objetivos().size())));
        }

        staff.openInventory(vista);
    }

    public void abrirAjustes(Player staff) {
        SesionEfectos sesion = sesion(staff);
        Inventory vista = crear(MenuEfectos.Pantalla.AJUSTES, 45, "efectos.titulo-ajustes",
                Ph.de("efecto", descripcionAccion(sesion)));

        rellenar(vista, 0, 45);

        int corto = config().getInt("efectos.incremento-corto", 10);
        int largo = config().getInt("efectos.incremento-largo", 60);

        vista.setItem(SLOT_DUR_MENOS_LARGO, item(material("menos", Material.RED_STAINED_GLASS_PANE), "item-menos",
                Ph.de("cantidad", Tiempo.formatear(largo * 1000L, plugin.mensajes()))));
        vista.setItem(SLOT_DUR_MENOS_CORTO, item(material("menos", Material.RED_STAINED_GLASS_PANE), "item-menos",
                Ph.de("cantidad", Tiempo.formatear(corto * 1000L, plugin.mensajes()))));
        vista.setItem(SLOT_DUR_INFO, item(material("duracion", Material.CLOCK), "item-duracion",
                Ph.de("duracion", sesion.permanente()
                        ? plugin.mensajes().palabra("general.permanente")
                        : Tiempo.formatear(sesion.duracion() * 1000L, plugin.mensajes()))));
        vista.setItem(SLOT_DUR_MAS_CORTO, item(material("mas", Material.LIME_STAINED_GLASS_PANE), "item-mas",
                Ph.de("cantidad", Tiempo.formatear(corto * 1000L, plugin.mensajes()))));
        vista.setItem(SLOT_DUR_MAS_LARGO, item(material("mas", Material.LIME_STAINED_GLASS_PANE), "item-mas",
                Ph.de("cantidad", Tiempo.formatear(largo * 1000L, plugin.mensajes()))));

        vista.setItem(SLOT_NIVEL_MENOS, item(material("menos", Material.RED_STAINED_GLASS_PANE), "item-menos",
                Ph.de("cantidad", "1")));
        ItemStack infoNivel = item(material("nivel", Material.GLOWSTONE_DUST), "item-nivel", Ph.de()
                .con("nivel", sesion.nivel())
                .con("maximo", config().getInt("efectos.nivel-maximo", 255)));
        infoNivel.setAmount(Math.max(1, Math.min(64, sesion.nivel())));
        vista.setItem(SLOT_NIVEL_INFO, infoNivel);
        vista.setItem(SLOT_NIVEL_MAS, item(material("mas", Material.LIME_STAINED_GLASS_PANE), "item-mas",
                Ph.de("cantidad", "1")));

        vista.setItem(SLOT_PARTICULAS, item(
                sesion.particulas() ? material("si", Material.GLOWSTONE) : material("no", Material.GUNPOWDER),
                sesion.particulas() ? "item-particulas-si" : "item-particulas-no", null));

        if (config().getBoolean("efectos.permitir-permanente", true)) {
            vista.setItem(SLOT_PERMANENTE, item(
                    sesion.permanente() ? material("si", Material.NETHER_STAR) : material("no", Material.CLOCK),
                    sesion.permanente() ? "item-permanente-si" : "item-permanente-no", null));
        }

        vista.setItem(SLOT_INFO_OBJETIVOS, item(material("objetivos", Material.PAPER), "item-objetivos",
                Ph.de("objetivos", resumenObjetivos(staff, sesion))));

        vista.setItem(SLOT_AJUSTES_VOLVER, item(material("volver", Material.OAK_DOOR), "item-volver", null));
        vista.setItem(SLOT_APLICAR, item(material("aplicar", Material.EMERALD_BLOCK), "item-aplicar", null));
        vista.setItem(SLOT_AJUSTES_CERRAR, item(material("cerrar", Material.BARRIER), "item-cerrar", null));

        staff.openInventory(vista);
    }

    // ---------------------------------------------------------------- aplicar

    /** Aplica o quita el efecto a los objetivos elegidos. */
    public void aplicar(Player staff) {
        SesionEfectos sesion = sesion(staff);
        List<Player> objetivos = objetivos(staff, sesion);
        if (objetivos.isEmpty()) {
            plugin.mensajes().enviar(staff, "efectos.sin-objetivos");
            return;
        }

        switch (sesion.modo()) {
            case QUITAR_TODOS -> {
                for (Player objetivo : objetivos) {
                    for (PotionEffect activo : new ArrayList<>(objetivo.getActivePotionEffects())) {
                        objetivo.removePotionEffect(activo.getType());
                    }
                    plugin.mensajes().enviar(objetivo, "efectos.notificar-limpiado",
                            Ph.de("autor", staff.getName()));
                }
                plugin.mensajes().enviar(staff, "efectos.limpiado", Ph.de()
                        .con("cantidad", objetivos.size())
                        .con("objetivos", nombres(objetivos)));
            }
            case QUITAR -> {
                if (sesion.efecto() == null) {
                    return;
                }
                for (Player objetivo : objetivos) {
                    objetivo.removePotionEffect(sesion.efecto());
                }
                plugin.mensajes().enviar(staff, "efectos.quitado", Ph.de()
                        .con("efecto", nombreDe(sesion.efecto()))
                        .con("cantidad", objetivos.size())
                        .con("objetivos", nombres(objetivos)));
            }
            default -> {
                if (sesion.efecto() == null) {
                    return;
                }
                int ticks = sesion.permanente() ? PotionEffect.INFINITE_DURATION : sesion.duracion() * 20;
                PotionEffect efecto = new PotionEffect(sesion.efecto(), ticks, sesion.nivel() - 1,
                        false, sesion.particulas(), sesion.particulas());
                for (Player objetivo : objetivos) {
                    objetivo.addPotionEffect(efecto);
                    plugin.mensajes().enviar(objetivo, "efectos.notificar", Ph.de()
                            .con("efecto", nombreDe(sesion.efecto()))
                            .con("nivel", sesion.nivel())
                            .con("autor", staff.getName())
                            .con("duracion", sesion.permanente()
                                    ? plugin.mensajes().palabra("general.permanente")
                                    : Tiempo.formatear(sesion.duracion() * 1000L, plugin.mensajes())));
                }
                plugin.mensajes().enviar(staff, "efectos.aplicado", Ph.de()
                        .con("efecto", nombreDe(sesion.efecto()))
                        .con("nivel", sesion.nivel())
                        .con("cantidad", objetivos.size())
                        .con("objetivos", nombres(objetivos))
                        .con("duracion", sesion.permanente()
                                ? plugin.mensajes().palabra("general.permanente")
                                : Tiempo.formatear(sesion.duracion() * 1000L, plugin.mensajes())));
            }
        }

        if (config().getBoolean("efectos.cerrar-al-aplicar", true)) {
            staff.closeInventory();
        }
    }

    public List<Player> objetivos(Player staff, SesionEfectos sesion) {
        List<Player> lista = new ArrayList<>();
        if (sesion.seleccion() == SesionEfectos.Seleccion.TODOS) {
            lista.addAll(Bukkit.getOnlinePlayers());
            return lista;
        }
        for (UUID uuid : sesion.objetivos()) {
            Player jugador = Bukkit.getPlayer(uuid);
            if (jugador != null) {
                lista.add(jugador);
            }
        }
        return lista;
    }

    private String nombres(List<Player> jugadores) {
        int maximo = config().getInt("efectos.nombres-mostrados", 5);
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < jugadores.size() && i < maximo; i++) {
            nombres.add(jugadores.get(i).getName());
        }
        String texto = String.join(", ", nombres);
        if (jugadores.size() > maximo) {
            texto = texto + " (+" + (jugadores.size() - maximo) + ")";
        }
        return texto;
    }

    private String resumenObjetivos(Player staff, SesionEfectos sesion) {
        if (sesion.seleccion() == SesionEfectos.Seleccion.TODOS) {
            return plugin.mensajes().palabra("efectos.palabra-todos") + " (" + Bukkit.getOnlinePlayers().size() + ")";
        }
        List<Player> objetivos = objetivos(staff, sesion);
        return objetivos.isEmpty() ? plugin.mensajes().palabra("general.ninguna") : nombres(objetivos);
    }

    private String descripcionAccion(SesionEfectos sesion) {
        if (sesion.modo() == SesionEfectos.Modo.QUITAR_TODOS) {
            return plugin.mensajes().palabra("efectos.palabra-limpiar");
        }
        if (sesion.efecto() == null) {
            return "-";
        }
        String nombre = nombreDe(sesion.efecto());
        return sesion.modo() == SesionEfectos.Modo.QUITAR
                ? plugin.mensajes().palabra("efectos.palabra-quitar") + " " + nombre
                : nombre;
    }

    // ----------------------------------------------------------------- items

    private Inventory crear(MenuEfectos.Pantalla pantalla, int tamano, String rutaTitulo, Ph placeholders) {
        MenuEfectos holder = new MenuEfectos(pantalla);
        Inventory vista = Bukkit.createInventory(holder, tamano,
                Texto.comp(config().getString(rutaTitulo, "Efectos"), placeholders));
        holder.inventario(vista);
        return vista;
    }

    private void rellenar(Inventory vista, int desde, int hasta) {
        Material material = material("relleno", Material.GRAY_STAINED_GLASS_PANE);
        if (material == Material.AIR) {
            return;
        }
        ItemStack relleno = new ItemStack(material);
        ItemMeta meta = relleno.getItemMeta();
        if (meta != null) {
            meta.displayName(Texto.item(" ", null));
            relleno.setItemMeta(meta);
        }
        for (int i = desde; i < hasta; i++) {
            if (vista.getItem(i) == null) {
                vista.setItem(i, relleno);
            }
        }
    }

    private Material material(String clave, Material porDefecto) {
        String nombre = config().getString("efectos.materiales." + clave);
        if (nombre == null || nombre.isBlank()) {
            return porDefecto;
        }
        Material material = Material.matchMaterial(nombre.toUpperCase(Locale.ROOT));
        return material == null ? porDefecto : material;
    }

    private ItemStack item(Material material, String ruta, Ph placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Texto.item(plugin.mensajes().bruto("efectos." + ruta + ".nombre"), placeholders));
            meta.lore(lore("efectos." + ruta + ".lore", placeholders));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Component> lore(String ruta, Ph placeholders) {
        List<Component> lineas = new ArrayList<>();
        for (String linea : plugin.mensajes().brutoLista(ruta)) {
            lineas.add(Texto.item(linea, placeholders));
        }
        return lineas;
    }

    private ItemStack itemEfecto(PotionEffectType tipo) {
        Material material = material("efecto-" + tipo.getKey().getKey(), null);
        ItemStack item = new ItemStack(material == null ? Material.POTION : material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof PotionMeta pocion && tipo.getColor() != null) {
            pocion.setColor(tipo.getColor());
        }
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            Ph placeholders = Ph.de()
                    .con("efecto", nombreDe(tipo))
                    .con("clave", tipo.getKey().getKey());
            meta.displayName(Texto.item(plugin.mensajes().bruto("efectos.item-efecto.nombre"), placeholders));
            meta.lore(lore("efectos.item-efecto.lore", placeholders));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack itemJugador(Player objetivo, boolean seleccionado) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta calavera) {
            calavera.setOwningPlayer(objetivo);
        }
        if (meta != null) {
            Ph placeholders = Ph.de()
                    .con("jugador", objetivo.getName())
                    .con("vida", String.format("%.1f", objetivo.getHealth()))
                    .con("mundo", objetivo.getWorld().getName())
                    .crudo("estado", plugin.mensajes().bruto(seleccionado
                            ? "efectos.estado-seleccionado" : "efectos.estado-no-seleccionado"));
            meta.displayName(Texto.item(plugin.mensajes().bruto("efectos.item-jugador.nombre"), placeholders));
            meta.lore(lore("efectos.item-jugador.lore", placeholders));
            if (seleccionado) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
