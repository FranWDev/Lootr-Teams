package dev.franwdev.lootrteams.mixins;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.franwdev.lootrteams.LootrTeams;
import dev.franwdev.lootrteams.config.TeamLootrConfig;
import dev.franwdev.lootrteams.team.TeamIdentifier;
import dev.franwdev.lootrteams.team.TeamLootrManager;
import net.minecraft.server.level.ServerPlayer;
import noobanidus.mods.lootr.common.data.LootrInventory;
import noobanidus.mods.lootr.common.data.LootrSavedData;

@Mixin(value = LootrSavedData.class, remap = false)
public abstract class MixinLootrSavedData {

    @Shadow
    private Map<UUID, LootrInventory> inventories;

    /**
     * Intercepts getInventory to return the team's shared inventory instead of the
     * player's private one.
     */
    @Inject(method = "getInventory(Ljava/util/UUID;)Lnoobanidus/mods/lootr/common/data/LootrInventory;", at = @At("HEAD"), cancellable = true, remap = false)
    private void teamGetInventory(UUID playerId, CallbackInfoReturnable<LootrInventory> cir) {
        if (!TeamLootrConfig.ENABLE_TEAMS || TeamLootrManager.INSTANCE == null) {
            return;
        }

        UUID teamId = TeamLootrManager.INSTANCE.getTeamId(playerId);
        LootrInventory teamInv = inventories.get(teamId);

        if (teamInv == null) {
            LootrInventory existingInv = null;
            UUID ghostId = TeamIdentifier.toGhostTeamId(playerId);

            // Check if the current player's ghost ID has an inventory
            existingInv = inventories.get(ghostId);

            // If it's a real team and the current player didn't have one, check other members
            if (existingInv == null && !teamId.equals(ghostId)) {
                Set<UUID> members = TeamLootrManager.INSTANCE.getStorageManager().getPlayersInTeam(teamId);
                for (UUID memberId : members) {
                    if (memberId.equals(playerId))
                        continue;
                    UUID memberGhostId = TeamIdentifier.toGhostTeamId(memberId);
                    existingInv = inventories.get(memberGhostId);
                    if (existingInv != null) {
                        if (TeamLootrConfig.DEBUG_MODE) {
                            LootrTeams.LOG.info("[LootrTeams] Team {} inherits loot from ghost entry of player {}",
                                    teamId, memberId);
                        }
                        break;
                    }
                }
            }

            // Fallback for vanilla migration
            if (existingInv == null && teamId.equals(ghostId)) {
                existingInv = inventories.get(playerId);
                if (existingInv != null) {
                    if (TeamLootrConfig.DEBUG_MODE) {
                        LootrTeams.LOG.info("[LootrTeams] Player {} inherits loot from playerUUID entry for ghost team",
                                playerId);
                    }
                }
            }

            if (existingInv != null) {
                // Promote to the current teamId (works for both ghost promoting to real team,
                // and playerUUID promoting to ghost)
                inventories.put(teamId, existingInv);
                ((LootrSavedData) (Object) this).setDirty();

                // Notify the storage manager for future synchronization
                TeamLootrManager.INSTANCE.getStorageManager().onInventoryCreated(teamId, playerId, existingInv);

                cir.setReturnValue(existingInv);
                return;
            }
        }

        if (teamInv != null) {
            TeamLootrManager.INSTANCE.getStorageManager().onInventoryCreated(teamId, playerId, teamInv);
            if (TeamLootrConfig.DEBUG_MODE) {
                LootrTeams.LOG.info("[LootrTeams] Player {} is opening chest with teamId {}",
                        playerId, teamId);
            }
        }

        cir.setReturnValue(teamInv);
    }

    /**
     * Intercepts clearInventories to also clear the team's shared inventory or the
     * ghost team inventory.
     * This ensures that /lootr clear <player> works correctly for team-based loot.
     */
    @Inject(method = "clearInventories(Ljava/util/UUID;)Z", at = @At("HEAD"), remap = false)
    private void onClearInventories(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        if (!TeamLootrConfig.ENABLE_TEAMS || TeamLootrManager.INSTANCE == null) {
            return;
        }

        // If the UUID being cleared is a player, also clear their team inventory
        UUID teamId = TeamLootrManager.INSTANCE.getStorageManager().getTeamForPlayer(uuid);
        if (teamId != null && !teamId.equals(uuid)) {
            this.inventories.remove(teamId);
        }

        // Also clear ghost team entry
        UUID ghostId = TeamIdentifier.toGhostTeamId(uuid);
        if (!ghostId.equals(uuid)) {
            this.inventories.remove(ghostId);
        }
    }

    /**
     * Intercepts the map 'put' operation in all createInventory variants.
     * It redirects the put to use the team UUID instead of the player UUID.
     */
    @Redirect(method = {
            "createInventory(Lnoobanidus/mods/lootr/common/api/data/ILootrInfoProvider;Lnet/minecraft/server/level/ServerPlayer;Lnoobanidus/mods/lootr/common/api/data/LootFiller;)Lnoobanidus/mods/lootr/common/data/LootrInventory;",
            "createInventory(Lnoobanidus/mods/lootr/common/api/data/ILootrInfoProvider;Ljava/util/UUID;Lnoobanidus/mods/lootr/common/api/data/LootFiller;)Lnoobanidus/mods/lootr/common/data/LootrInventory;"
    }, at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private Object teamPutInventory(Map<UUID, LootrInventory> map, Object keyPlayerUUID, Object value) {
        if (!TeamLootrConfig.ENABLE_TEAMS || TeamLootrManager.INSTANCE == null) {
            return map.put((UUID) keyPlayerUUID, (LootrInventory) value);
        }

        UUID playerId = (UUID) keyPlayerUUID;
        UUID teamId = TeamLootrManager.INSTANCE.getTeamId(playerId);

        if (TeamLootrConfig.DEBUG_MODE) {
            LootrTeams.LOG.info("[LootrTeams] Creating inventory for teamId {} (triggered by player {})", teamId,
                    playerId);
        }

        // Notify the storage manager for future synchronization
        TeamLootrManager.INSTANCE.getStorageManager()
                .onInventoryCreated(teamId, playerId, (LootrInventory) value);

        Object result = map.put(teamId, (LootrInventory) value);

        if ("true".equals(System.getProperty("lootrteams.testMode"))) {
            TeamLootrManager.INSTANCE.synchronizer.processTaskImmediate((LootrSavedData) (Object) this, teamId);
        } else {
            TeamLootrManager.INSTANCE.synchronizer
                    .scheduleSyncToPlayers((LootrSavedData) (Object) this, teamId);
        }

        return result;
    }
}

