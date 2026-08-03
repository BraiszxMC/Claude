package com.braiszx.bbranked.ready;

import com.braiszx.bbranked.config.QueueMode;
import com.braiszx.bbranked.queue.QueueTicket;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Una partida esperando a que todos confirmen antes de empezar.
 */
public final class ReadyCheck {

    private final UUID id = UUID.randomUUID();
    private final QueueMode mode;
    private final List<QueueTicket> tickets;
    private final List<UUID> red;
    private final List<UUID> blue;
    private final long deadline;
    private final Set<UUID> accepted = new LinkedHashSet<>();

    private boolean resolved;

    public ReadyCheck(QueueMode mode, List<QueueTicket> tickets, List<UUID> red, List<UUID> blue,
                      int seconds) {
        this.mode = mode;
        this.tickets = List.copyOf(tickets);
        this.red = List.copyOf(red);
        this.blue = List.copyOf(blue);
        this.deadline = System.currentTimeMillis() + seconds * 1000L;
    }

    public UUID id() {
        return id;
    }

    public QueueMode mode() {
        return mode;
    }

    public List<QueueTicket> tickets() {
        return tickets;
    }

    public List<UUID> red() {
        return red;
    }

    public List<UUID> blue() {
        return blue;
    }

    public List<UUID> everyone() {
        List<UUID> all = new ArrayList<>(red.size() + blue.size());
        all.addAll(red);
        all.addAll(blue);
        return all;
    }

    public boolean resolved() {
        return resolved;
    }

    public void resolved(boolean resolved) {
        this.resolved = resolved;
    }

    public boolean accept(UUID uuid) {
        if (!red.contains(uuid) && !blue.contains(uuid)) {
            return false;
        }
        return accepted.add(uuid);
    }

    public boolean hasAccepted(UUID uuid) {
        return accepted.contains(uuid);
    }

    public int acceptedCount() {
        return accepted.size();
    }

    public int total() {
        return red.size() + blue.size();
    }

    public boolean everyoneAccepted() {
        return accepted.size() >= total();
    }

    /**
     * Los que todavia no han confirmado.
     */
    public List<UUID> missing() {
        List<UUID> missing = new ArrayList<>();
        for (UUID uuid : everyone()) {
            if (!accepted.contains(uuid)) {
                missing.add(uuid);
            }
        }
        return missing;
    }

    public long secondsLeft() {
        long remaining = deadline - System.currentTimeMillis();
        return remaining <= 0 ? 0 : remaining / 1000L + 1;
    }

    public boolean expired() {
        return System.currentTimeMillis() >= deadline;
    }
}
