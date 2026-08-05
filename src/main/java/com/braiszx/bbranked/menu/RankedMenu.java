package com.braiszx.bbranked.menu;

import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.config.RankTier;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.PlayerStats;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.party.Party;
import com.braiszx.bbranked.party.PartyManager;
import com.braiszx.bbranked.queue.QueueManager;
import com.braiszx.bbranked.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Menu de cofre con los modos, tus estadisticas y tu party.
 */
public final class RankedMenu implements InventoryHolder {

    private static final int SIZE = 45;

    private final RankedConfig config;
    private final Messages messages;
    private final StatsManager stats;
    private final QueueManager queues;
    private final PartyManager parties;

    /** slot -> modo al que apunta ese icono. */
    private final Map<Integer, QueueMode> modeSlots = new LinkedHashMap<>();
    private Inventory inventory;
    private int leaveSlot = -1;

    public RankedMenu(RankedConfig config, Messages messages, StatsManager stats,
                      QueueManager queues, PartyManager parties) {
        this.config = config;
        this.messages = messages;
        this.stats = stats;
        this.queues = queues;
        this.parties = parties;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public QueueMode modeAt(int slot) {
        return modeSlots.get(slot);
    }

    public boolean isLeaveSlot(int slot) {
        return slot == leaveSlot;
    }

    /**
     * Construye y abre el menu.
     */
    public void open(Player player) {
        this.inventory = Bukkit.createInventory(this, SIZE, Messages.mini(config.menuTitle()));
        PlayerStats own = stats.getOrDefault(player);

        // Fila de modos, centrada.
        List<QueueMode> modes = new ArrayList<>(config.modes().values());
        if (modes.isEmpty()) {
            // Sin esto el menu sale medio vacio y no hay forma de saber por que.
            inventory.setItem(13, noModesIcon());
        }
        int start = Math.max(0, 10 + (7 - modes.size()) / 2);
        for (int i = 0; i < modes.size() && i < 7; i++) {
            QueueMode mode = modes.get(i);
            int slot = start + i;
            modeSlots.put(slot, mode);
            inventory.setItem(slot, modeIcon(mode));
        }

        inventory.setItem(31, statsIcon(player, own));

        if (queues.isQueued(player.getUniqueId())) {
            leaveSlot = 35;
            inventory.setItem(leaveSlot, leaveIcon());
        }

        if (config.partyEnabled()) {
            inventory.setItem(27, partyIcon(player));
        }

        player.openInventory(inventory);
    }

    private ItemStack modeIcon(QueueMode mode) {
        Material material = materialOr(config.menuIconFor(mode.id()), Material.LEATHER_BOOTS);
        ItemStack item = new ItemStack(material);

        List<Component> lore = new ArrayList<>();
        lore.add(plain("<gray>Equipos de <white>" + mode.teamSize() + "</white> jugadores."));
        lore.add(plain("<gray>En cola: <yellow>" + queues.size(mode) + "</yellow>/"
                + mode.requiredPlayers()));
        lore.add(Component.empty());
        lore.add(plain("<green>Click para entrar a la cola"));

        return named(item, "<gold><bold>" + mode.displayName(), lore);
    }

    private ItemStack statsIcon(Player player, PlayerStats own) {
        RankTier tier = config.rankOf(own.elo());
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        List<Component> lore = new ArrayList<>();
        lore.add(plain("<gray>Division: <reset>" + tier.displayName()));
        lore.add(plain("<gray>Elo: <white>" + own.elo() + "</white> <gray>(pico " + own.peakElo() + ")"));
        lore.add(plain("<gray>V/D/E: <green>" + own.wins() + "</green><gray>/<red>"
                + own.losses() + "</red><gray>/<yellow>" + own.draws()));
        lore.add(plain("<gray>Goles: <white>" + own.goals() + "</white> <gray>| MVPs: <gold>"
                + own.mvps()));

        int remaining = config.placementMatches() - own.matches();
        if (remaining > 0) {
            lore.add(Component.empty());
            lore.add(plain("<yellow>Colocacion: faltan " + remaining + " partidas"));
        }

        return named(item, "<yellow><bold>" + player.getName(), lore);
    }

    private ItemStack partyIcon(Player player) {
        Party party = parties.partyOf(player.getUniqueId());
        ItemStack item = new ItemStack(party == null ? Material.GRAY_DYE : Material.LIME_DYE);

        List<Component> lore = new ArrayList<>();
        if (party == null) {
            lore.add(plain("<gray>No estas en ninguna party."));
            lore.add(Component.empty());
            lore.add(plain("<gray>Usa <white>/ranked party invite <jugador>"));
        } else {
            for (java.util.UUID member : party.members()) {
                PlayerStats memberStats = stats.require(member);
                lore.add(plain("<gray> - " + (party.isLeader(member) ? "<gold>" : "<white>")
                        + memberStats.name() + " <gray>(" + memberStats.elo() + " Elo)"));
            }
        }
        return named(item, "<green><bold>Party", lore);
    }

    /**
     * Aviso de que no hay ningun modo cargado, con los pasos para arreglarlo.
     */
    private ItemStack noModesIcon() {
        List<Component> lore = List.of(
                plain("<gray>El plugin no ha cargado ningun modo."),
                Component.empty(),
                plain("<gray>Casi siempre es un error de formato en"),
                plain("<white>plugins/BlockBallRanked/config.yml</white><gray>:"),
                plain("<gray>si el YAML no se puede leer, se usan los"),
                plain("<gray>valores por defecto y la lista de modos"),
                plain("<gray>se queda vacia."),
                Component.empty(),
                plain("<yellow>1.</yellow> <gray>Mira la consola al arrancar."),
                plain("<yellow>2.</yellow> <gray>Comprueba la seccion <white>modes:</white>"),
                plain("<yellow>3.</yellow> <gray>Usa <white>/ranked check</white>"));
        return named(new ItemStack(Material.BARRIER), "<red><bold>Sin modos configurados", lore);
    }

    private ItemStack leaveIcon() {
        List<Component> lore = List.of(plain("<gray>Click para salir de la cola"));
        return named(new ItemStack(Material.BARRIER), "<red><bold>Salir de la cola", lore);
    }

    private ItemStack named(ItemStack item, String name, List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plain(name));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Los items llevan cursiva por defecto: se quita para que se lea limpio.
     */
    private static Component plain(String miniMessage) {
        return Messages.mini(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private static Material materialOr(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
