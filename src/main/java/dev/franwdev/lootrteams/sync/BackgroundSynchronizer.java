package dev.franwdev.lootrteams.sync;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.franwdev.lootrteams.config.TeamLootrConfig;
import dev.franwdev.lootrteams.mixins.AccessorLootrSavedData;
import dev.franwdev.lootrteams.team.FTBTeamsCompat;
import dev.franwdev.lootrteams.team.TeamLootrManager;
import dev.franwdev.lootrteams.team.TeamStorageManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import noobanidus.mods.lootr.common.data.LootrInventory;
import noobanidus.mods.lootr.common.data.LootrSavedData;

public class BackgroundSynchronizer {

    private static final Logger LOG = LogManager.getLogger("lootrteams");

    private final ExecutorService executor =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "lootrteams-sync");
            t.setDaemon(true);
            return t;
        });

    private final BlockingQueue<SyncTask> queue = new LinkedBlockingQueue<>();

    public void start() {
        executor.submit(this::loop);
    }

    public void stop() {
        executor.shutdownNow();
    }

    /**
     * Queues a synchronization task so it doesn't block the main thread.
     * If team members are unknown, attempts to resolve them from FTB Teams.
     */
    public void scheduleSyncToPlayers(LootrSavedData chestData, UUID teamUUID) {
        if (!TeamLootrConfig.ENABLE_TEAMS || !TeamLootrConfig.ENABLE_LEGACY_SYNC) return;
        if (TeamLootrManager.INSTANCE == null) return;

        TeamStorageManager storageManager = TeamLootrManager.INSTANCE.getStorageManager();
        Set<UUID> playerIds = storageManager.getPlayersInTeam(teamUUID);

        // Player joined a team after having solo loot
        // Try to resolve team members from FTB Teams if not cached yet
        if (playerIds.isEmpty() && FTBTeamsCompat.isLoaded()) {
            Set<UUID> resolved = FTBTeamsCompat.getTeamMembers(teamUUID);
            if (!resolved.isEmpty()) {
                playerIds = resolved;
                // Update cache for future syncs
                for (UUID playerId : resolved) {
                    storageManager.updatePlayerTeam(playerId, teamUUID);
                }
            }
        }

        if (playerIds.isEmpty()) return;

        queue.offer(new SyncTask(chestData, teamUUID, new HashSet<>(playerIds)));
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SyncTask task = queue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("[LootrTeams] Error in sync thread", e);
            }
        }
    }

    public void processTaskImmediate(LootrSavedData chestData, UUID teamUUID) {
        TeamStorageManager storageManager = TeamLootrManager.INSTANCE != null
            ? TeamLootrManager.INSTANCE.getStorageManager()
            : null;
        Set<UUID> playerUUIDs = storageManager != null ? storageManager.getPlayersInTeam(teamUUID) : Collections.emptySet();

        LootrInventory teamInventory = chestData.getInventory(teamUUID);
        if (teamInventory == null) return;

        for (UUID playerId : playerUUIDs) {
            // Synchronize the player entry with the team inventory
            ((AccessorLootrSavedData) chestData).lootrteams$getInventories()
                .put(playerId, teamInventory);
            chestData.setDirty();
            if (storageManager != null) {
                storageManager.markPlayerSynced(teamUUID, playerId);
            }
        }
    }

    private void processTask(SyncTask task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                LootrInventory teamInventory = task.chestData.getInventory(task.teamUUID);
                if (teamInventory == null) return;

                TeamStorageManager storageManager = TeamLootrManager.INSTANCE != null
                    ? TeamLootrManager.INSTANCE.getStorageManager()
                    : null;
                for (UUID playerId : task.playerUUIDs) {
                    // Only synchronize if the player does not have their own entry already
                    if (task.chestData.getInventory(playerId) == null) {
                        ((AccessorLootrSavedData) task.chestData).lootrteams$getInventories()
                            .put(playerId, teamInventory);
                        task.chestData.setDirty();
                        if (TeamLootrConfig.DEBUG_MODE) {
                            LOG.info("[LootrTeams] Synchronized inventory for player {} from team {}", playerId, task.teamUUID);
                        }
                        if (storageManager != null) {
                            storageManager.markPlayerSynced(task.teamUUID, playerId);
                        }
                    }
                }
            });
        }
    }

    private record SyncTask(LootrSavedData chestData, UUID teamUUID, Set<UUID> playerUUIDs) {}
}
