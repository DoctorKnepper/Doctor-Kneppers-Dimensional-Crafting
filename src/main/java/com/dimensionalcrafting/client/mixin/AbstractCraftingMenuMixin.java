package com.dimensionalcrafting.client.mixin;

import com.dimensionalcrafting.client.ClientRemoteStock;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the recipe book count items sitting in the player's remote chests while a Dimensional Crafting Table is open. */
@Mixin(AbstractCraftingMenu.class)
public class AbstractCraftingMenuMixin {
	@Inject(method = "fillCraftSlotsStackedContents", at = @At("TAIL"))
	private void dimensionalcrafting$addRemoteStock(final StackedItemContents stackedContents, final CallbackInfo ci) {
		ClientRemoteStock.fill((AbstractContainerMenu)(Object)this, stackedContents);
	}
}
