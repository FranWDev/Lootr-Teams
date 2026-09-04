package dev.franwdev.lootrteams.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.franwdev.lootrteams.mixins.AccessorLootrSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import noobanidus.mods.lootr.common.api.LootFiller;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.data.LootrInventory;
import noobanidus.mods.lootr.common.data.LootrSavedData;

public class TestHelpers {

    public static final BlockPos CHEST_POS = new BlockPos(1, 1, 1);

    public static void setupChest(GameTestHelper helper, BlockPos pos) {
        ResourceLocation lootrChest = ResourceLocation.fromNamespaceAndPath("lootr", "chest");
        Block chestBlock = helper.getLevel().registryAccess()
            .registry(net.minecraft.core.registries.Registries.BLOCK)
            .flatMap(r -> r.getOptional(lootrChest))
            .orElse(null);

        if (chestBlock == null || chestBlock == Blocks.AIR) {
            System.err.println("[LootrTeamsTest] lootr:chest not found, falling back to vanilla chest");
            chestBlock = Blocks.CHEST;
        }

        helper.setBlock(pos, chestBlock);
        System.out.println("[LootrTeamsTest] Set block at relative " + pos + " to " + helper.getBlockState(pos));
    }

    /**
     * Creates a FakePlayer with the given UUID.
     * FakePlayer does not have a real GameProfile but works to simulate opening chests.
     */
    public static ServerPlayer makePlayer(GameTestHelper helper, UUID uuid, String name) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        GameProfile profile = new GameProfile(uuid, name);
        return new FakePlayer(level, profile);
    }

    /**
     * Gets the Lootr LootrSavedData for the chest at CHEST_POS inside the test structure.
     * Retrieves from the last opened inventory to avoid timing issues with data persistence.
     */
    public static LootrSavedData getChestData(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos worldPos = helper.absolutePos(CHEST_POS);
        BlockEntity be = level.getBlockEntity(worldPos);
        if (be instanceof ILootrBlockEntity lootrBE) {
            UUID chestUUID = lootrBE.getTileId();
            if (chestUUID != null) {
                return LootrSavedData.getContainerData(level, worldPos, chestUUID);
            }
        }
        return null;
    }

    /**
     * Simulates a player opening a chest (triggers LootrSavedData getInventory).
     * Builds a LootFiller from the block entity's loot table and seed.
     */
    public static LootrInventory openChest(GameTestHelper helper, ServerPlayer player) {
        return openChestAt(helper, player, CHEST_POS);
    }

    /**
     * Obtains the internal inventories map from LootrSavedData via Accessor.
     */
    public static Map<UUID, LootrInventory> getInventoryMap(LootrSavedData data) {
        return ((AccessorLootrSavedData) data).lootrteams$getInventories();
    }

    public static List<BlockPos> getAllChestPositions(GameTestHelper helper) {
        List<BlockPos> positions = new ArrayList<>();
        helper.forEveryBlockInStructure(pos -> {
            BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            if (be instanceof ILootrBlockEntity) {
                positions.add(pos.immutable());
            }
        });
        return positions;
    }

    public static LootrInventory openChestAt(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos worldPos = helper.absolutePos(pos);

        // Use helper methods which are safer in GameTest context
        BlockEntity be = helper.getBlockEntity(pos);
        if (be == null) {
            // Try forcing a tick update if not present
            helper.setBlock(pos, helper.getBlockState(pos));
            be = helper.getBlockEntity(pos);
        }

        if (be == null) {
            System.err.println("[LootrTeamsTest] BlockEntity at relative " + pos + " (absolute " + worldPos + ") is NULL! Block is: " + helper.getBlockState(pos));
            return null;
        }
        if (!(be instanceof ILootrBlockEntity lootrBE)) {
            System.err.println("[LootrTeamsTest] BlockEntity at relative " + pos + " is not an ILootrBlockEntity! Class: " + be.getClass().getName());
            return null;
        }

        UUID chestUUID = lootrBE.getTileId();
        // ILootrBlockEntity implements LootFiller directly
        LootFiller filler = (fillerPlayer, fillerContainer, fillerTable, fillerSeed) ->
            lootrBE.unpackLootTable(fillerPlayer, fillerContainer, fillerTable, fillerSeed);
        return LootrSavedData.getInventory(level, chestUUID, worldPos, player, lootrBE, filler);
    }

    public static LootrInventory getInventoryAt(GameTestHelper helper, BlockPos pos, UUID teamId) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos worldPos = helper.absolutePos(pos);
        BlockEntity be = level.getBlockEntity(worldPos);
        if (be instanceof ILootrBlockEntity lootrBE) {
            UUID chestUUID = lootrBE.getTileId();
            LootrSavedData data = LootrSavedData.getContainerData(level, worldPos, chestUUID);
            if (data != null) {
                return getInventoryMap(data).get(teamId);
            }
        }
        return null;
    }

    public static boolean clearPlayerInventories(GameTestHelper helper, UUID playerId) {
        LootrSavedData data = getChestData(helper);
        if (data != null) {
            return data.clearInventory(playerId);
        }
        return false;
    }

    public static void reset() {
        // No static state to reset
    }
}
