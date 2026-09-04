package dev.franwdev.lootrteams.mixins;

import java.util.Map;
import java.util.UUID;

import noobanidus.mods.lootr.common.data.LootrInventory;
import noobanidus.mods.lootr.common.data.LootrSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LootrSavedData.class, remap = false)
public interface AccessorLootrSavedData {

    @Accessor("inventories")
    Map<UUID, LootrInventory> lootrteams$getInventories();
}

