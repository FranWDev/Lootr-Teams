package dev.franwdev.lootrteams.gametest;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.franwdev.lootrteams.LootrTeams;
import dev.franwdev.lootrteams.config.TeamLootrConfig;
import dev.franwdev.lootrteams.team.TeamIdentifier;
import dev.franwdev.lootrteams.team.TeamLootrManager;
import dev.franwdev.lootrteams.team.TeamStorageManager;
import dev.franwdev.lootrteams.util.LootrTeamsServerUtil;

import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.data.LootrInventory;
import noobanidus.mods.lootr.common.data.LootrSavedData;

@GameTestHolder(LootrTeams.MODID)
@PrefixGameTestTemplate(false)
public class LootrTeamsGameTests {

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sharedLootSameTeam(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID teamId = UUID.nameUUIDFromBytes("shared_team".getBytes());
        UUID playerAId = UUID.nameUUIDFromBytes("shared_player_a".getBytes());
        UUID playerBId = UUID.nameUUIDFromBytes("shared_player_b".getBytes());

        TeamTestStub.setTeam(playerAId, teamId);
        TeamTestStub.setTeam(playerBId, teamId);

        ServerPlayer playerA = TestHelpers.makePlayer(helper, playerAId, "PlayerA");
        ServerPlayer playerB = TestHelpers.makePlayer(helper, playerBId, "PlayerB");

        helper.runAfterDelay(1, () -> {
            LootrInventory invA = TestHelpers.openChest(helper, playerA);
            LootrInventory invB = TestHelpers.openChest(helper, playerB);

            helper.assertTrue(invA != null, "PlayerA should get inventory (LootrSavedData not found)");
            helper.assertTrue(invA == invB, "Both players in same team should share the exact same inventory instance");

            LootrSavedData data = TestHelpers.getChestData(helper);
            helper.assertTrue(data != null, "LootrSavedData should be accessible via TestHelpers");
            helper.assertTrue(TestHelpers.getInventoryMap(data).containsKey(teamId),
                    "Inventory map should contain the team UUID");

            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ghostTeamsHaveSeparateLoot(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID playerAId = UUID.randomUUID();
        UUID playerBId = UUID.randomUUID();

        ServerPlayer playerA = TestHelpers.makePlayer(helper, playerAId, "SoloA");
        ServerPlayer playerB = TestHelpers.makePlayer(helper, playerBId, "SoloB");

        helper.runAfterDelay(1, () -> {
            LootrInventory invA = TestHelpers.openChest(helper, playerA);
            LootrInventory invB = TestHelpers.openChest(helper, playerB);

            helper.assertTrue(invA != null && invB != null,
                    "Both solo players should get inventories (LootrSavedData not found)");
            helper.assertTrue(invA != invB, "Solo players should have separate inventory instances");

            LootrSavedData data = TestHelpers.getChestData(helper);
            helper.assertTrue(data != null, "LootrSavedData should be accessible via TestHelpers");
            var map = TestHelpers.getInventoryMap(data);
            helper.assertTrue(map.containsKey(TeamIdentifier.toGhostTeamId(playerAId)), "Map should contain ghost A");
            helper.assertTrue(map.containsKey(TeamIdentifier.toGhostTeamId(playerBId)), "Map should contain ghost B");

            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void teamChangeCausesNewLoot(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID playerAId = UUID.randomUUID();
        UUID team1Id = UUID.randomUUID();
        UUID team2Id = UUID.randomUUID();

        TeamTestStub.setTeam(playerAId, team1Id);
        ServerPlayer playerA = TestHelpers.makePlayer(helper, playerAId, "PlayerA");

        UUID id1 = TeamLootrManager.INSTANCE.getTeamId(playerA);
        helper.assertTrue(id1.equals(team1Id), "Should be team 1");

        TeamTestStub.setTeam(playerAId, team2Id);
        UUID id2 = TeamLootrManager.INSTANCE.getTeamId(playerA);
        helper.assertTrue(id2.equals(team2Id), "Should be team 2 after change");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void soloPlayerThenJoinsTeam(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID playerAId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ServerPlayer playerA = TestHelpers.makePlayer(helper, playerAId, "PlayerA");

        UUID idSolo = TeamLootrManager.INSTANCE.getTeamId(playerA);
        helper.assertTrue(idSolo.equals(TeamIdentifier.toGhostTeamId(playerAId)), "Should be ghost team");

        TeamTestStub.setTeam(playerAId, teamId);
        UUID idTeam = TeamLootrManager.INSTANCE.getTeamId(playerA);
        helper.assertTrue(idTeam.equals(teamId), "Should be real team after joining");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void worksWithoutFTBTeams(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        ServerPlayer player = TestHelpers.makePlayer(helper, UUID.randomUUID(), "SoloA");

        UUID id = TeamLootrManager.INSTANCE.getTeamId(player);
        helper.assertTrue(id.equals(TeamIdentifier.toGhostTeamId(player.getUUID())), "Should fallback to ghost team");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated")
    public static void addonDisabledFallsBackToVanillaLootr(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        boolean previous = TeamLootrConfig.ENABLE_TEAMS;
        TeamLootrConfig.ENABLE_TEAMS = false;

        helper.runAfterDelay(1, () -> {
            try {
                UUID teamId = UUID.randomUUID();
                UUID playerAId = UUID.randomUUID();
                UUID playerBId = UUID.randomUUID();

                TeamTestStub.setTeam(playerAId, teamId);
                TeamTestStub.setTeam(playerBId, teamId);

                ServerPlayer playerA = TestHelpers.makePlayer(helper, playerAId, "PlayerA");
                ServerPlayer playerB = TestHelpers.makePlayer(helper, playerBId, "PlayerB");

                TestHelpers.openChest(helper, playerA);
                TestHelpers.openChest(helper, playerB);

                LootrSavedData data = TestHelpers.getChestData(helper);
                if (data == null) {
                    helper.fail("Chest data should exist");
                    return;
                }

                var map = TestHelpers.getInventoryMap(data);

                helper.assertTrue(map.containsKey(playerAId), "PlayerA should use player UUID when disabled");
                helper.assertTrue(map.containsKey(playerBId), "PlayerB should use player UUID when disabled");
                helper.assertFalse(map.containsKey(teamId), "Team UUID should not be used when disabled");
                helper.succeed();
            } finally {
                TeamLootrConfig.ENABLE_TEAMS = previous;
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void concurrentOpensSameTeam(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID teamId = UUID.nameUUIDFromBytes("concurrent_team".getBytes());
        int playerCount = 6;

        var players = new ArrayList<ServerPlayer>();
        var inventories = new ArrayList<LootrInventory>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.nameUUIDFromBytes(("concurrent_player_" + i).getBytes());
            TeamTestStub.setTeam(playerId, teamId);
            players.add(TestHelpers.makePlayer(helper, playerId, "Player_" + i));
        }

        helper.runAfterDelay(1, () -> {
            for (ServerPlayer player : players) {
                inventories.add(TestHelpers.openChest(helper, player));
            }
        });

        helper.runAfterDelay(10, () -> {
            LootrSavedData data = TestHelpers.getChestData(helper);
            if (data == null) {
                helper.fail("Chest data should exist");
                return;
            }
            var map = TestHelpers.getInventoryMap(data);
            var teamInventory = map.get(teamId);

            if (teamInventory == null) {
                System.out.println("[LootrTeamsTest] ERROR: teamInventory is null! teamId was " + teamId);
                System.out.println("[LootrTeamsTest] Map contains " + map.size() + " keys:");
                for (UUID k : map.keySet()) {
                    System.out.println("[LootrTeamsTest] Key: " + k);
                }
            }

            helper.assertTrue(teamInventory != null, "Team inventory must exist");
            for (int i = 0; i < inventories.size(); i++) {
                helper.assertTrue(inventories.get(i) == teamInventory, "Player " + i + " should share the team inventory");
            }

            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void manyChestsSameTeam(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        UUID teamId = UUID.randomUUID();
        UUID playerAId = UUID.randomUUID();
        TeamTestStub.setTeam(playerAId, teamId);
        ServerPlayer player = TestHelpers.makePlayer(helper, playerAId, "Tester");

        for (int i = 0; i < 10; i++) {
            BlockPos pos = TestHelpers.CHEST_POS.offset(i, 0, 0);
            TestHelpers.setupChest(helper, pos);
            UUID id = TeamLootrManager.INSTANCE.getTeamId(player);
            helper.assertTrue(id.equals(teamId), "Identifier must be team UUID for chest " + i);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void synchronizerPopulatesPlayerUUID(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        TeamTestStub.setTeam(playerId, teamId);

        final LootrSavedData[] dataRef = new LootrSavedData[1];

        helper.runAfterDelay(1, () -> {
            ServerPlayer player = TestHelpers.makePlayer(helper, playerId, "PlayerA");
            TestHelpers.openChest(helper, player);
            dataRef[0] = TestHelpers.getChestData(helper);
            if (dataRef[0] == null) {
                helper.fail("Chest data should exist");
                return;
            }

            var map = TestHelpers.getInventoryMap(dataRef[0]);
            helper.assertTrue(map.containsKey(teamId), "Team entry should exist after opening");
        });

        helper.runAfterDelay(40, () -> {
            LootrSavedData data = dataRef[0];
            if (data == null) {
                helper.fail("Chest data should exist");
                return;
            }
            var updated = TestHelpers.getInventoryMap(data);
            helper.assertTrue(updated.containsKey(playerId), "Player entry should be synced after delay");
            helper.assertTrue(updated.get(playerId) == updated.get(teamId), "Synced entry should match team inventory");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void playerLeavesTeamGetsNoNewLoot(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        TeamTestStub.setTeam(playerId, teamId);
        ServerPlayer player = TestHelpers.makePlayer(helper, playerId, "PlayerA");

        final LootrSavedData[] dataRef = new LootrSavedData[1];
        final LootrInventory[] inventoryRef = new LootrInventory[1];

        helper.runAfterDelay(1, () -> {
            inventoryRef[0] = TestHelpers.openChest(helper, player);
            dataRef[0] = TestHelpers.getChestData(helper);
            if (dataRef[0] == null) {
                helper.fail("Chest data should exist");
                return;
            }
        });

        helper.runAfterDelay(20, () -> {
            LootrSavedData data = dataRef[0];
            if (data == null) {
                helper.fail("Chest data should exist");
                return;
            }

            var map = TestHelpers.getInventoryMap(data);
            helper.assertTrue(map.containsKey(playerId), "Player entry should be synced before leaving");

            // LEAVE TEAM: Map player to their own ghost team (solo)
            TeamTestStub.setTeam(playerId, TeamIdentifier.toGhostTeamId(playerId));

            LootrInventory ghostInventory = TestHelpers.openChest(helper, player);
            UUID ghostId = TeamIdentifier.toGhostTeamId(playerId);
            var updated = TestHelpers.getInventoryMap(data);

            helper.assertTrue(updated.containsKey(ghostId), "Ghost entry should exist after leaving");
            helper.assertTrue(ghostInventory == inventoryRef[0], "Leaving should not grant new loot (shared loot is kept)");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teamStorageManagerCacheRebuilds(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID teamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        TeamStorageManager storage = TeamLootrManager.INSTANCE.getStorageManager();
        storage.onInventoryCreated(teamId, playerId, null);

        // Verify the team has the player tracked
        helper.assertTrue(storage.getPlayersInTeam(teamId).contains(playerId), "Should have player in team");

        storage.clear();
        helper.assertTrue(storage.getPlayersInTeam(teamId).isEmpty(), "Should be empty after clear");

        final LootrSavedData[] dataRef = new LootrSavedData[1];
        final LootrInventory[] inventoryRef = new LootrInventory[1];

        helper.runAfterDelay(1, () -> {
            ServerPlayer player = TestHelpers.makePlayer(helper, playerId, "SoloPlayer");
            inventoryRef[0] = TestHelpers.openChest(helper, player);
            dataRef[0] = TestHelpers.getChestData(helper);
            if (dataRef[0] == null) {
                helper.fail("Chest data should exist");
            }
        });

        helper.runAfterDelay(40, () -> {
            LootrSavedData data = dataRef[0];
            if (data == null) {
                helper.fail("Chest data should exist");
                return;
            }

            var map = TestHelpers.getInventoryMap(data);
            helper.assertTrue(map.containsKey(playerId), "Player entry should be synced before clear");

            boolean cleared = TestHelpers.clearPlayerInventories(helper, playerId);
            helper.assertTrue(cleared, "Player inventory should be cleared by Lootr");

            ServerPlayer player = TestHelpers.makePlayer(helper, playerId, "SoloPlayer");
            LootrInventory secondInventory = TestHelpers.openChest(helper, player);
            helper.assertTrue(secondInventory != inventoryRef[0], "After clear, solo player should get new loot");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void lootrClearWorksForSoloPlayers(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID playerId = UUID.randomUUID();
        UUID ghostId = TeamIdentifier.toGhostTeamId(playerId);

        ServerPlayer player = TestHelpers.makePlayer(helper, playerId, "SoloPlayer");

        helper.runAfterDelay(1, () -> {
            TestHelpers.openChest(helper, player);
        });

        helper.runAfterDelay(40, () -> {
            LootrSavedData data = TestHelpers.getChestData(helper);
            var map = TestHelpers.getInventoryMap(data);
            helper.assertTrue(map.containsKey(playerId), "Player entry should be synced");
            helper.assertTrue(map.containsKey(ghostId), "Ghost entry should exist");

            TestHelpers.clearPlayerInventories(helper, playerId);
            helper.assertFalse(map.containsKey(playerId), "Player entry should be cleared");
            helper.assertFalse(map.containsKey(ghostId), "Ghost entry should also be cleared (synced deletion)");

            LootrInventory secondInv = TestHelpers.openChest(helper, player);
            helper.assertTrue(secondInv != null, "Should get new inventory after clear");

            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teamMembersSeeOpenedTexture(GameTestHelper helper) {
        TeamTestStub.init();
        TestHelpers.reset();
        TestHelpers.setupChest(helper, TestHelpers.CHEST_POS);
        UUID teamId = UUID.nameUUIDFromBytes("texture_team".getBytes());
        UUID playerAId = UUID.nameUUIDFromBytes("texture_player_a".getBytes());
        UUID playerBId = UUID.nameUUIDFromBytes("texture_player_b".getBytes());

        TeamTestStub.setTeam(playerAId, teamId);
        TeamTestStub.setTeam(playerBId, teamId);

        ServerPlayer playerA = TestHelpers.makePlayer(helper, playerAId, "PlayerA");
        ServerPlayer playerB = TestHelpers.makePlayer(helper, playerBId, "PlayerB");

        helper.runAfterDelay(1, () -> {
            // Player A opens the chest
            TestHelpers.openChest(helper, playerA);

            // Simulate the addOpener/sync that happens in real gameplay
            ILootrBlockEntity tile = (ILootrBlockEntity) helper.getBlockEntity(TestHelpers.CHEST_POS);
            LootrTeamsServerUtil.refreshOpeners(tile);

            java.util.Set<UUID> openers = tile.getOpeners();

            helper.assertTrue(openers.contains(playerAId), "PlayerA should be in openers set");
            helper.assertTrue(openers.contains(playerBId), "PlayerB should be in openers set");
            helper.assertTrue(openers.contains(teamId), "TeamId should be in openers set");

            helper.succeed();
        });
    }
}
