package com.braiszx.bbranked.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Una entrada en la cola: o un jugador solo, o una party entera.
 *
 * <p>Los miembros de un ticket siempre acaban en el mismo equipo, que es lo
 * que permite jugar con amigos.</p>
 */
public final class QueueTicket {

    private final List<QueueEntry> members;
    private final UUID partyId;
    private final long joinedAt = System.currentTimeMillis();

    public QueueTicket(List<QueueEntry> members, UUID partyId) {
        this.members = List.copyOf(members);
        this.partyId = partyId;
    }

    public static QueueTicket solo(QueueEntry entry) {
        return new QueueTicket(List.of(entry), null);
    }

    public List<QueueEntry> members() {
        return members;
    }

    public int size() {
        return members.size();
    }

    public boolean isParty() {
        return partyId != null;
    }

    public UUID partyId() {
        return partyId;
    }

    public List<UUID> uuids() {
        List<UUID> uuids = new ArrayList<>(members.size());
        for (QueueEntry entry : members) {
            uuids.add(entry.uuid());
        }
        return uuids;
    }

    public boolean contains(UUID uuid) {
        for (QueueEntry entry : members) {
            if (entry.uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Elo medio del ticket, que es con lo que se empareja.
     */
    public int averageElo() {
        int total = 0;
        for (QueueEntry entry : members) {
            total += entry.elo();
        }
        return total / members.size();
    }

    public int totalElo() {
        int total = 0;
        for (QueueEntry entry : members) {
            total += entry.elo();
        }
        return total;
    }

    public long waitedSeconds() {
        return (System.currentTimeMillis() - joinedAt) / 1000L;
    }

    /**
     * Ventana de Elo que acepta el ticket. Se usa la del miembro que lleva
     * mas esperando, para que una party no se quede atascada.
     */
    public int acceptedRange(int initial, int perSecond, int max) {
        long range = initial + waitedSeconds() * perSecond;
        return (int) Math.min(range, max);
    }

    /**
     * True si algun miembro comparte IP con algun miembro del otro ticket.
     */
    public boolean sharesIpWith(QueueTicket other) {
        for (QueueEntry mine : members) {
            for (QueueEntry theirs : other.members) {
                if (mine.sharesIpWith(theirs)) {
                    return true;
                }
            }
        }
        return false;
    }
}
