package com.braiszx.bbranked.party;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Un grupo de amigos que entra a la cola junto y juega en el mismo equipo.
 *
 * <p>Vive solo en memoria: si el servidor se reinicia, las parties se
 * deshacen. Es intencionado, no tiene sentido conservarlas.</p>
 */
public final class Party {

    private final UUID id = UUID.randomUUID();
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID id() {
        return id;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    /**
     * Todos los miembros, con el lider el primero.
     */
    public List<UUID> members() {
        return new ArrayList<>(members);
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public void add(UUID uuid) {
        members.add(uuid);
    }

    /**
     * Saca a un miembro. Si era el lider, el mando pasa al siguiente.
     *
     * @return true si la party se queda sin gente suficiente y hay que disolverla
     */
    public boolean remove(UUID uuid) {
        members.remove(uuid);
        if (members.isEmpty()) {
            return true;
        }
        if (leader.equals(uuid)) {
            leader = members.iterator().next();
        }
        return members.size() < 2;
    }
}
