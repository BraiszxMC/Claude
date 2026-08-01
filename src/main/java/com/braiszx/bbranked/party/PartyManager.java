package com.braiszx.bbranked.party;

import com.braiszx.bbranked.config.RankedConfig;
import com.braiszx.bbranked.data.PlayerStats;
import com.braiszx.bbranked.data.StatsManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona las parties: creacion, invitaciones y expulsiones.
 */
public final class PartyManager {

    private final RankedConfig config;
    private final StatsManager stats;

    /** party id -> party */
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    /** jugador -> party en la que esta */
    private final Map<UUID, Party> byPlayer = new ConcurrentHashMap<>();
    /** invitado -> (party id -> momento en que caduca la invitacion) */
    private final Map<UUID, Map<UUID, Long>> invites = new ConcurrentHashMap<>();

    public PartyManager(RankedConfig config, StatsManager stats) {
        this.config = config;
        this.stats = stats;
    }

    /**
     * Resultado de intentar entrar en una party.
     */
    public enum JoinResult {
        OK,
        PARTY_FULL,
        ELO_TOO_DIFFERENT,
        ALREADY_IN_PARTY,
        NO_INVITE
    }

    public Party partyOf(UUID uuid) {
        return byPlayer.get(uuid);
    }

    public boolean isInParty(UUID uuid) {
        return byPlayer.containsKey(uuid);
    }

    public Collection<Party> parties() {
        return parties.values();
    }

    /**
     * Diferencia de Elo entre el candidato y el miembro de la party mas
     * alejado. Se compara contra todos, no solo contra el lider, para que la
     * party entera se mantenga dentro del margen.
     */
    public int eloGapWith(Party party, UUID candidate) {
        PlayerStats candidateStats = stats.require(candidate);
        int worst = 0;
        for (UUID member : party.members()) {
            int gap = Math.abs(stats.require(member).elo() - candidateStats.elo());
            worst = Math.max(worst, gap);
        }
        return worst;
    }

    /**
     * Invita a alguien. La party se crea sola si el lider no tenia una.
     *
     * @return el motivo por el que no se ha podido, u OK
     */
    public JoinResult invite(UUID leader, UUID target) {
        if (isInParty(target)) {
            return JoinResult.ALREADY_IN_PARTY;
        }

        Party party = byPlayer.get(leader);
        if (party == null) {
            party = new Party(leader);
            parties.put(party.id(), party);
            byPlayer.put(leader, party);
        }

        if (party.size() >= config.partyMaxSize()) {
            return JoinResult.PARTY_FULL;
        }
        if (eloGapWith(party, target) > config.partyMaxEloDifference()) {
            return JoinResult.ELO_TOO_DIFFERENT;
        }

        invites.computeIfAbsent(target, key -> new ConcurrentHashMap<>())
                .put(party.id(), System.currentTimeMillis() + config.partyInviteTimeoutSeconds() * 1000L);
        return JoinResult.OK;
    }

    /**
     * Acepta la invitacion de la party de {@code leader}.
     */
    public JoinResult accept(UUID target, UUID leader) {
        if (isInParty(target)) {
            return JoinResult.ALREADY_IN_PARTY;
        }

        Party party = byPlayer.get(leader);
        if (party == null) {
            return JoinResult.NO_INVITE;
        }

        Map<UUID, Long> pending = invites.get(target);
        if (pending == null) {
            return JoinResult.NO_INVITE;
        }
        Long expiresAt = pending.get(party.id());
        if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
            pending.remove(party.id());
            return JoinResult.NO_INVITE;
        }

        if (party.size() >= config.partyMaxSize()) {
            return JoinResult.PARTY_FULL;
        }
        // Se vuelve a comprobar el Elo: pudo cambiar entre la invitacion y la
        // aceptacion.
        if (eloGapWith(party, target) > config.partyMaxEloDifference()) {
            return JoinResult.ELO_TOO_DIFFERENT;
        }

        pending.remove(party.id());
        party.add(target);
        byPlayer.put(target, party);
        return JoinResult.OK;
    }

    public void deny(UUID target, UUID leader) {
        Party party = byPlayer.get(leader);
        Map<UUID, Long> pending = invites.get(target);
        if (party != null && pending != null) {
            pending.remove(party.id());
        }
    }

    /**
     * Saca al jugador de su party. Si se queda con menos de dos miembros, la
     * party se disuelve.
     *
     * @return los que estaban en la party antes de irse, para poder avisarles
     */
    public List<UUID> leave(UUID uuid) {
        Party party = byPlayer.remove(uuid);
        if (party == null) {
            return List.of();
        }
        List<UUID> before = party.members();
        if (party.remove(uuid)) {
            disband(party);
        }
        return before;
    }

    public List<UUID> disband(UUID leader) {
        Party party = byPlayer.get(leader);
        if (party == null) {
            return List.of();
        }
        List<UUID> before = party.members();
        disband(party);
        return before;
    }

    private void disband(Party party) {
        for (UUID member : party.members()) {
            byPlayer.remove(member, party);
        }
        parties.remove(party.id());
    }

    /**
     * Limpia las invitaciones caducadas. Se llama cada cierto tiempo.
     */
    public void purgeExpiredInvites() {
        long now = System.currentTimeMillis();
        for (Map<UUID, Long> pending : invites.values()) {
            pending.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        invites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Parties en las que este jugador tiene invitacion pendiente.
     */
    public List<Party> pendingInvites(UUID uuid) {
        Map<UUID, Long> pending = invites.get(uuid);
        if (pending == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<Party> result = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : pending.entrySet()) {
            if (entry.getValue() >= now) {
                Party party = parties.get(entry.getKey());
                if (party != null) {
                    result.add(party);
                }
            }
        }
        return result;
    }

    public void clear() {
        parties.clear();
        byPlayer.clear();
        invites.clear();
    }
}
