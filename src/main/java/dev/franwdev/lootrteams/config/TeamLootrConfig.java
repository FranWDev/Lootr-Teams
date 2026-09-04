package dev.franwdev.lootrteams.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class TeamLootrConfig {

    public static final ForgeConfigSpec SPEC;

    public static boolean ENABLE_TEAMS       = true;
    public static boolean ENABLE_LEGACY_SYNC = true;
    public static boolean AUTO_MIGRATE       = true;
    public static boolean DEBUG_MODE         = true;
    public static boolean GLOBAL_SHARED_LOOT = false;

    private static final ForgeConfigSpec.BooleanValue CFG_ENABLE_TEAMS;
    private static final ForgeConfigSpec.BooleanValue CFG_ENABLE_LEGACY_SYNC;
    private static final ForgeConfigSpec.BooleanValue CFG_AUTO_MIGRATE;
    private static final ForgeConfigSpec.BooleanValue CFG_DEBUG_MODE;
    private static final ForgeConfigSpec.BooleanValue CFG_GLOBAL_SHARED_LOOT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("LootrTeams Configuration").push("general");

        CFG_ENABLE_TEAMS = builder
            .comment("Enable team-based shared loot.")
            .define("enableTeams", true);

        CFG_ENABLE_LEGACY_SYNC = builder
            .comment("Keep player-UUID entries in sync with team-UUID entries for compatibility.")
            .define("enableLegacySync", true);

        CFG_AUTO_MIGRATE = builder
            .comment("Automatically migrate existing world data on first load.")
            .define("autoMigrate", true);

        CFG_DEBUG_MODE = builder
            .comment("Enable verbose debug logging.")
            .define("debugMode", false);

        CFG_GLOBAL_SHARED_LOOT = builder
            .comment("If true, all players share a single global team inventory for all chests (making chests generic/shared).")
            .define("globalSharedLoot", false);

        builder.pop();
        SPEC = builder.build();
    }

    /** 
     * Bakes the config values to static fields for fast access.
     * Should be called in FMLCommonSetupEvent or after the config is loaded. 
     */
    public static void bake() {
        ENABLE_TEAMS       = CFG_ENABLE_TEAMS.get();
        ENABLE_LEGACY_SYNC = CFG_ENABLE_LEGACY_SYNC.get();
        AUTO_MIGRATE       = CFG_AUTO_MIGRATE.get();
        GLOBAL_SHARED_LOOT = CFG_GLOBAL_SHARED_LOOT.get();
        // DEBUG_MODE is controlled by the source code during tests, do not override
    }
}
