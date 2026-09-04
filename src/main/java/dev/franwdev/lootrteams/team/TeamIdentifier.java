package dev.franwdev.lootrteams.team;

import java.util.UUID;
import java.util.function.Function;

import net.minecraft.server.level.ServerPlayer;
import dev.franwdev.lootrteams.config.TeamLootrConfig;

public class TeamIdentifier {

    public static final UUID GLOBAL_SHARED_TEAM_ID = UUID.nameUUIDFromBytes("global_shared_team".getBytes());

    public static Function<UUID, UUID> TEST_STUB = null;

    /**
     * Returns the team UUID of the given player.
     * If the player does not have a team (FTB Teams is absent or the player is solo),
     * it returns a ghost team UUID derived deterministically from the player's UUID.
     */
    public UUID getTeamId(ServerPlayer player) {
        if (TeamLootrConfig.GLOBAL_SHARED_LOOT) {
            return GLOBAL_SHARED_TEAM_ID;
        }
        UUID result;
        if ("true".equals(System.getProperty("lootrteams.testMode")) && TEST_STUB != null) {
            System.out.println("[LootrTeamsTest] getTeamId using TEST_STUB. identityHashCode: " + System.identityHashCode(TEST_STUB));
            result = TEST_STUB.apply(player.getUUID());
        } else if (FTBTeamsCompat.isLoaded()) {
            UUID teamId = FTBTeamsCompat.getTeamId(player);
            result = (teamId != null) ? teamId : toGhostTeamId(player.getUUID());
        } else {
            result = toGhostTeamId(player.getUUID());
        }
        
        if ("true".equals(System.getProperty("lootrteams.testMode"))) {
             System.out.println("[LootrTeamsTest] Resolved team for " + player.getName().getString() + " (" + player.getUUID() + ") -> " + result);
        }
        return result;
    }

    /**
     * Generates a deterministic ghost team UUID from a player UUID.
     * The same player will always resolve to the same ghost team UUID.
     */
    public static UUID toGhostTeamId(UUID playerUUID) {
        return UUID.nameUUIDFromBytes(("ghost_" + playerUUID.toString()).getBytes());
    }

    public UUID getTeamId(UUID playerId) {
        if (TeamLootrConfig.GLOBAL_SHARED_LOOT) {
            return GLOBAL_SHARED_TEAM_ID;
        }
        UUID result;
        if ("true".equals(System.getProperty("lootrteams.testMode")) && TEST_STUB != null) {
            result = TEST_STUB.apply(playerId);
        } else if (FTBTeamsCompat.isLoaded()) {
            UUID teamId = FTBTeamsCompat.getTeamIdForPlayerId(playerId);
            result = (teamId != null) ? teamId : toGhostTeamId(playerId);
        } else {
            result = toGhostTeamId(playerId);
        }
        return result;
    }
}
