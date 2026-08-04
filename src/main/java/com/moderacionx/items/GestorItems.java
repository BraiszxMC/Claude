package com.moderacionx.items;

import com.moderacionx.ModeracionX;
import com.moderacionx.util.Ph;
import com.moderacionx.util.Texto;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Editor de items: encantamientos sin limite, nombre, lore, irrompible y flags. */
public final class GestorItems {

    public static final int POR_PAGINA = 45;

    // Menu principal (36 huecos)
    public static final int SLOT_VISTA = 4;
    public static final int SLOT_ENCANTAR = 19;
    public static final int SLOT_NOMBRE = 20;
    public static final int SLOT_LORE = 21;
    public static final int SLOT_LORE_LIMPIAR = 22;
    public static final int SLOT_IRROMPIBLE = 23;
    public static final int SLOT_FLAGS = 24;
    public static final int SLOT_DESENCANTAR = 25;
    public static final int SLOT_CERRAR = 31;

    // Menu de encantamientos (54 huecos)
    public static final int SLOT_ANTERIOR = 45;
    public static final int SLOT_VOLVER = 46;
    public static final int SLOT_NIVEL_MENOS = 48;
    public static final int SLOT_NIVEL_INFO = 49;
    public static final int SLOT_NIVEL_MAS = 50;
    public static final int SLOT_SIGUIENTE = 53;

    private final ModeracionX plugin;
    private final Map<UUID, SesionItems> sesiones = new ConcurrentHashMap<>();

    public GestorItems(ModeracionX plugin) {
        this.plugin = plugin;
    }

    public SesionItems sesion(Player jugador) {
        return sesiones.computeIfAbsent(jugador.getUniqueId(),
                clave -> new SesionItems(config().getInt("items.nivel-por-defecto", 255)));
    }

    /** Devuelve la sesion solo si ya existe, sin crearla. */
    public SesionItems sesionSiExiste(Player jugador) {
        return sesiones.get(jugador.getUniqueId());
    }

    public void olvidar(UUID uuid) {
        sesiones.remove(uuid);
    }

    private org.bukkit.configuration.file.FileConfiguration config() {
        return plugin.ajustes().raiz();
    }

    public int nivelMaximo() {
        return Math.max(1, config().getInt("items.nivel-maximo", 255));
    }

    // ---------------------------------------------------------- encantamientos

    /** Todos los encantamientos del servidor, ordenados por nombre interno. */
    public List<Enchantment> encantamientos() {
        List<Enchantment> lista = new ArrayList<>();
        for (Enchantment encantamiento : Registry.ENCHANTMENT) {
            lista.add(encantamiento);
        }
        lista.sort(Comparator.comparing(encantamiento -> encantamiento.getKey().getKey()));
        return lista;
    }

    public String nombreDe(Enchantment encantamiento) {
        String personalizado = config().getString("items.nombres." + encantamiento.getKey().getKey());
        if (personalizado != null && !personalizado.isBlank()) {
            return personalizado;
        }
        String clave = encantamiento.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(clave.charAt(0)) + clave.substring(1);
    }

    public Enchantment buscar(String nombre) {
        String limpio = nombre.toLowerCase(Locale.ROOT).replace(' ', '_');
        Enchantment directo = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(limpio));
        if (directo != null) {
            return directo;
        }
        for (Enchantment encantamiento : encantamientos()) {
            if (nombreDe(encantamiento).equalsIgnoreCase(nombre)) {
                return encantamiento;
            }
        }
        return null;
    }

    // -------------------------------------------------------------- pantallas

    public void abrirPrincipal(Player staff) {
        ItemStack enMano = staff.getInventory().getItemInMainHand();
        Inventory vista = crear(MenuItems.Pantalla.PRINCIPAL, 36, "items.titulo-principal", null);

        rellenar(vista, 0, 36);
        vista.setItem(SLOT_VISTA, enMano.getType().isAir()
                ? item(material("sin-item", Material.BARRIER), "item-sin-item", null)
                : enMano.clone());

        vista.setItem(SLOT_ENCANTAR, item(material("encantar", Material.ENCHANTED_BOOK), "item-encantar", null));
        vista.setItem(SLOT_NOMBRE, item(material("nombre", Material.NAME_TAG), "item-nombre", null));
        vista.setItem(SLOT_LORE, item(material("lore", Material.PAPER), "item-lore", null));
        vista.setItem(SLOT_LORE_LIMPIAR, item(material("lore-limpiar", Material.SHEARS), "item-lore-limpiar", null));

        boolean irrompible = enMano.hasItemMeta() && enMano.getItemMeta().isUnbreakable();
        vista.setItem(SLOT_IRROMPIBLE, item(material("irrompible", Material.ANVIL),
                irrompible ? "item-irrompible-si" : "item-irrompible-no", null));

        boolean ocultos = enMano.hasItemMeta() && !enMano.getItemMeta().getItemFlags().isEmpty();
        vista.setItem(SLOT_FLAGS, item(material("flags", Material.GLASS),
                ocultos ? "item-flags-si" : "item-flags-no", null));

        vista.setItem(SLOT_DESENCANTAR, item(material("desencantar", Material.GRINDSTONE), "item-desencantar", null));
        vista.setItem(SLOT_CERRAR, item(material("cerrar", Material.BARRIER), "item-cerrar", null));

        staff.openInventory(vista);
    }

    public void abrirEncantamientos(Player staff) {
        SesionItems sesion = sesion(staff);
        List<Enchantment> lista = encantamientos();
        int paginas = Math.max(1, (int) Math.ceil(lista.size() / (double) POR_PAGINA));
        int pagina = Math.min(sesion.pagina(), paginas - 1);
        sesion.pagina(pagina);

        Inventory vista = crear(MenuItems.Pantalla.ENCANTAMIENTOS, 54, "items.titulo-encantamientos", Ph.de()
                .con("pagina", pagina + 1)
                .con("paginas", paginas)
                .con("nivel", sesion.nivel()));

        ItemStack enMano = staff.getInventory().getItemInMainHand();
        for (int i = 0; i < POR_PAGINA; i++) {
            int indice = pagina * POR_PAGINA + i;
            if (indice >= lista.size()) {
                break;
            }
            vista.setItem(i, itemEncantamiento(lista.get(indice), enMano, sesion.nivel()));
        }

        rellenar(vista, 45, 54);
        if (pagina > 0) {
            vista.setItem(SLOT_ANTERIOR, item(material("anterior", Material.ARROW), "item-anterior", null));
        }
        if (pagina + 1 < paginas) {
            vista.setItem(SLOT_SIGUIENTE, item(material("siguiente", Material.ARROW), "item-siguiente", null));
        }
        vista.setItem(SLOT_VOLVER, item(material("volver", Material.OAK_DOOR), "item-volver", null));
        vista.setItem(SLOT_NIVEL_MENOS, item(material("menos", Material.RED_STAINED_GLASS_PANE), "item-menos",
                Ph.de("cantidad", config().getInt("items.incremento-corto", 1))));

        ItemStack info = item(material("nivel", Material.EXPERIENCE_BOTTLE), "item-nivel",
                Ph.de().con("nivel", sesion.nivel()).con("maximo", nivelMaximo()));
        info.setAmount(Math.max(1, Math.min(64, sesion.nivel())));
        vista.setItem(SLOT_NIVEL_INFO, info);

        vista.setItem(SLOT_NIVEL_MAS, item(material("mas", Material.LIME_STAINED_GLASS_PANE), "item-mas",
                Ph.de("cantidad", config().getInt("items.incremento-corto", 1))));

        staff.openInventory(vista);
    }

    // ----------------------------------------------------------------- acciones

    /** ¿Tiene algo en la mano? Si no, avisa. */
    public ItemStack enMano(Player staff) {
        ItemStack item = staff.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            plugin.mensajes().enviar(staff, "items.sin-item");
            return null;
        }
        return item;
    }

    /** Reenvia el inventario al cliente para que vea el item ya modificado. */
    private void refrescar(Player staff) {
        staff.updateInventory();
    }

    public void encantar(Player staff, Enchantment encantamiento, int nivel) {
        ItemStack item = enMano(staff);
        if (item == null) {
            return;
        }
        int aplicado = Math.max(1, Math.min(nivelMaximo(), nivel));
        item.addUnsafeEnchantment(encantamiento, aplicado);
        refrescar(staff);
        plugin.mensajes().enviar(staff, "items.encantado", Ph.de()
                .con("encantamiento", nombreDe(encantamiento))
                .con("nivel", aplicado));
    }

    public void quitarEncantamiento(Player staff, Enchantment encantamiento) {
        ItemStack item = enMano(staff);
        if (item == null) {
            return;
        }
        item.removeEnchantment(encantamiento);
        refrescar(staff);
        plugin.mensajes().enviar(staff, "items.encantamiento-quitado",
                Ph.de("encantamiento", nombreDe(encantamiento)));
    }

    public void quitarEncantamientos(Player staff) {
        ItemStack item = enMano(staff);
        if (item == null) {
            return;
        }
        for (Enchantment encantamiento : new ArrayList<>(item.getEnchantments().keySet())) {
            item.removeEnchantment(encantamiento);
        }
        refrescar(staff);
        plugin.mensajes().enviar(staff, "items.encantamientos-quitados");
    }

    public void renombrar(Player staff, String texto) {
        ItemStack item = enMano(staff);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (texto == null || texto.isBlank() || "quitar".equalsIgnoreCase(texto)) {
            meta.displayName(null);
            item.setItemMeta(meta);
            refrescar(staff);
            plugin.mensajes().enviar(staff, "items.nombre-quitado");
            return;
        }
        meta.displayName(Texto.item(texto, null));
        item.setItemMeta(meta);
        refrescar(staff);
        plugin.mensajes().enviar(staff, "items.nombre-puesto", Ph.de("texto", texto));
    }

    public void anadirLore(Player staff, String texto) {
        ItemStack item = enMano(staff);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lineas = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lineas.add(Texto.item(texto, null));
        meta.lore(lineas);
        item.setItemMeta(meta);
        refrescar(staff);
        plugin.mensajes().enviar(staff, "items.lore-anadido", Ph.de("texto", texto));
    }

    public void limpiarLore(Player staff) {
        ItemStack item = enMano(staff);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.lore(null);
        item.setItemMeta(meta);
        refrescar(staff);
        plugin.mensajes().enviar(staff, "items.lore-limpiado");
    }

    public void alternarIrrompible(Player staff) {
        ItemStack item = enMano(staff);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        boolean nuevo = !meta.isUnbreakable();
        meta.setUnbreakable(nuevo);
        item.setItemMeta(meta);
        refrescar(staff);
        plugin.mensajes().enviar(staff, nuevo ? "items.irrompible-si" : "items.irrompible-no");
    }

    public void alternarFlags(Player staff) {
        ItemStack item = enMano(staff);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (meta.getItemFlags().isEmpty()) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
            refrescar(staff);
            plugin.mensajes().enviar(staff, "items.flags-si");
            return;
        }
        meta.removeItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        refrescar(staff);
        plugin.mensajes().enviar(staff, "items.flags-no");
    }

    // -------------------------------------------------------------------- items

    private Inventory crear(MenuItems.Pantalla pantalla, int tamano, String rutaTitulo, Ph placeholders) {
        MenuItems holder = new MenuItems(pantalla);
        Inventory vista = Bukkit.createInventory(holder, tamano,
                Texto.comp(config().getString(rutaTitulo, "Editor de items"), placeholders));
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
        String nombre = config().getString("items.materiales." + clave);
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
            meta.displayName(Texto.item(plugin.mensajes().bruto("items." + ruta + ".nombre"), placeholders));
            meta.lore(lore("items." + ruta + ".lore", placeholders));
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

    private ItemStack itemEncantamiento(Enchantment encantamiento, ItemStack enMano, int nivel) {
        ItemStack item = new ItemStack(material("encantamiento", Material.ENCHANTED_BOOK));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int actual = enMano == null ? 0 : enMano.getEnchantmentLevel(encantamiento);
            Ph placeholders = Ph.de()
                    .con("encantamiento", nombreDe(encantamiento))
                    .con("clave", encantamiento.getKey().getKey())
                    .con("nivel", nivel)
                    .con("actual", actual == 0 ? plugin.mensajes().palabra("general.ninguna") : String.valueOf(actual))
                    .con("vanilla", encantamiento.getMaxLevel());
            meta.displayName(Texto.item(plugin.mensajes().bruto("items.item-encantamiento.nombre"), placeholders));
            meta.lore(lore("items.item-encantamiento.lore", placeholders));
            if (actual > 0) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
