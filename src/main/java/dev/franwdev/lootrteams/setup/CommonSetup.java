package dev.franwdev.lootrteams.setup;

import dev.franwdev.lootrteams.LootrTeams;
import dev.franwdev.lootrteams.config.TeamLootrConfig;
import dev.franwdev.lootrteams.team.FTBTeamsCompat;
import dev.franwdev.lootrteams.team.TeamLootrManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@EventBusSubscriber(modid = LootrTeams.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CommonSetup {

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TeamLootrConfig.bake();
            if (TeamLootrConfig.ENABLE_TEAMS) {
                TeamLootrManager.init();
                if (FTBTeamsCompat.isLoaded() && TeamLootrManager.INSTANCE != null) {
                    FTBTeamsCompat.registerEventHandlers(TeamLootrManager.INSTANCE.getStorageManager());
                }
            }
        });
    }
}

