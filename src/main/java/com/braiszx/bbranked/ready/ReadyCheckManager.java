package com.braiszx.bbranked.ready;

import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.match.MatchManager;
import com.braiszx.bbranked.queue.QueueTicket;
import com.braiszx.bbranked.util.Messages;
import com.braiszx.bbranked.util.Sounds;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Confirmacion previa a la partida: todos tienen que pulsar ACEPTAR antes de
 * que se les teletransporte a la arena.
 */
public final class ReadyCheckManager {

    private final RankedConfig config;
    private final Messages messages;
    private final Sounds sounds;
    private final MatchManager matches;

    /** confirmacion en curso, por id. */
    private final Map<UUID, ReadyCheck> checks = new ConcurrentHashMap<>();
    /** jugador -> confirmacion en la que esta. */
    private final Map<UUID, ReadyCheck> byPlayer = new ConcurrentHashMap<>();

    /** Devuelve los tickets a la cola cuando la confirmacion falla. */
    private BiConsumer<QueueMode, List<QueueTicket>> requeue = (mode, tickets) -> { };
    /** Penaliza a quien no acepta. */
    private Consumer<UUID> penalize = uuid -> { };

    public ReadyCheckManager(RankedConfig config, Messages messages, Sounds sounds, MatchManager matches) {
        this.config = config;
        this.messages = messages;
        this.sounds = sounds;
        this.matches = matches;
    }

    /**
     * Enlaza las acciones que dependen de la cola. Se hace aparte para no
     * crear una dependencia circular en los constructores.
     */
    public void wire(BiConsumer<QueueMode, List<QueueTicket>> requeue, Consumer<UUID> penalize) {
        this.requeue = requeue;
        this.penalize = penalize;
    }

    public boolean isPending(UUID uuid) {
        return byPlayer.containsKey(uuid);
    }

    /**
     * Lanza la confirmacion. Los jugadores ya tienen que estar fuera de la cola.
     */
    public void begin(QueueMode mode, List<QueueTicket> tickets, List<UUID> red, List<UUID> blue) {
        ReadyCheck check = new ReadyCheck(mode, tickets, red, blue, config.readyCheckSeconds());
        checks.put(check.id(), check);
        for (UUID uuid : check.everyone()) {
            byPlayer.put(uuid, check);
        }

        for (UUID uuid : check.everyone()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            messages.sendRaw(player, "ready.header");
            messages.sendRaw(player, "ready.found", Map.of(
                    "mode", mode.displayName(),
                    "seconds", String.valueOf(config.readyCheckSeconds())));
            // El boton clicable ejecuta /ranked accept.
            player.sendMessage(messages.raw("ready.button"));
            messages.sendRaw(player, "ready.footer");
            sounds.play(player, "ready-check");
        }
    }

    /**
     * Marca que un jugador acepta.
     */
    public void accept(Player player) {
        ReadyCheck check = byPlayer.get(player.getUniqueId());
        if (check == null || check.resolved()) {
            messages.send(player, "ready.nothing-pending");
            return;
        }
        if (!check.accept(player.getUniqueId())) {
            messages.send(player, "ready.already-accepted");
            return;
        }

        sounds.play(player, "ready-accept");
        Map<String, String> placeholders = Map.of(
                "accepted", String.valueOf(check.acceptedCount()),
                "total", String.valueOf(check.total()));
        for (UUID uuid : check.everyone()) {
            Player other = Bukkit.getPlayer(uuid);
            if (other != null) {
                messages.send(other, "ready.accepted", placeholders);
            }
        }

        if (check.everyoneAccepted()) {
            start(check);
        }
    }

    /**
     * Se llama cada segundo desde el hilo principal.
     */
    public void tick() {
        for (ReadyCheck check : new ArrayList<>(checks.values())) {
            if (check.resolved()) {
                continue;
            }

            // Si alguien se desconecta a mitad, se cancela ya.
            for (UUID uuid : check.everyone()) {
                if (Bukkit.getPlayer(uuid) == null) {
                    cancel(check, List.of(uuid), "ready.cancelled-offline");
                    break;
                }
            }
            if (check.resolved()) {
                continue;
            }

            if (check.expired()) {
                cancel(check, check.missing(), "ready.cancelled-timeout");
                continue;
            }

            // Cuenta atras en la barra de accion para los que faltan.
            Component countdown = messages.raw("actionbar.ready", Map.of(
                    "seconds", String.valueOf(check.secondsLeft()),
                    "accepted", String.valueOf(check.acceptedCount()),
                    "total", String.valueOf(check.total())));
            for (UUID uuid : check.missing()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendActionBar(countdown);
                }
            }
        }
    }

    private void start(ReadyCheck check) {
        check.resolved(true);
        release(check);

        List<Player> red = toPlayers(check.red());
        List<Player> blue = toPlayers(check.blue());
        if (red == null || blue == null) {
            requeue.accept(check.mode(), check.tickets());
            return;
        }

        boolean dispatched = matches.startMatch(check.mode(), red, blue,
                () -> requeue.accept(check.mode(), check.tickets()));
        if (!dispatched) {
            requeue.accept(check.mode(), check.tickets());
        }
    }

    /**
     * Cancela la confirmacion: los culpables se llevan penalizacion y el resto
     * vuelve a la cola.
     */
    private void cancel(ReadyCheck check, List<UUID> blame, String reasonKey) {
        check.resolved(true);
        release(check);

        for (UUID uuid : blame) {
            if (config.readyCheckPenaltySeconds() > 0) {
                penalize.accept(uuid);
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                messages.send(player, "ready.you-failed");
                sounds.play(player, "error");
            }
        }

        for (UUID uuid : check.everyone()) {
            if (blame.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                messages.send(player, reasonKey);
                sounds.play(player, "error");
            }
        }

        // Solo vuelven a la cola los tickets sin culpables dentro.
        List<QueueTicket> returning = new ArrayList<>();
        for (QueueTicket ticket : check.tickets()) {
            boolean guilty = false;
            for (UUID uuid : ticket.uuids()) {
                if (blame.contains(uuid)) {
                    guilty = true;
                    break;
                }
            }
            if (!guilty) {
                returning.add(ticket);
            }
        }
        if (!returning.isEmpty()) {
            requeue.accept(check.mode(), returning);
        }
    }

    private void release(ReadyCheck check) {
        checks.remove(check.id());
        for (UUID uuid : check.everyone()) {
            byPlayer.remove(uuid, check);
        }
    }

    private List<Player> toPlayers(List<UUID> uuids) {
        List<Player> players = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                return null;
            }
            players.add(player);
        }
        return players;
    }

    /**
     * Saca a un jugador de cualquier confirmacion pendiente, por ejemplo al
     * desconectarse.
     */
    public void remove(UUID uuid) {
        ReadyCheck check = byPlayer.get(uuid);
        if (check != null && !check.resolved()) {
            cancel(check, List.of(uuid), "ready.cancelled-offline");
        }
    }

    public void clear() {
        checks.clear();
        byPlayer.clear();
    }
}
