package com.yami.yamidelight.feast.mixin;

import com.yami.yamidelight.feast.FeastContent;
import com.yami.yamidelight.feast.FoxHeadFoodData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

/** FD normally discards container components. Transfer them only for this one meal, at serving time. */
@Mixin(CookingPotBlockEntity.class)
public abstract class FoxBrainCookingPotMixin {
    @Inject(method = "useHeldItemOnMeal", at = @At("HEAD"), cancellable = true, remap = false)
    private void yami$serveHeld(ItemStack container, CallbackInfoReturnable<ItemStack> cir) {
        CookingPotBlockEntity pot = (CookingPotBlockEntity)(Object)this;
        if (!pot.getMeal().is(FeastContent.ROASTED_AGED_FOX_BRAIN_ITEM.get())) return;
        if (!container.is(FeastContent.FOX_BRAIN_BOWL_ITEM.get()) || pot.getLevel() == null) {
            cir.setReturnValue(ItemStack.EMPTY); return;
        }
        ItemStack result = FoxHeadFoodData.create(FeastContent.ROASTED_AGED_FOX_BRAIN_ITEM.get(), container, pot.getLevel().registryAccess());
        container.shrink(1);
        pot.getInventory().extractItem(CookingPotBlockEntity.MEAL_DISPLAY_SLOT, 1, false);
        pot.setChanged();
        cir.setReturnValue(result);
    }

    @Inject(method = "useStoredContainersOnMeal", at = @At("HEAD"), cancellable = true, remap = false)
    private void yami$serveStored(CallbackInfo ci) {
        CookingPotBlockEntity pot = (CookingPotBlockEntity)(Object)this;
        ItemStack meal = pot.getMeal();
        if (!meal.is(FeastContent.ROASTED_AGED_FOX_BRAIN_ITEM.get())) return;
        ci.cancel();
        if (pot.getLevel() == null || pot.getLevel().isClientSide) return;
        var inventory = pot.getInventory();
        ItemStack bowl = inventory.getStackInSlot(CookingPotBlockEntity.CONTAINER_SLOT);
        if (!bowl.is(FeastContent.FOX_BRAIN_BOWL_ITEM.get())) return;
        ItemStack result = FoxHeadFoodData.create(FeastContent.ROASTED_AGED_FOX_BRAIN_ITEM.get(), bowl, pot.getLevel().registryAccess());
        ItemStack output = inventory.getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty() && !ItemStack.isSameItemSameComponents(result, output)) return;
        int amount = Math.min(Math.min(meal.getCount(), bowl.getCount()), result.getMaxStackSize() - output.getCount());
        if (amount <= 0) return;
        result.setCount(output.getCount() + amount);
        inventory.extractItem(CookingPotBlockEntity.MEAL_DISPLAY_SLOT, amount, false);
        inventory.extractItem(CookingPotBlockEntity.CONTAINER_SLOT, amount, false);
        inventory.setStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT, result);
        pot.setChanged();
    }
}
