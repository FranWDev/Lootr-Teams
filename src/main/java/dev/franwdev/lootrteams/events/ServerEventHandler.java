package dev.franwdev.lootrteams.events;

import dev.franwdev.lootrteams.LootrTeams;
import dev.franwdev.lootrteams.config.TeamLootrConfig;
import dev.franwdev.lootrteams.migration.LegacyMigrator;
import dev.franwdev.lootrteams.team.TeamLootrManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = LootrTeams.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (TeamLootrConfig.ENABLE_TEAMS && TeamLootrConfig.AUTO_MIGRATE) {
            LegacyMigrator.runIfNeeded(event.getServer());
        }
        
        // Clear storage manager cache on startup
        if (TeamLootrManager.INSTANCE != null) {
            TeamLootrManager.INSTANCE.getStorageManager().clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (TeamLootrManager.INSTANCE != null) {
            TeamLootrManager.INSTANCE.shutdown();
        }
    }
}

