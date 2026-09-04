package dev.franwdev.lootrteams.team;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FTBTeamsCompat {

    private static final Logger LOG = LogManager.getLogger("lootrteams");
    private static final String MOD_ID = "ftbteams";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Returns the party team UUID for a player, or null if they are not in a party.
     */
    public static UUID getTeamId(ServerPlayer player) {
        if (!LOADED) return null;

        try {
            Object team = getTeamForPlayer(player);
            if (team == null) return null;

            if (!(boolean) team.getClass().getMethod("isPartyTeam").invoke(team)) {
                return null;
            }

            return readTeamId(team);
        } catch (Throwable e) {
            LOG.debug("[LootrTeams] Failed to resolve team ID", e);
            return null;
        }
    }

    /**
     * Returns the party team UUID for a player UUID, or null if they are not in a party.
     */
    public static UUID getTeamIdForPlayerId(UUID playerId) {
        if (!LOADED) return null;

        try {
            Object team = getTeamForPlayerId(playerId);
            if (team == null) return null;

            if (!(boolean) team.getClass().getMethod("isPartyTeam").invoke(team)) {
                return null;
            }

            return readTeamId(team);
        } catch (Throwable e) {
            LOG.debug("[LootrTeams] Failed to resolve team ID for player UUID", e);
            return null;
        }
    }

    /**
     * Returns all member UUIDs for a team, or an empty set if unavailable.
     */
    public static Set<UUID> getTeamMembers(UUID teamId) {
        if (!LOADED) return Collections.emptySet();

        try {
            Object team = getTeamById(teamId);
            if (team == null) return Collections.emptySet();

            Method getMembers;
            try {
                getMembers = team.getClass().getMethod("getMembers");
            } catch (NoSuchMethodException e) {
                LOG.warn("[LootrTeams] FTB Teams Team.getMembers() not found - sync disabled. Check FTB Teams version.");
                return Collections.emptySet();
            }

            @SuppressWarnings("unchecked")
            Set<UUID> members = (Set<UUID>) getMembers.invoke(team);
            return members != null ? members : Collections.emptySet();
        } catch (Throwable e) {
            LOG.debug("[LootrTeams] Failed to resolve team members", e);
            return Collections.emptySet();
        }
    }

    public static void registerEventHandlers(TeamStorageManager storageManager) {
        if (!LOADED) return;

        try {
            registerTeamEvent("dev.ftb.mods.ftbteams.api.event.TeamEvent", "PLAYER_JOINED_PARTY",
                event -> handlePlayerJoined(event, storageManager));
            registerTeamEvent("dev.ftb.mods.ftbteams.api.event.TeamEvent", "PLAYER_LEFT_PARTY",
                event -> handlePlayerLeft(event, storageManager));
            registerTeamEvent("dev.ftb.mods.ftbteams.api.event.TeamEvent", "PLAYER_CHANGED",
                event -> handlePlayerChanged(event, storageManager));
            registerTeamEvent("dev.ftb.mods.ftbteams.api.event.TeamManagerEvent", "CREATED",
                event -> handleTeamManager(event, storageManager));
            registerTeamEvent("dev.ftb.mods.ftbteams.api.event.TeamManagerEvent", "LOADED",
                event -> handleTeamManager(event, storageManager));

            if (dev.franwdev.lootrteams.config.TeamLootrConfig.DEBUG_MODE) {
                LOG.info("[LootrTeams] Registered FTB Teams event handlers.");
            }
        } catch (Throwable e) {
            LOG.debug("[LootrTeams] Failed to register FTB Teams event handlers", e);
        }
    }

    private static Object getTeamForPlayer(ServerPlayer player) throws Exception {
        Object api = getApi();
        if (api == null) return null;
        Object manager = api.getClass().getMethod("getManager").invoke(api);
        Method getTeamForPlayer = manager.getClass().getMethod("getTeamForPlayer", ServerPlayer.class);
        Optional<?> team = (Optional<?>) getTeamForPlayer.invoke(manager, player);
        return team.orElse(null);
    }

    private static Object getTeamForPlayerId(UUID playerId) throws Exception {
        Object api = getApi();
        if (api == null) return null;
        Object manager = api.getClass().getMethod("getManager").invoke(api);
        Method getTeamForPlayerId = manager.getClass().getMethod("getTeamForPlayerID", UUID.class);
        Optional<?> team = (Optional<?>) getTeamForPlayerId.invoke(manager, playerId);
        return team.orElse(null);
    }

    private static Object getTeamById(UUID teamId) throws Exception {
        Object api = getApi();
        if (api == null) return null;
        Object manager = api.getClass().getMethod("getManager").invoke(api);
        Method getTeamById = manager.getClass().getMethod("getTeamByID", UUID.class);
        Optional<?> team = (Optional<?>) getTeamById.invoke(manager, teamId);
        return team.orElse(null);
    }

    private static Object getApi() throws Exception {
        Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
        Method apiMethod = apiClass.getMethod("api");
        return apiMethod.invoke(null);
    }

    private static UUID readTeamId(Object team) throws Exception {
        Method getTeamId = team.getClass().getMethod("getTeamId");
        return (UUID) getTeamId.invoke(team);
    }

    private static void registerTeamEvent(String ownerClassName, String fieldName, Consumer<Object> handler) throws Exception {
        Class<?> ownerClass = Class.forName(ownerClassName);
        Object event = ownerClass.getField(fieldName).get(null);
        Method register = findRegisterMethod(event.getClass());
        register.invoke(event, handler);
    }

    private static Method findRegisterMethod(Class<?> eventClass) throws NoSuchMethodException {
        for (Method method : eventClass.getMethods()) {
            if (method.getName().equals("register") && method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new NoSuchMethodException("register method not found on " + eventClass.getName());
    }

    private static void handlePlayerJoined(Object event, TeamStorageManager storageManager) {
        try {
            Object team = event.getClass().getMethod("getTeam").invoke(event);
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            if (team == null || player == null) return;

            UUID teamId = (UUID) team.getClass().getMethod("getId").invoke(team);
            UUID playerId = (UUID) player.getClass().getMethod("getUUID").invoke(player);
            storageManager.updatePlayerTeam(playerId, teamId);
        } catch (Throwable e) {
            LOG.debug("[LootrTeams] Failed to handle player join event", e);
        }
    }

    private static void handlePlayerLeft(Object event, TeamStorageManager storageManager) {
        try {
            UUID playerId = null;
            try {
                playerId = (UUID) event.getClass().getMethod("getPlayerId").invoke(event);
            } catch (NoSuchMethodException e) {
                Object player = event.getClass().getMethod("getPlayer").invoke(event);
                if (player != null) {
                    playerId = (UUID) player.getClass().getMethod("getUUID").invoke(player);
                }
            }
            if (playerId == null) return;
            UUID ghostId = TeamIdentifier.toGhostTeamId(playerId);
            storageManager.updatePlayerTeam(playerId, ghostId);
        } catch (Throwable e) {
            LOG.debug("[LootrTeams] Failed to handle player left event", e);
        }
    }

    private static void handlePlayerChanged(Object event, TeamStorageManager storageManager) {
        try {
            Object team = event.getClass().getMethod("getTeam").invoke(event);
            UUID playerId = (UUID) event.getClass().getMethod("getPlayerId").invoke(event);
            if (team == null || playerId == null) return;

            UUID teamId = resolveTeamId(team, playerId);
            storageManager.updatePlayerTeam(playerId, teamId);
        } catch (Throwable e) {
            LOG.debug("[LootrTeams] Failed to handle player change event", e);
        }
    }

    private static void handleTeamManager(Object event, TeamStorageManager storageManager) {
        // Team manager events are not needed since team IDs are resolved in vivo
    }

    private static UUID resolveTeamId(Object team, UUID playerId) throws Exception {
        boolean isParty = (boolean) team.getClass().getMethod("isPartyTeam").invoke(team);
        if (isParty) {
            return (UUID) team.getClass().getMethod("getId").invoke(team);
        }
        return TeamIdentifier.toGhostTeamId(playerId);
    }
}
