package dev.franwdev.lootrteams.team;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import noobanidus.mods.lootr.common.data.LootrInventory;

public class TeamStorageManager {

    // teamUUID -> Set of playerUUIDs in that team
    private final Map<UUID, Set<UUID>> teamMembers = new ConcurrentHashMap<>();

    // playerUUID -> teamUUID (reverse cache)
    private final Map<UUID, UUID> playerToTeam = new ConcurrentHashMap<>();

    // teamUUID -> Set of playerUUIDs that have a synced player entry
    private final Map<UUID, Set<UUID>> syncedPlayers = new ConcurrentHashMap<>();

    /**
     * Called from the Mixin when a new inventory is created.
     * Registers the relationship between player and team.
     */
    public void onInventoryCreated(UUID teamId, UUID playerId, LootrInventory inventory) {
        updatePlayerTeam(playerId, teamId);
    }


    /** Returns all playerUUIDs associated with a team. */
    public Set<UUID> getPlayersInTeam(UUID teamId) {
        return teamMembers.getOrDefault(teamId, Collections.emptySet());
    }



    public void updatePlayerTeam(UUID playerId, UUID teamId) {
        UUID previousTeam = playerToTeam.put(playerId, teamId);
        if (previousTeam != null && !previousTeam.equals(teamId)) {
            Set<UUID> previousMembers = teamMembers.get(previousTeam);
            if (previousMembers != null) {
                previousMembers.remove(playerId);
            }
            Set<UUID> previousSynced = syncedPlayers.get(previousTeam);
            if (previousSynced != null) {
                previousSynced.remove(playerId);
            }
        }
        teamMembers.computeIfAbsent(teamId, k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public void markPlayerSynced(UUID teamId, UUID playerId) {
        syncedPlayers.computeIfAbsent(teamId, k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public boolean wasPlayerSynced(UUID teamId, UUID playerId) {
        return syncedPlayers.getOrDefault(teamId, Collections.emptySet()).contains(playerId);
    }

    public UUID getTeamForPlayer(UUID playerId) {
        return playerToTeam.get(playerId);
    }

    /** Clears the cache (should be called on server stop or world reload). */
    public void clear() {
        teamMembers.clear();
        playerToTeam.clear();
        syncedPlayers.clear();
    }
}
