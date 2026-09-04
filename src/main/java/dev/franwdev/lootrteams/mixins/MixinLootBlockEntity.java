package dev.franwdev.lootrteams.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.franwdev.lootrteams.util.LootrTeamsServerUtil;
import net.minecraft.nbt.CompoundTag;
import noobanidus.mods.lootr.api.blockentity.ILootBlockEntity;
import noobanidus.mods.lootr.block.entities.LootrChestBlockEntity;
import noobanidus.mods.lootr.block.entities.LootrInventoryBlockEntity;
import noobanidus.mods.lootr.block.entities.LootrShulkerBlockEntity;
import noobanidus.mods.lootr.block.entities.LootrBarrelBlockEntity;

@Mixin(value = {
    LootrChestBlockEntity.class, 
    LootrInventoryBlockEntity.class, 
    LootrShulkerBlockEntity.class, 
    LootrBarrelBlockEntity.class
}, remap = false)
public abstract class MixinLootBlockEntity {

    @Inject(method = "getUpdateTag()Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), remap = true, require = 0)
    private void onGetUpdateTag(CallbackInfoReturnable<CompoundTag> cir) {
        LootrTeamsServerUtil.refreshOpeners((ILootBlockEntity) this);
    }
}
