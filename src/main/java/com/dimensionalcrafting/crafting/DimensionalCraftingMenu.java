package com.dimensionalcrafting.crafting;

import com.dimensionalcrafting.DimensionalCrafting;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Server-side menu for the Dimensional Crafting Table. To the client it's an ordinary crafting
 * table (same menu type and screen); the difference is that recipe-book placement can pull
 * ingredients from the player's {@link RemoteStock}.
 */
public class DimensionalCraftingMenu extends CraftingMenu {
	private final ContainerLevelAccess access;
	private final ServerPlayer player;
	private final BorrowedItems borrowed = new BorrowedItems();

	public DimensionalCraftingMenu(final int containerId, final Inventory inventory, final ContainerLevelAccess access) {
		super(containerId, inventory, access);
		this.access = access;
		this.player = (ServerPlayer)inventory.player;
	}

	@Override
	public boolean stillValid(final Player player) {
		return stillValid(this.access, player, DimensionalCrafting.DIMENSIONAL_CRAFTING_TABLE);
	}

	@Override
	public RecipeBookMenu.PostPlaceAction handlePlacement(
		final boolean useMaxItems, final boolean allowDroppingItemsToClear, final RecipeHolder<?> recipe, final ServerLevel level, final Inventory inventory
	) {
		@SuppressWarnings("unchecked")
		RecipeHolder<CraftingRecipe> typedRecipe = (RecipeHolder<CraftingRecipe>)recipe;
		RemoteStock stock = RemoteStock.gather(this.player);
		this.beginPlacingRecipe();

		try {
			List<Slot> inputSlots = this.getInputGridSlots();
			return RemotePlaceRecipe.placeRecipe(new ServerPlaceRecipe.CraftingMenuAccess<CraftingRecipe>() {
				@Override
				public void fillCraftSlotsStackedContents(final StackedItemContents stackedContents) {
					DimensionalCraftingMenu.this.fillCraftSlotsStackedContents(stackedContents);
				}

				@Override
				public void clearCraftingContent() {
					DimensionalCraftingMenu.this.resultSlots.clearContent();
					DimensionalCraftingMenu.this.craftSlots.clearContent();
				}

				@Override
				public boolean recipeMatches(final RecipeHolder<CraftingRecipe> recipe) {
					return recipe.value().matches(DimensionalCraftingMenu.this.craftSlots.asCraftInput(), DimensionalCraftingMenu.this.owner().level());
				}
			}, 3, 3, inputSlots, inventory, stock, this.borrowed, typedRecipe, useMaxItems, allowDroppingItemsToClear);
		} finally {
			this.finishPlacingRecipe(level, typedRecipe);
			this.sendStock(stock);
		}
	}

	@Override
	public void slotsChanged(final Container container) {
		super.slotsChanged(container);
		this.borrowed.clampTo(this.getInputGridSlots());
	}

	@Override
	public void removed(final Player player) {
		// Send borrowed ingredients home first; vanilla then moves whatever is left into the inventory.
		this.borrowed.returnFrom(this.getInputGridSlots(), this.player);
		super.removed(player);
	}

	public void sendStock(final RemoteStock stock) {
		if (ServerPlayNetworking.canSend(this.player, RemoteStockPayload.TYPE)) {
			ServerPlayNetworking.send(this.player, new RemoteStockPayload(this.containerId, stock.summary()));
		}
	}
}
