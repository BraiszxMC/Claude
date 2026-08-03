package com.braiszx.bbranked.match;

import com.braiszx.bbranked.BlockBallRankedPlugin;
import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.config.RankTier;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.PlayerStats;
import com.braiszx.bbranked.data.StatsManager;
import com.braiszx.bbranked.elo.EloChange;
import com.braiszx.bbranked.elo.EloCalculator;
import com.braiszx.bbranked.elo.MatchWinner;
import com.braiszx.bbranked.util.Messages;
import com.braiszx.bbranked.util.Sounds;
import com.github.shynixn.blockball.contract.GameService;
import com.github.shynixn.blockball.contract.SoccerGame;
import com.github.shynixn.blockball.enumeration.GameState;
import com.github.shynixn.blockball.enumeration.GameType;
import com.github.shynixn.blockball.enumeration.JoinResult;
import com.github.shynixn.blockball.enumeration.Team;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Arranca, vigila y liquida las partidas ranked.
 */
public final class MatchManager {

    private final BlockBallRankedPlugin plugin;
    private final RankedConfig config;
    private final Messages messages;
    private final StatsManager stats;
    private final EloCalculator elo;
    private final Sounds sounds;

    /** Partidas vivas, por id. */
    private final Map<UUID, RankedMatch> matches = new ConcurrentHashMap<>();
    /** Instancia de BlockBall asociada a cada partida. */
    private final Map<UUID, SoccerGame> games = new ConcurrentHashMap<>();
    /** Jugador -> partida en la que esta. */
    private final Map<UUID, RankedMatch> byPlayer = new ConcurrentHashMap<>();
    /** Arenas ocupadas por el ranked (no se liberan hasta que BlockBall las reinicia). */
    private final Set<String> reservedArenas = ConcurrentHashMap.newKeySet();
    /** Jugadores a los que el plugin esta metiendo ahora mismo en una arena ranked. */
    private final Set<UUID> authorizedJoins = ConcurrentHashMap.newKeySet();

    public MatchManager(BlockBallRankedPlugin plugin, RankedConfig config, Messages messages,
                        StatsManager stats, EloCalculator elo, Sounds sounds) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.stats = stats;
        this.elo = elo;
        this.sounds = sounds;
    }

    // ------------------------------------------------------------------
    //  Consultas
    // ------------------------------------------------------------------

    public RankedMatch matchOf(UUID uuid) {
        return byPlayer.get(uuid);
    }

    public boolean isInMatch(UUID uuid) {
        return byPlayer.containsKey(uuid);
    }

    public Collection<RankedMatch> activeMatches() {
        return matches.values();
    }

    public boolean isAuthorizedJoin(UUID uuid) {
        return authorizedJoins.contains(uuid);
    }

    /**
     * True si la arena esta reservada para el ranked en el config.
     */
    public boolean isRankedArena(String arenaName) {
        for (QueueMode mode : config.modes().values()) {
            for (String arena : mode.arenas()) {
                if (arena.equalsIgnoreCase(arenaName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cuenta cuantas arenas de un modo estan libres ahora mismo.
     */
    public int freeArenas(QueueMode mode) {
        int count = 0;
        for (String arena : mode.arenas()) {
            if (findFreeGame(arena) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Revisa que las arenas del config existan de verdad en BlockBall y esten
     * bien configuradas. Devuelve una linea por cada problema encontrado.
     *
     * <p>Sin esto, un servidor mal configurado deja a la gente en una cola que
     * nunca puede arrancar nada y sin ningun aviso.</p>
     */
    public List<String> validateArenas() {
        List<String> problems = new ArrayList<>();
        GameService service = plugin.gameService();

        if (service == null) {
            problems.add("No se ha podido acceder al GameService de BlockBall.");
            return problems;
        }

        for (QueueMode mode : config.modes().values()) {
            if (mode.arenas().isEmpty()) {
                problems.add("El modo '" + mode.id() + "' no tiene ninguna arena asignada.");
                continue;
            }

            for (String arenaName : mode.arenas()) {
                SoccerGame game = service.getByName(arenaName);

                if (game == null) {
                    problems.add("Arena '" + arenaName + "' (modo " + mode.id() + "): no existe en BlockBall"
                            + " o esta desactivada. Comprueba /blockball list.");
                    continue;
                }

                if (game.getArena().getGameType() != GameType.MINIGAME) {
                    problems.add("Arena '" + arenaName + "' (modo " + mode.id() + "): es de tipo "
                            + game.getArena().getGameType() + " y tiene que ser MINIGAME."
                            + " Usa /blockball gamerule gameType " + arenaName + " minigame");
                }

                int red = game.getArena().getMeta().getRedTeamMeta().getMaxAmount();
                int blue = game.getArena().getMeta().getBlueTeamMeta().getMaxAmount();
                if (red != mode.teamSize() || blue != mode.teamSize()) {
                    problems.add("Arena '" + arenaName + "' (modo " + mode.id() + "): maxAmount es "
                            + red + "v" + blue + " pero el modo es " + mode.teamSize() + "v" + mode.teamSize()
                            + ". Ajusta meta.redTeamMeta/blueTeamMeta en"
                            + " plugins/BlockBall/arena/" + arenaName + ".yml");
                }
            }
        }
        return problems;
    }

    // ------------------------------------------------------------------
    //  Arranque de partidas
    // ------------------------------------------------------------------

    private SoccerGame findFreeGame(String arenaName) {
        if (reservedArenas.contains(arenaName.toLowerCase(Locale.ROOT))) {
            return null;
        }
        GameService service = plugin.gameService();
        if (service == null) {
            return null;
        }
        SoccerGame game = service.getByName(arenaName);
        if (game == null || game.isDisposed()) {
            return null;
        }
        if (game.getStatus() != GameState.JOINABLE || !game.getPlayers().isEmpty()) {
            return null;
        }
        return game;
    }

    /**
     * Intenta arrancar una partida con los jugadores dados.
     *
     * <p>BlockBall marca <code>GameJoinEvent</code>/<code>GameLeaveEvent</code>
     * como eventos asincronos (<code>BlockBallEvent(true)</code> en su
     * codigo), y Bukkit lanza <code>IllegalStateException</code> si se
     * disparan desde el hilo principal ("... may only be triggered
     * asynchronously"). Por eso la parte que llama a {@code game.join()} se
     * ejecuta fuera del hilo principal, y solo se vuelve a el para tocar
     * nuestro propio estado y mandar mensajes.</p>
     *
     * <p>Los equipos vienen ya repartidos desde la cola, que es quien sabe
     * que jugadores van juntos en una party y no se pueden separar.</p>
     *
     * @param onJoinFailure se ejecuta en el hilo principal si al final no se
     *                      ha podido meter a todo el mundo en la arena
     * @return true si se ha lanzado el intento (reservando la arena); no
     *         implica que la partida vaya a arrancar, para eso esta {@code onJoinFailure}
     */
    public boolean startMatch(QueueMode mode, List<Player> redPlayers, List<Player> bluePlayers,
                              Runnable onJoinFailure) {
        if (redPlayers.size() != mode.teamSize() || bluePlayers.size() != mode.teamSize()) {
            return false;
        }

        List<Player> players = new ArrayList<>(redPlayers);
        players.addAll(bluePlayers);

        String arenaName = null;
        SoccerGame game = null;
        for (String candidate : mode.arenas()) {
            SoccerGame found = findFreeGame(candidate);
            if (found != null) {
                arenaName = candidate;
                game = found;
                break;
            }
        }
        if (game == null) {
            return false;
        }

        reservedArenas.add(arenaName.toLowerCase(Locale.ROOT));
        for (Player player : players) {
            authorizedJoins.add(player.getUniqueId());
        }

        String finalArenaName = arenaName;
        SoccerGame finalGame = game;
        offPrimaryThread(() -> performJoins(mode, finalArenaName, finalGame, redPlayers, bluePlayers,
                players, onJoinFailure));
        return true;
    }

    /**
     * Mete a todos los jugadores en la arena. Se ejecuta fuera del hilo
     * principal (ver {@link #startMatch}).
     */
    private void performJoins(QueueMode mode, String arenaName, SoccerGame game, List<Player> redPlayers,
                              List<Player> bluePlayers, List<Player> players, Runnable onJoinFailure) {
        List<UUID> actualRed = new ArrayList<>();
        List<UUID> actualBlue = new ArrayList<>();
        List<Player> joined = new ArrayList<>();
        boolean ok = true;
        try {
            // Se intercalan rojo y azul a proposito: con la opcion
            // 'onlyAllowEventTeams' activada, BlockBall reasigna el equipo si
            // uno tiene mas jugadores que el otro en ese momento.
            for (int i = 0; i < mode.teamSize() && ok; i++) {
                ok = join(game, redPlayers.get(i), Team.RED, joined, actualRed, actualBlue)
                        && join(game, bluePlayers.get(i), Team.BLUE, joined, actualRed, actualBlue);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Error metiendo jugadores en la arena " + arenaName + ": "
                    + exception.getMessage());
            ok = false;
        }

        if (!ok) {
            for (Player player : joined) {
                try {
                    game.leave(player);
                } catch (RuntimeException ignored) {
                    // la arena puede haber cambiado de estado, nada que hacer
                }
            }
        }

        boolean succeeded = ok;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : players) {
                authorizedJoins.remove(player.getUniqueId());
            }

            if (!succeeded) {
                reservedArenas.remove(arenaName.toLowerCase(Locale.ROOT));
                for (Player player : players) {
                    messages.send(player, "match.cancelled");
                }
                if (onJoinFailure != null) {
                    onJoinFailure.run();
                }
                return;
            }

            RankedMatch match = new RankedMatch(mode, arenaName, actualRed, actualBlue);
            matches.put(match.id(), match);
            games.put(match.id(), game);
            for (UUID uuid : match.allPlayers()) {
                byPlayer.put(uuid, match);
            }

            Map<String, String> placeholders = Map.of("arena", arenaName, "mode", mode.displayName());
            for (Player player : players) {
                messages.send(player, "match.found", placeholders);
                sounds.play(player, "match-found");
            }
            sendMatchupComparison(match);
        });
    }

    /**
     * Mete a un jugador en la arena y apunta el equipo en el que ha acabado
     * de verdad, que no tiene por que ser el pedido.
     */
    private boolean join(SoccerGame game, Player player, Team team, List<Player> joined,
                         List<UUID> actualRed, List<UUID> actualBlue) {
        JoinResult result = game.join(player, team);

        if (result == JoinResult.SUCCESS_RED) {
            actualRed.add(player.getUniqueId());
        } else if (result == JoinResult.SUCCESS_BLUE) {
            actualBlue.add(player.getUniqueId());
        } else {
            plugin.getLogger().warning("BlockBall ha rechazado la entrada de " + player.getName()
                    + " en " + game.getArena().getName() + ": " + result);
            return false;
        }

        joined.add(player);
        return true;
    }

    /**
     * Manda a cada jugador un mensaje con el Elo y el rango de su(s) rival(es),
     * y debajo el suyo, para que pueda comparar de un vistazo.
     */
    private void sendMatchupComparison(RankedMatch match) {
        for (UUID uuid : match.allPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }

            Team team = match.teamOf(uuid);
            List<UUID> rivals = team == Team.RED ? match.blue() : match.red();
            List<UUID> allies = team == Team.RED ? match.red() : match.blue();
            PlayerStats own = stats.getOrDefault(player);
            RankTier ownRank = config.rankOf(own.elo());

            messages.sendRaw(player, "matchup.header");

            messages.sendRaw(player, rivals.size() > 1 ? "matchup.rivals-title" : "matchup.rival-title");
            for (UUID rivalUuid : rivals) {
                sendMatchupLine(player, "matchup.line", rivalUuid);
            }

            // En modos por equipos hace falta ver tambien el Elo de los
            // companeros, no solo el propio.
            boolean hasAllies = allies.size() > 1;
            messages.sendRaw(player, hasAllies ? "matchup.team-title" : "matchup.you-title");
            messages.sendRaw(player, "matchup.line-self", Map.of(
                    "player", own.name(),
                    "elo", String.valueOf(own.elo()),
                    "rank", ownRank.displayName(),
                    "ping", pingOf(uuid)));
            for (UUID allyUuid : allies) {
                if (!allyUuid.equals(uuid)) {
                    sendMatchupLine(player, "matchup.line", allyUuid);
                }
            }

            messages.sendRaw(player, "matchup.footer");
        }
    }

    private void sendMatchupLine(Player to, String key, UUID subject) {
        PlayerStats subjectStats = stats.cached(subject);
        if (subjectStats == null) {
            return;
        }
        messages.sendRaw(to, key, Map.of(
                "player", subjectStats.name(),
                "elo", String.valueOf(subjectStats.elo()),
                "rank", config.rankOf(subjectStats.elo()).displayName(),
                "ping", pingOf(subject)));
    }

    /**
     * Aviso de MVP en la barra de accion. Se repite unos segundos para que no
     * se lo coman los mensajes del resultado.
     */
    private void showMvpActionbar(Player player, int totalMvps) {
        if (!config.actionbarMvpEnabled()) {
            return;
        }
        Component text = messages.raw("actionbar.mvp", Map.of("mvps", String.valueOf(totalMvps)));
        UUID uuid = player.getUniqueId();

        for (int second = 0; second < config.actionbarMvpSeconds(); second++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.sendActionBar(text);
                }
            }, second * 20L);
        }
    }

    /**
     * Ping del jugador en milisegundos, o "?" si ya no esta conectado.
     */
    private String pingOf(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "?" : String.valueOf(player.getPing());
    }

    /**
     * Ejecuta {@code action} fuera del hilo principal. BlockBall exige que
     * quien llame a {@code join}/{@code leave}/{@code close} no este en el
     * hilo principal, porque sus eventos son asincronos.
     */
    private void offPrimaryThread(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, action);
        } else {
            action.run();
        }
    }


    // ------------------------------------------------------------------
    //  Eventos de BlockBall
    // ------------------------------------------------------------------

    public void onGameStarted(SoccerGame game) {
        RankedMatch match = matchOfGame(game);
        if (match != null) {
            match.started(true);
            broadcast(match, messages.get("match.starting",
                    Map.of("mode", match.mode().displayName())));
            sounds.play(match.allPlayers(), "match-start");
        }
    }

    public void onGoal(SoccerGame game, Player scorer) {
        RankedMatch match = matchOfGame(game);
        if (match == null || scorer == null) {
            return;
        }
        match.addGoal(scorer.getUniqueId());
        sounds.play(match.allPlayers(), "goal");
    }

    public void onGameEnd(SoccerGame game, Team winningTeam) {
        RankedMatch match = matchOfGame(game);
        if (match == null || match.settled()) {
            return;
        }
        match.updateScore(game.getRedScore(), game.getBlueScore());
        match.pendingWinner(winningTeam == Team.RED ? MatchWinner.RED
                : winningTeam == Team.BLUE ? MatchWinner.BLUE : match.winnerFromScore());
        // La liquidacion se hace en el siguiente tick del monitor, para no
        // trabajar dentro del propio evento de BlockBall.
    }

    /**
     * Un jugador ha salido de la partida (comando, kick o desconexion).
     */
    public void onPlayerLeft(UUID uuid) {
        RankedMatch match = byPlayer.get(uuid);
        if (match == null || match.settled()) {
            return;
        }
        if (!match.started()) {
            // Todavia en el lobby: se cancela la partida sin tocar el Elo.
            abort(match, true);
            return;
        }

        match.addLeaver(uuid);
        Player player = Bukkit.getPlayer(uuid);
        String name = player != null ? player.getName() : "Un jugador";
        broadcast(match, messages.get("match.leaver-forfeit", Map.of("player", name)));

        if (config.forfeitOnLeave()) {
            Team team = match.teamOf(uuid);
            match.pendingWinner(team == Team.RED ? MatchWinner.BLUE : MatchWinner.RED);
            // BlockBall no sabe que la partida ha terminado: el marcador no ha
            // llegado al maximo ni se ha acabado el tiempo. Si no se marca
            // como forfeit, se aplica el Elo pero los que quedan se quedan
            // jugando solos en la arena para siempre.
            match.forfeited(true);
        }
    }

    private RankedMatch matchOfGame(SoccerGame game) {
        for (Map.Entry<UUID, SoccerGame> entry : games.entrySet()) {
            if (entry.getValue() == game) {
                return matches.get(entry.getKey());
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Monitor
    // ------------------------------------------------------------------

    /**
     * Se ejecuta cada segundo en el hilo principal.
     */
    public void tick() {
        for (RankedMatch match : new ArrayList<>(matches.values())) {
            SoccerGame game = games.get(match.id());

            if (game == null) {
                cleanup(match);
                continue;
            }

            if (!match.settled() && !game.isDisposed()) {
                match.updateScore(game.getRedScore(), game.getBlueScore());
            }

            if (match.settled()) {
                // Ya liquidada: solo falta que BlockBall reinicie la arena.
                if (game.isDisposed()) {
                    cleanup(match);
                }
                continue;
            }

            if (match.pendingWinner() != null) {
                settle(match, match.pendingWinner());
                continue;
            }

            if (game.isDisposed()) {
                // Termino sin GameEndEvent: pasa en los empates por tiempo.
                if (match.started()) {
                    settle(match, match.winnerFromScore());
                } else {
                    abort(match, false);
                }
                continue;
            }

            if (!match.started() && match.ageSeconds() > config.startTimeoutSeconds()) {
                plugin.getLogger().warning("La partida ranked en " + match.arenaName()
                        + " no ha arrancado en " + config.startTimeoutSeconds() + "s, se cancela.");
                abort(match, true);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Liquidacion
    // ------------------------------------------------------------------

    private void settle(RankedMatch match, MatchWinner winner) {
        match.settled(true);
        match.pendingWinner(winner);

        List<PlayerStats> red = collectStats(match.red());
        List<PlayerStats> blue = collectStats(match.blue());

        // Los goles se apuntan aunque la partida acabe en abandono.
        for (Map.Entry<UUID, Integer> entry : match.goals().entrySet()) {
            PlayerStats scorer = stats.cached(entry.getKey());
            if (scorer != null) {
                scorer.addGoals(entry.getValue());
            }
        }

        Map<UUID, RankTier> before = new HashMap<>();
        for (PlayerStats playerStats : red) {
            before.put(playerStats.uuid(), config.rankOf(playerStats.elo()));
        }
        for (PlayerStats playerStats : blue) {
            before.put(playerStats.uuid(), config.rankOf(playerStats.elo()));
        }

        List<EloChange> changes = elo.apply(red, blue, match.redScore(), match.blueScore(),
                winner, new HashSet<>(match.leavers()));

        // El MVP se aplica encima del Elo ya calculado, para que el mensaje
        // final muestre el total real.
        Set<UUID> mvps = resolveMvps(match);
        int mvpGoals = mvps.isEmpty() ? 0 : match.goals().getOrDefault(mvps.iterator().next(), 0);
        if (!mvps.isEmpty()) {
            List<EloChange> adjusted = new ArrayList<>(changes.size());
            for (EloChange change : changes) {
                if (mvps.contains(change.uuid())) {
                    PlayerStats mvpStats = stats.require(change.uuid());
                    mvpStats.addMvp();
                    adjusted.add(elo.applyMvpBonus(mvpStats, change, config.mvpBonus()));
                } else {
                    adjusted.add(change);
                }
            }
            changes = adjusted;
        }

        for (EloChange change : changes) {
            sendResult(match, change, before.get(change.uuid()), winner, mvps, mvpGoals);
        }

        List<PlayerStats> all = new ArrayList<>(red);
        all.addAll(blue);
        stats.storage().saveAll(all);
        stats.refreshLeaderboard();

        persistHistory(match, winner, changes);

        if (config.broadcastResults()) {
            broadcastResult(match, winner);
        }

        // Los jugadores ya pueden volver a la cola aunque la arena tarde un
        // poco en reiniciarse.
        for (UUID uuid : match.allPlayers()) {
            byPlayer.remove(uuid);
        }

        // Cuando la partida acaba por abandono, BlockBall sigue creyendo que
        // esta en juego: hay que cerrarle la arena o los que quedan se quedan
        // dentro jugando. En un final normal no se toca, que ya se cierra sola
        // pasados unos segundos con sus mensajes de victoria.
        if (match.forfeited()) {
            SoccerGame game = games.get(match.id());
            if (game != null && !game.isDisposed()) {
                offPrimaryThread(() -> {
                    try {
                        game.close();
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("Error cerrando la arena " + match.arenaName()
                                + " tras un abandono: " + exception.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Quien se lleva el MVP: el que mas goles haya marcado en la partida.
     *
     * <p>Solo se da en modos por equipos (en un 1v1 no aporta nada) y solo si
     * alguien ha marcado. Si varios empatan a goles, se lo llevan todos o
     * ninguno segun {@code elo.mvp.share-on-tie}.</p>
     */
    private Set<UUID> resolveMvps(RankedMatch match) {
        if (!config.mvpEnabled() || match.mode().teamSize() < config.mvpMinTeamSize()) {
            return Set.of();
        }

        int best = 0;
        for (int goals : match.goals().values()) {
            best = Math.max(best, goals);
        }
        if (best <= 0) {
            return Set.of();
        }

        Set<UUID> mvps = new HashSet<>();
        for (Map.Entry<UUID, Integer> entry : match.goals().entrySet()) {
            // Un abandono no se lleva el MVP aunque fuera el maximo goleador.
            if (entry.getValue() == best && !match.leavers().contains(entry.getKey())) {
                mvps.add(entry.getKey());
            }
        }

        if (mvps.size() > 1 && !config.mvpShareOnTie()) {
            return Set.of();
        }
        return mvps;
    }

    private void sendResult(RankedMatch match, EloChange change, RankTier previousRank, MatchWinner winner,
                            Set<UUID> mvps, int mvpGoals) {
        Player player = Bukkit.getPlayer(change.uuid());
        if (player == null) {
            return;
        }

        messages.sendRaw(player, "result.header");
        messages.sendRaw(player, "result.score", Map.of(
                "red", String.valueOf(match.redScore()),
                "blue", String.valueOf(match.blueScore())));

        String outcomeKey = change.outcome() > 0 ? "result.win"
                : change.outcome() < 0 ? "result.loss" : "result.draw";
        messages.sendRaw(player, outcomeKey);
        sounds.play(player, change.outcome() > 0 ? "victory" : change.outcome() < 0 ? "defeat" : "draw");

        int delta = change.delta();
        if (delta > 0) {
            messages.sendRaw(player, "result.elo-gain", Map.of(
                    "old", String.valueOf(change.before()),
                    "new", String.valueOf(change.after()),
                    "delta", String.valueOf(delta)));
        } else if (delta < 0) {
            messages.sendRaw(player, "result.elo-loss", Map.of(
                    "old", String.valueOf(change.before()),
                    "new", String.valueOf(change.after()),
                    "delta", String.valueOf(delta)));
        } else {
            messages.sendRaw(player, "result.elo-same", Map.of("new", String.valueOf(change.after())));
        }

        if (!mvps.isEmpty()) {
            if (mvps.contains(change.uuid())) {
                int total = stats.require(change.uuid()).mvps();
                messages.sendRaw(player, "result.mvp-self", Map.of(
                        "goals", String.valueOf(mvpGoals),
                        "bonus", String.valueOf(config.mvpBonus()),
                        "mvps", String.valueOf(total)));
                sounds.play(player, "mvp");
                showMvpActionbar(player, total);
            } else {
                messages.sendRaw(player, "result.mvp-other", Map.of(
                        "players", names(new ArrayList<>(mvps)),
                        "goals", String.valueOf(mvpGoals)));
            }
        }

        RankTier newRank = config.rankOf(change.after());
        if (previousRank != null && !newRank.id().equals(previousRank.id())) {
            boolean promoted = newRank.minElo() > previousRank.minElo();
            messages.sendRaw(player, promoted ? "result.rank-up" : "result.rank-down",
                    Map.of("rank", newRank.displayName()));
            sounds.play(player, promoted ? "rank-up" : "rank-down");
        }

        messages.sendRaw(player, "result.footer");
    }

    private void broadcastResult(RankedMatch match, MatchWinner winner) {
        if (winner == MatchWinner.DRAW) {
            return;
        }
        List<UUID> winners = winner == MatchWinner.RED ? match.red() : match.blue();
        List<UUID> losers = winner == MatchWinner.RED ? match.blue() : match.red();
        Bukkit.broadcast(messages.get("result.broadcast", Map.of(
                "winners", names(winners),
                "losers", names(losers),
                "red", String.valueOf(match.redScore()),
                "blue", String.valueOf(match.blueScore()),
                "mode", match.mode().displayName())));
    }

    private void persistHistory(RankedMatch match, MatchWinner winner, List<EloChange> changes) {
        StringJoiner eloChanges = new StringJoiner(",");
        for (EloChange change : changes) {
            eloChanges.add(change.uuid() + ":" + change.before() + ">" + change.after());
        }
        stats.storage().recordMatch(
                match.mode().id(),
                match.arenaName(),
                match.redScore(),
                match.blueScore(),
                winner.name(),
                match.red().stream().map(UUID::toString).collect(Collectors.joining(",")),
                match.blue().stream().map(UUID::toString).collect(Collectors.joining(",")),
                eloChanges.toString());
    }

    private List<PlayerStats> collectStats(List<UUID> uuids) {
        List<PlayerStats> result = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            result.add(stats.require(uuid));
        }
        return result;
    }

    /**
     * Cancela una partida sin tocar el Elo de nadie.
     */
    public void abort(RankedMatch match, boolean closeGame) {
        if (match.settled()) {
            return;
        }
        match.settled(true);

        broadcast(match, messages.get("match.aborted"));

        if (closeGame) {
            SoccerGame game = games.get(match.id());
            if (game != null && !game.isDisposed()) {
                // close() dispara GameLeaveEvent por cada jugador, y ese
                // evento es asincrono: no se puede llamar desde el hilo
                // principal (ver startMatch).
                offPrimaryThread(() -> {
                    try {
                        game.close();
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("Error cerrando la arena " + match.arenaName()
                                + ": " + exception.getMessage());
                    }
                });
            }
        }

        for (UUID uuid : match.allPlayers()) {
            byPlayer.remove(uuid);
        }
    }

    private void cleanup(RankedMatch match) {
        matches.remove(match.id());
        games.remove(match.id());
        reservedArenas.remove(match.arenaName().toLowerCase(Locale.ROOT));
        for (UUID uuid : match.allPlayers()) {
            byPlayer.remove(uuid, match);
            stats.unloadIfOffline(uuid);
        }
    }

    private void broadcast(RankedMatch match, Component component) {
        for (UUID uuid : match.allPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(component);
            }
        }
    }

    private String names(List<UUID> uuids) {
        StringJoiner joiner = new StringJoiner(", ");
        for (UUID uuid : uuids) {
            PlayerStats playerStats = stats.cached(uuid);
            joiner.add(playerStats != null ? playerStats.name() : uuid.toString().substring(0, 8));
        }
        return joiner.toString();
    }

    /**
     * Cierra todas las partidas ranked, por ejemplo al apagar el servidor.
     *
     * <p>No puede usar {@link #abort} tal cual: ese metodo lanza el cierre
     * de la arena con {@code runTaskAsynchronously} sin esperar a que
     * termine, y durante el apagado el servidor puede cancelar esa tarea
     * antes de que llegue a ejecutarse. Aqui se cierra en un hilo aparte y se
     * espera (con limite) a que acabe antes de que <code>onDisable</code>
     * continue.</p>
     */
    public void shutdown() {
        List<SoccerGame> toClose = new ArrayList<>();
        for (RankedMatch match : new ArrayList<>(matches.values())) {
            if (!match.settled()) {
                match.settled(true);
                broadcast(match, messages.get("match.aborted"));
            }
            SoccerGame game = games.get(match.id());
            if (game != null && !game.isDisposed()) {
                toClose.add(game);
            }
            for (UUID uuid : match.allPlayers()) {
                byPlayer.remove(uuid);
            }
        }
        matches.clear();
        games.clear();
        byPlayer.clear();
        reservedArenas.clear();

        if (toClose.isEmpty()) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            for (SoccerGame game : toClose) {
                try {
                    if (!game.isDisposed()) {
                        game.close();
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Error cerrando una arena ranked al apagar: "
                            + exception.getMessage());
                }
            }
            latch.countDown();
        }, "BlockBallRanked-Shutdown");
        thread.setDaemon(true);
        thread.start();

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("El plugin se esta apagando sin esperar a que todas las"
                        + " arenas ranked terminen de cerrarse.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Detalle de las partidas activas para /ranked matches.
     */
    public Map<RankedMatch, SoccerGame> snapshot() {
        Map<RankedMatch, SoccerGame> result = new LinkedHashMap<>();
        for (RankedMatch match : matches.values()) {
            result.put(match, games.get(match.id()));
        }
        return result;
    }
}
