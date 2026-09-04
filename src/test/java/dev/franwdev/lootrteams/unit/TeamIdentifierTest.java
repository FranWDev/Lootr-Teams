package dev.franwdev.lootrteams.unit;

import dev.franwdev.lootrteams.team.TeamIdentifier;
import dev.franwdev.lootrteams.config.TeamLootrConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TeamIdentifierTest {

    @Test
    void ghostTeamIsDeterministic() {
        UUID player = UUID.randomUUID();
        UUID ghost1 = TeamIdentifier.toGhostTeamId(player);
        UUID ghost2 = TeamIdentifier.toGhostTeamId(player);
        assertEquals(ghost1, ghost2, "Ghost team UUID must be deterministic");
    }

    @Test
    void differentPlayersHaveDifferentGhostTeams() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        assertNotEquals(
            TeamIdentifier.toGhostTeamId(playerA),
            TeamIdentifier.toGhostTeamId(playerB),
            "Different players must yield different ghost team UUIDs"
        );
    }

    @Test
    void ghostTeamIsNotSameAsPlayerUUID() {
        UUID player = UUID.randomUUID();
        UUID ghost = TeamIdentifier.toGhostTeamId(player);
        assertNotEquals(player, ghost, "Ghost team UUID should not equal the original player UUID");
    }

    @Test
    void globalSharedLootRedirectsAllPlayersToSameGlobalTeam() {
        TeamLootrConfig.GLOBAL_SHARED_LOOT = true;
        try {
            TeamIdentifier identifier = new TeamIdentifier();
            UUID playerA = UUID.randomUUID();
            UUID playerB = UUID.randomUUID();
            
            UUID idA = identifier.getTeamId(playerA);
            UUID idB = identifier.getTeamId(playerB);
            
            assertEquals(TeamIdentifier.GLOBAL_SHARED_TEAM_ID, idA);
            assertEquals(TeamIdentifier.GLOBAL_SHARED_TEAM_ID, idB);
            assertEquals(idA, idB);
        } finally {
            TeamLootrConfig.GLOBAL_SHARED_LOOT = false;
        }
    }
}
