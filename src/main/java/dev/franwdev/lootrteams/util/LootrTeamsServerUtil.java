package dev.franwdev.lootrteams.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.franwdev.lootrteams.mixins.AccessorLootrSavedData;
import dev.franwdev.lootrteams.team.FTBTeamsCompat;
import dev.franwdev.lootrteams.team.TeamLootrManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import noobanidus.mods.lootr.common.api.IOpeners;
import noobanidus.mods.lootr.common.api.data.ILootrInfoProvider;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.data.DataStorage;
import noobanidus.mods.lootr.common.data.LootrInventory;
import noobanidus.mods.lootr.common.data.LootrSavedData;

public class LootrTeamsServerUtil {

    public static void refreshOpeners(IOpeners openable) {
        if (!(openable instanceof ILootrInfoProvider provider)) {
            return;
        }

        Level level = provider.getInfoLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (TeamLootrManager.INSTANCE == null) {
            return;
        }

        LootrSavedData data = DataStorage.getData(provider);
        if (data == null) {
            return;
        }

        Set<UUID> newOpeners = new HashSet<>();

        Map<UUID, LootrInventory> inventories = ((AccessorLootrSavedData) data).lootrteams$getInventories();

        for (UUID id : inventories.keySet()) {
            // Check if it's a real FTB team
            Set<UUID> members = FTBTeamsCompat.getTeamMembers(id);
            if (!members.isEmpty()) {
                newOpeners.addAll(members);
                newOpeners.add(id); // Keep the team ID just in case
            } else {
                // It's either a ghost ID or a Player UUID
                // Is it a player UUID? We can check their current team.
                UUID teamId = TeamLootrManager.INSTANCE.getTeamId(id);

                // If their teamId is a real team, we only add them if that real team also opened the chest!
                if (!FTBTeamsCompat.getTeamMembers(teamId).isEmpty()) {
                    if (inventories.containsKey(teamId)) {
                        newOpeners.add(id);
                    }
                } else {
                    // They are solo (or this is a ghost ID being processed)
                    newOpeners.add(id);
                }
            }
        }

        Set<UUID> visualOpeners = data.getVisualOpeners();
        if (!visualOpeners.equals(newOpeners)) {
            visualOpeners.clear();
            visualOpeners.addAll(newOpeners);
            data.markChanged();

            if (openable instanceof BlockEntity be) {
                be.setChanged();
                if (openable instanceof ILootrBlockEntity tile) {
                    tile.updatePacketViaForce();
                }
            }
        }
    }
}
