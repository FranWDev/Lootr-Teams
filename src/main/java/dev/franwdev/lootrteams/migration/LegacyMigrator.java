package dev.franwdev.lootrteams.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.franwdev.lootrteams.LootrTeams;
import dev.franwdev.lootrteams.config.TeamLootrConfig;
import dev.franwdev.lootrteams.team.FTBTeamsCompat;
import dev.franwdev.lootrteams.team.TeamIdentifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class LegacyMigrator {

    private static final Logger LOG = LogManager.getLogger(LootrTeams.MODID);
    private static final String MIGRATION_FLAG_FILE = "lootrteams_migrated.txt";

    /**
     * Entry point for migration. Should be called during ServerStartedEvent.
     */
    public static void runIfNeeded(MinecraftServer server) {
        if (!TeamLootrConfig.ENABLE_TEAMS) {
            LOG.info("[LootrTeams] Team loot is disabled; skipping migration.");
            return;
        }

        long lastMigrationTime = getLastMigrationTime(server);
        if (lastMigrationTime > 0) {
            LOG.info("[LootrTeams] Resuming migration. Last scan: {}", lastMigrationTime);
        } else {
            LOG.info("[LootrTeams] Starting fresh legacy migration...");
        }

        int migratedCount = migrate(server, lastMigrationTime);
        LOG.info("[LootrTeams] Migration cycle complete. Processed {} chest files.", migratedCount);

        setMigrated(server);
    }

    private static long getLastMigrationTime(MinecraftServer server) {
        Path flagPath = getFlagPath(server);
        if (!Files.exists(flagPath)) return 0;
        try {
            String content = Files.readString(flagPath).trim();
            return Long.parseLong(content);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void setMigrated(MinecraftServer server) {
        Path flagPath = getFlagPath(server);
        try {
            Files.createDirectories(flagPath.getParent());
            Files.writeString(flagPath, String.valueOf(System.currentTimeMillis()));
        } catch (IOException e) {
            LOG.error("[LootrTeams] Could not write migration flag!", e);
        }
    }

    private static Path getFlagPath(MinecraftServer server) {
        return server.getWorldPath(new LevelResource("data")).resolve(MIGRATION_FLAG_FILE);
    }

    private static int migrate(MinecraftServer server, long lastMigrationTime) {
        Path lootrDataPath = server.getWorldPath(new LevelResource("data")).resolve("lootr");
        if (!Files.isDirectory(lootrDataPath)) {
            LOG.info("[LootrTeams] No lootr data directory found. Nothing to migrate.");
            return 0;
        }

        int count = 0;
        try (Stream<Path> paths = Files.walk(lootrDataPath)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".dat"))
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() > lastMigrationTime;
                        } catch (IOException e) {
                            return true;
                        }
                    })
                    .toList()) {
                if (migrateFile(path, server)) {
                    count++;
                }
            }
        } catch (IOException e) {
            LOG.error("[LootrTeams] Error walking lootr data directory.", e);
        }
        return count;
    }

    private static boolean migrateFile(Path path, MinecraftServer server) {
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            // Fallback to uncompressed just in case
            try {
                root = NbtIo.read(path);
            } catch (IOException ex) {
                LOG.warn("[LootrTeams] Could not read {} (compressed or uncompressed)", path.getFileName());
                return false;
            }
        }

        boolean modified = migrateNbt(root, server);

        if (modified) {
            try {
                NbtIo.writeCompressed(root, path);
                return true;
            } catch (IOException e) {
                LOG.error("[LootrTeams] Could not write migrated file {}", path.getFileName(), e);
            }
        }

        return false;
    }


    /**
     * Extracts NBT manipulation logic for unit testing without a server
     * environment.
     * 
     * @param root The root NBT tag of the ChestData file.
     * @return true if the NBT was modified, false otherwise.
     */
    public static boolean migrateNbt(CompoundTag root) {
        return migrateNbt(root, uuid -> null);
    }

    public static boolean migrateNbt(CompoundTag root, Function<UUID, UUID> teamResolver) {
        if (!root.contains("data", Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag data = root.getCompound("data");
        if (!data.contains("inventories", Tag.TAG_LIST)) {
            return false;
        }

        ListTag inventories = data.getList("inventories", Tag.TAG_COMPOUND);
        boolean modified = false;
        ListTag toAdd = new ListTag();

        Set<UUID> existingUuids = new HashSet<>();
        Set<UUID> derivedGhosts = new HashSet<>();
        Set<UUID> addedTeams = new HashSet<>();

        for (int i = 0; i < inventories.size(); i++) {
            CompoundTag entry = inventories.getCompound(i);
            if (entry.hasUUID("uuid")) {
                UUID u = entry.getUUID("uuid");
                existingUuids.add(u);
                derivedGhosts.add(TeamIdentifier.toGhostTeamId(u));
            }
        }

        for (int i = 0; i < inventories.size(); i++) {
            CompoundTag entry = inventories.getCompound(i);
            if (!entry.hasUUID("uuid")) {
                continue;
            }

            UUID playerUUID = entry.getUUID("uuid");

            // If this UUID is itself a ghost of another UUID in the file, skip it.
            if (derivedGhosts.contains(playerUUID)) {
                continue;
            }

            UUID teamUUID = teamResolver.apply(playerUUID);

            if (teamUUID != null && !teamUUID.equals(playerUUID)) {
                if (!existingUuids.contains(teamUUID) && !addedTeams.contains(teamUUID)) {
                    CompoundTag newEntry = entry.copy();
                    newEntry.putUUID("uuid", teamUUID);
                    toAdd.add(newEntry);
                    existingUuids.add(teamUUID);
                    addedTeams.add(teamUUID);
                    modified = true;
                }
                continue;
            }

            UUID ghostTeamUUID = TeamIdentifier.toGhostTeamId(playerUUID);
            if (!existingUuids.contains(ghostTeamUUID)) {
                CompoundTag newEntry = entry.copy();
                newEntry.putUUID("uuid", ghostTeamUUID);
                toAdd.add(newEntry);
                existingUuids.add(ghostTeamUUID);
                modified = true;
            }
        }

        if (modified) {
            for (Tag tag : toAdd) {
                inventories.add(tag);
            }
        }

        return modified;
    }

    private static boolean migrateNbt(CompoundTag root, MinecraftServer server) {
        return migrateNbt(root, playerUUID -> resolveTeamUUID(playerUUID, server));
    }

    private static UUID resolveTeamUUID(UUID playerUUID, MinecraftServer server) {
        if (server == null) {
            return null;
        }
        if (!TeamLootrConfig.ENABLE_TEAMS || !FTBTeamsCompat.isLoaded()) {
            return null;
        }
        return FTBTeamsCompat.getTeamIdForPlayerId(playerUUID);
    }
}
