package com.braiszx.bbranked.command;

import com.braiszx.bbranked.BlockBallRankedPlugin;
import com.braiszx.bbranked.ban.BanEntry;
import com.braiszx.bbranked.ban.BanManager;
import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.config.RankTier;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.LeaderboardEntry;
import com.braiszx.bbranked.data.PlayerStats;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.match.RankedMatch;
import com.braiszx.bbranked.queue.QueueManager;
import com.braiszx.bbranked.util.Messages;
import com.github.shynixn.blockball.contract.SoccerGame;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /ranked y sus subcomandos.
 */
public final class RankedCommand implements CommandExecutor, TabCompleter {

    private final BlockBallRankedPlugin plugin;
    private final RankedConfig config;
    private final Messages messages;
    private final StatsManager stats;
    private final QueueManager queues;
    private final MatchManager matches;
    private final BanManager bans;

    public RankedCommand(BlockBallRankedPlugin plugin, RankedConfig config, Messages messages,
                         StatsManager stats, QueueManager queues, MatchManager matches, BanManager bans) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.stats = stats;
        this.queues = queues;
        this.matches = matches;
        this.bans = bans;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join", "entrar" -> handleJoin(sender, args);
            case "leave", "salir" -> handleLeave(sender);
            case "stats", "elo" -> handleStats(sender, args);
            case "top", "ranking" -> handleTop(sender, args);
            case "queues", "colas" -> handleQueues(sender);
            case "reload" -> handleReload(sender);
            case "setelo" -> handleSetElo(sender, args);
            case "reset" -> handleReset(sender, args);
            case "check", "comprobar" -> handleCheck(sender);
            case "matches", "partidas" -> handleMatches(sender);
            case "forceend" -> handleForceEnd(sender, args);
            case "ban" -> handleBan(sender, args);
            case "unban", "desbanear" -> handleUnban(sender, args);
            case "banip" -> handleBanIp(sender, args);
            case "unbanip" -> handleUnbanIp(sender, args);
            case "bans", "baneos" -> handleBans(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    /**
     * Ayuda pensada para alguien que no ha jugado ranked nunca: explica que es
     * y por donde empezar. La parte de administracion solo la ve quien puede
     * usarla.
     */
    private void sendHelp(CommandSender sender) {
        Map<String, String> placeholders = Map.of(
                "modes", String.join(", ", config.modes().keySet()),
                "placements", String.valueOf(config.placementMatches()));

        for (Component line : messages.list("help", placeholders)) {
            sender.sendMessage(line);
        }
        if (sender.hasPermission("bbranked.admin")) {
            for (Component line : messages.list("help-admin", placeholders)) {
                sender.sendMessage(line);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Jugador
    // ------------------------------------------------------------------

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("bbranked.play")) {
            messages.send(sender, "no-permission");
            return;
        }

        QueueMode mode;
        if (args.length >= 2) {
            mode = config.mode(args[1]);
        } else if (config.modes().size() == 1) {
            mode = config.modes().values().iterator().next();
        } else {
            mode = null;
        }

        if (mode == null) {
            messages.send(sender, "unknown-mode", Map.of(
                    "mode", args.length >= 2 ? args[1] : "?",
                    "modes", String.join(", ", config.modes().keySet())));
            return;
        }
        queues.join(player, mode);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        queues.leave(player, true);
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.stats")) {
            messages.send(sender, "no-permission");
            return;
        }

        String target;
        if (args.length >= 2) {
            if (!sender.hasPermission("bbranked.stats.others")) {
                messages.send(sender, "no-permission");
                return;
            }
            target = args[1];
        } else if (sender instanceof Player player) {
            target = player.getName();
        } else {
            messages.send(sender, "players-only");
            return;
        }

        stats.lookup(target).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (found == null) {
                messages.send(sender, "stats.no-data");
                return;
            }
            printStats(sender, found);
        }));
    }

    private void printStats(CommandSender sender, PlayerStats found) {
        RankTier tier = config.rankOf(found.elo());
        messages.sendRaw(sender, "stats.header", Map.of("player", found.name()));
        messages.sendRaw(sender, "stats.rank", Map.of("rank", tier.displayName()));
        messages.sendRaw(sender, "stats.elo", Map.of(
                "elo", String.valueOf(found.elo()),
                "peak", String.valueOf(found.peakElo())));
        messages.sendRaw(sender, "stats.record", Map.of(
                "wins", String.valueOf(found.wins()),
                "losses", String.valueOf(found.losses()),
                "draws", String.valueOf(found.draws()),
                "winrate", String.format(Locale.ROOT, "%.1f", found.winRate())));
        messages.sendRaw(sender, "stats.matches", Map.of(
                "matches", String.valueOf(found.matches()),
                "goals", String.valueOf(found.goals())));
        messages.sendRaw(sender, "stats.streak", Map.of(
                "streak", String.valueOf(found.winStreak()),
                "best", String.valueOf(found.bestStreak())));
        messages.sendRaw(sender, "stats.leaves", Map.of("leaves", String.valueOf(found.leaves())));

        int remaining = config.placementMatches() - found.matches();
        if (remaining > 0) {
            messages.sendRaw(sender, "stats.placements", Map.of("remaining", String.valueOf(remaining)));
        }
    }

    private void handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.stats")) {
            messages.send(sender, "no-permission");
            return;
        }

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException exception) {
                messages.send(sender, "invalid-number", Map.of("input", args[1]));
                return;
            }
        }

        // Se consulta la base de datos directamente en vez de usar la cache:
        // la cache se rellena de forma asincrona y el primer /ranked top tras
        // arrancar la encontraba vacia, asi que no salia nadie en el ranking.
        int requestedPage = page;
        stats.storage().topPlayers(config.leaderboardPageSize() * 20, config.leaderboardMinMatches())
                .thenAccept(entries -> Bukkit.getScheduler().runTask(plugin,
                        () -> renderTop(sender, entries, requestedPage)));
    }

    private void renderTop(CommandSender sender, List<LeaderboardEntry> entries, int page) {
        if (entries.isEmpty()) {
            messages.sendRaw(sender, "top.empty");
            return;
        }

        int pageSize = config.leaderboardPageSize();
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        page = Math.min(page, pages);
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, entries.size());

        messages.sendRaw(sender, "top.header", Map.of(
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)));
        for (int i = from; i < to; i++) {
            LeaderboardEntry entry = entries.get(i);
            messages.sendRaw(sender, "top.line", Map.of(
                    "position", String.valueOf(i + 1),
                    "player", entry.name(),
                    "elo", String.valueOf(entry.elo()),
                    "rank", config.rankOf(entry.elo()).displayName()));
        }
    }

    private void handleQueues(CommandSender sender) {
        Map<String, Integer> sizes = queues.sizes();
        boolean any = sizes.values().stream().anyMatch(size -> size > 0);
        messages.sendRaw(sender, "queue.status-header");
        if (!any) {
            messages.sendRaw(sender, "queue.status-empty");
            return;
        }
        sizes.forEach((mode, size) -> messages.sendRaw(sender, "queue.status-line", Map.of(
                "mode", mode, "count", String.valueOf(size))));
    }

    // ------------------------------------------------------------------
    //  Administracion
    // ------------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        plugin.reloadEverything();
        messages.send(sender, "reloaded");
        // Recargar suele ir acompanado de cambios en las arenas, asi que se
        // revisan de paso.
        handleCheck(sender);
    }

    /**
     * Revisa que las arenas del config existan y esten bien montadas.
     */
    private void handleCheck(CommandSender sender) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }

        List<String> problems = matches.validateArenas();
        if (problems.isEmpty()) {
            messages.send(sender, "admin.check-ok");
            return;
        }

        messages.send(sender, "admin.check-header");
        for (String problem : problems) {
            messages.sendRaw(sender, "admin.check-line", Map.of("problem", problem));
        }
    }

    private void handleSetElo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }

        int value;
        try {
            value = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            messages.send(sender, "invalid-number", Map.of("input", args[2]));
            return;
        }

        String name = args[1];
        stats.lookup(name).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (found == null) {
                messages.send(sender, "player-not-found", Map.of("player", name));
                return;
            }
            found.elo(Math.max(config.minimumElo(), value));
            stats.save(found);
            stats.refreshLeaderboard();
            messages.send(sender, "admin.elo-set", Map.of(
                    "player", found.name(), "elo", String.valueOf(found.elo())));
        }));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        String name = args[1];
        stats.lookup(name).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (found == null) {
                messages.send(sender, "player-not-found", Map.of("player", name));
                return;
            }
            found.elo(config.startingElo());
            found.peakElo(config.startingElo());
            found.wins(0);
            found.losses(0);
            found.draws(0);
            found.goals(0);
            found.leaves(0);
            found.bestStreak(0);
            found.winStreak(0);
            stats.save(found);
            stats.refreshLeaderboard();
            messages.send(sender, "admin.reset", Map.of("player", found.name()));
        }));
    }

    private void handleMatches(CommandSender sender) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        Map<RankedMatch, SoccerGame> snapshot = matches.snapshot();
        messages.sendRaw(sender, "admin.matches-header");
        if (snapshot.isEmpty()) {
            messages.sendRaw(sender, "admin.matches-empty");
            return;
        }
        snapshot.forEach((match, game) -> messages.sendRaw(sender, "admin.matches-line", Map.of(
                "arena", match.arenaName(),
                "mode", match.mode().displayName(),
                "red", String.valueOf(match.redScore()),
                "blue", String.valueOf(match.blueScore()),
                "seconds", String.valueOf(match.ageSeconds()))));
    }

    private void handleForceEnd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", Map.of("player", args[1]));
            return;
        }
        RankedMatch match = matches.matchOf(target.getUniqueId());
        if (match == null) {
            messages.send(sender, "admin.no-match");
            return;
        }
        matches.abort(match, true);
        messages.send(sender, "admin.match-ended");
    }

    // ------------------------------------------------------------------
    //  Baneos
    // ------------------------------------------------------------------

    /**
     * /ranked ban &lt;jugador&gt; [tiempo] [motivo]
     */
    private void handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        String name = args[1];
        long parsed = args.length >= 3 ? BanManager.parseDuration(args[2]) : -1;
        // Si el tercer argumento no es una duracion, se toma como parte del
        // motivo y el baneo es permanente.
        int reasonFrom = parsed == Long.MIN_VALUE ? 2 : 3;
        long duration = parsed == Long.MIN_VALUE ? -1 : parsed;
        String reason = joinFrom(args, reasonFrom, "Sin motivo");

        resolvePlayer(name, resolved -> {
            if (resolved == null) {
                messages.send(sender, "player-not-found", Map.of("player", name));
                return;
            }

            BanEntry entry = bans.banPlayer(resolved.uuid(), resolved.name(), reason, duration, sender.getName());

            // Si estaba en cola, se le saca ya.
            Player online = Bukkit.getPlayer(resolved.uuid());
            if (online != null && queues.remove(online.getUniqueId())) {
                messages.send(online, "admin.kicked-from-queue");
            }

            messages.send(sender, "admin.ban-added", Map.of(
                    "player", resolved.name(), "expires", entry.remainingText(), "reason", reason));
        });
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        String name = args[1];
        resolvePlayer(name, resolved -> {
            if (resolved == null) {
                messages.send(sender, "player-not-found", Map.of("player", name));
                return;
            }
            messages.send(sender, bans.unbanPlayer(resolved.uuid())
                    ? "admin.ban-removed" : "admin.ban-not-found", Map.of("player", resolved.name()));
        });
    }

    /**
     * /ranked banip &lt;jugador|ip&gt; [tiempo] [motivo]
     */
    private void handleBanIp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        String input = args[1];
        long duration = args.length >= 3 ? BanManager.parseDuration(args[2]) : -1;
        int reasonFrom = duration == Long.MIN_VALUE ? 2 : 3;
        if (duration == Long.MIN_VALUE) {
            duration = -1;
        }
        String reason = joinFrom(args, reasonFrom, "Sin motivo");

        // Se acepta tanto una IP directa como el nombre de alguien conectado.
        String ip = input;
        if (!looksLikeIp(input)) {
            Player online = Bukkit.getPlayerExact(input);
            if (online == null) {
                messages.send(sender, "admin.ip-unknown", Map.of("player", input));
                return;
            }
            ip = BanManager.ipOf(online);
            if (ip == null) {
                messages.send(sender, "admin.ip-unknown", Map.of("player", input));
                return;
            }
        }

        BanEntry entry = bans.banIp(ip, reason, duration, sender.getName());

        // Saca de la cola a todos los que esten usando esa IP.
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (ip.equalsIgnoreCase(BanManager.ipOf(online)) && queues.remove(online.getUniqueId())) {
                messages.send(online, "admin.kicked-from-queue");
            }
        }

        messages.send(sender, "admin.ipban-added", Map.of(
                "ip", ip, "expires", entry.remainingText(), "reason", reason));
    }

    private void handleUnbanIp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        messages.send(sender, bans.unbanIp(args[1])
                ? "admin.ipban-removed" : "admin.ipban-not-found", Map.of("ip", args[1]));
    }

    private void handleBans(CommandSender sender) {
        if (!sender.hasPermission("bbranked.admin")) {
            messages.send(sender, "no-permission");
            return;
        }

        List<BanEntry> active = bans.activeBans();
        messages.sendRaw(sender, "admin.bans-header");
        if (active.isEmpty()) {
            messages.sendRaw(sender, "admin.bans-empty");
            return;
        }
        for (BanEntry entry : active) {
            messages.sendRaw(sender, "admin.bans-line", Map.of(
                    "target", entry.label(),
                    "type", entry.type() == BanEntry.BanType.IP ? "IP" : "jugador",
                    "expires", entry.remainingText(),
                    "reason", entry.reason()));
        }
    }

    /**
     * Resultado de buscar a un jugador por nombre.
     */
    private record ResolvedPlayer(UUID uuid, String name) {
    }

    /**
     * Busca a un jugador por nombre, este conectado o no, y llama a
     * {@code callback} en el hilo principal.
     *
     * <p>Primero mira entre los conectados, luego la cache de Bukkit y por
     * ultimo la base de datos del propio plugin (cualquiera que haya jugado
     * una ranked esta ahi). Asi se puede banear a alguien desconectado sin
     * inventarse UUIDs.</p>
     */
    private void resolvePlayer(String name, java.util.function.Consumer<ResolvedPlayer> callback) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            callback.accept(new ResolvedPlayer(online.getUniqueId(), online.getName()));
            return;
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getName() != null) {
            callback.accept(new ResolvedPlayer(cached.getUniqueId(), cached.getName()));
            return;
        }

        stats.lookup(name).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () ->
                callback.accept(found == null ? null : new ResolvedPlayer(found.uuid(), found.name()))));
    }

    private static boolean looksLikeIp(String input) {
        return input.matches("^[0-9.]+$") || input.contains(":");
    }

    private static String joinFrom(String[] args, int from, String fallback) {
        if (from >= args.length) {
            return fallback;
        }
        return String.join(" ", List.of(args).subList(from, args.length));
    }

    // ------------------------------------------------------------------
    //  Autocompletado
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.addAll(List.of("join", "leave", "stats", "top", "queues"));
            if (sender.hasPermission("bbranked.admin")) {
                options.addAll(List.of("reload", "check", "setelo", "reset", "matches", "forceend",
                        "ban", "unban", "banip", "unbanip", "bans"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join", "entrar" -> options.addAll(config.modes().keySet());
                case "stats", "elo", "setelo", "reset", "forceend", "ban", "unban", "banip" ->
                        Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
                case "unbanip" -> bans.activeBans().stream()
                        .filter(entry -> entry.type() == BanEntry.BanType.IP)
                        .forEach(entry -> options.add(entry.target()));
                default -> {
                    // sin sugerencias
                }
            }
        } else if (args.length == 3
                && List.of("ban", "banip").contains(args[0].toLowerCase(Locale.ROOT))) {
            options.addAll(List.of("perm", "30m", "1h", "6h", "1d", "7d"));
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
