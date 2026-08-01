package com.braiszx.bbranked.ban;

import com.braiszx.bbranked.data.StatsStorage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baneos del ranked, por jugador y por IP.
 *
 * <p>Se cargan enteros en memoria al arrancar. Son pocos y hay que
 * consultarlos en sitios donde no se puede esperar a la base de datos (al
 * entrar en cola, al entrar a una arena), asi que la cache es la fuente de
 * verdad y la base de datos solo persiste.</p>
 */
public final class BanManager {

    private static final Pattern DURATION = Pattern.compile("(?i)^(\\d+)\\s*([smhdw])$");

    private final StatsStorage storage;

    private final Map<UUID, BanEntry> playerBans = new ConcurrentHashMap<>();
    private final Map<String, BanEntry> ipBans = new ConcurrentHashMap<>();

    public BanManager(StatsStorage storage) {
        this.storage = storage;
    }

    /**
     * Carga los baneos de la base de datos. Bloquea a proposito: se llama en
     * onEnable, antes de que nadie pueda entrar en cola.
     */
    public void load() {
        playerBans.clear();
        ipBans.clear();
        for (BanEntry entry : storage.loadBansBlocking()) {
            if (entry.expired()) {
                storage.deleteBan(entry.target(), entry.type());
                continue;
            }
            if (entry.type() == BanEntry.BanType.PLAYER) {
                try {
                    playerBans.put(UUID.fromString(entry.target()), entry);
                } catch (IllegalArgumentException ignored) {
                    // fila corrupta, se ignora
                }
            } else {
                ipBans.put(entry.target().toLowerCase(Locale.ROOT), entry);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Consultas
    // ------------------------------------------------------------------

    /**
     * Baneo activo del jugador, o null. Los caducados se limpian solos.
     */
    public BanEntry playerBan(UUID uuid) {
        BanEntry entry = playerBans.get(uuid);
        if (entry == null) {
            return null;
        }
        if (entry.expired()) {
            playerBans.remove(uuid);
            storage.deleteBan(entry.target(), entry.type());
            return null;
        }
        return entry;
    }

    /**
     * Baneo activo de la IP, o null.
     */
    public BanEntry ipBan(String ip) {
        if (ip == null) {
            return null;
        }
        String key = ip.toLowerCase(Locale.ROOT);
        BanEntry entry = ipBans.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired()) {
            ipBans.remove(key);
            storage.deleteBan(entry.target(), entry.type());
            return null;
        }
        return entry;
    }

    /**
     * Cualquier baneo que afecte a este jugador (suyo o de su IP).
     */
    public BanEntry activeBanFor(Player player) {
        BanEntry entry = playerBan(player.getUniqueId());
        if (entry != null) {
            return entry;
        }
        return ipBan(ipOf(player));
    }

    public List<BanEntry> activeBans() {
        List<BanEntry> all = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(playerBans.keySet())) {
            BanEntry entry = playerBan(uuid);
            if (entry != null) {
                all.add(entry);
            }
        }
        for (String ip : new ArrayList<>(ipBans.keySet())) {
            BanEntry entry = ipBan(ip);
            if (entry != null) {
                all.add(entry);
            }
        }
        return all;
    }

    // ------------------------------------------------------------------
    //  Altas y bajas
    // ------------------------------------------------------------------

    public BanEntry banPlayer(UUID uuid, String name, String reason, long durationMillis, String bannedBy) {
        long expiresAt = durationMillis < 0 ? -1 : System.currentTimeMillis() + durationMillis;
        BanEntry entry = new BanEntry(uuid.toString(), name, BanEntry.BanType.PLAYER, reason,
                expiresAt, bannedBy, System.currentTimeMillis());
        playerBans.put(uuid, entry);
        storage.saveBan(entry);
        return entry;
    }

    public boolean unbanPlayer(UUID uuid) {
        BanEntry removed = playerBans.remove(uuid);
        if (removed == null) {
            return false;
        }
        storage.deleteBan(removed.target(), removed.type());
        return true;
    }

    public BanEntry banIp(String ip, String reason, long durationMillis, String bannedBy) {
        String key = ip.toLowerCase(Locale.ROOT);
        long expiresAt = durationMillis < 0 ? -1 : System.currentTimeMillis() + durationMillis;
        BanEntry entry = new BanEntry(key, key, BanEntry.BanType.IP, reason,
                expiresAt, bannedBy, System.currentTimeMillis());
        ipBans.put(key, entry);
        storage.saveBan(entry);
        return entry;
    }

    public boolean unbanIp(String ip) {
        BanEntry removed = ipBans.remove(ip.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        storage.deleteBan(removed.target(), removed.type());
        return true;
    }

    // ------------------------------------------------------------------
    //  Utilidades
    // ------------------------------------------------------------------

    /**
     * IP del jugador, o null si no se puede leer.
     */
    public static String ipOf(Player player) {
        if (player == null || player.getAddress() == null || player.getAddress().getAddress() == null) {
            return null;
        }
        return player.getAddress().getAddress().getHostAddress();
    }

    /**
     * Convierte "30m", "2h", "7d", "1w" o "perm" a milisegundos.
     *
     * @return los milisegundos, -1 si es permanente, o {@link Long#MIN_VALUE}
     *         si el texto no es una duracion valida
     */
    public static long parseDuration(String input) {
        if (input == null) {
            return -1;
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.equals("perm") || value.equals("permanent") || value.equals("permanente")) {
            return -1;
        }

        Matcher matcher = DURATION.matcher(value);
        if (!matcher.matches()) {
            return Long.MIN_VALUE;
        }

        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "s" -> amount * 1000L;
            case "m" -> amount * 60_000L;
            case "h" -> amount * 3_600_000L;
            case "d" -> amount * 86_400_000L;
            case "w" -> amount * 604_800_000L;
            default -> Long.MIN_VALUE;
        };
    }

    /**
     * Formato legible de una duracion en milisegundos.
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return Math.max(1, seconds) + "s";
    }
}
