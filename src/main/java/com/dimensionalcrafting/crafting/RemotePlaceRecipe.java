package com.dimensionalcrafting.crafting;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Vanilla's {@link ServerPlaceRecipe}, except that ingredients the player's inventory doesn't have are
 * pulled from their {@link RemoteStock}. Inventory items are always used first, and anything
 * borrowed from a remote container goes back there when the grid is cleared.
 */
public final class RemotePlaceRecipe {
	private final ServerPlaceRecipe.CraftingMenuAccess<CraftingRecipe> menu;
	private final Inventory inventory;
	private final RemoteStock stock;
	private final BorrowedItems borrowed;
	private final ServerPlayer player;
	private final boolean useMaxItems;
	private final int gridWidth;
	private final int gridHeight;
	private final List<Slot> inputGridSlots;

	public static RecipeBookMenu.PostPlaceAction placeRecipe(
		final ServerPlaceRecipe.CraftingMenuAccess<CraftingRecipe> menu,
		final int gridWidth,
		final int gridHeight,
		final List<Slot> inputGridSlots,
		final Inventory inventory,
		final RemoteStock stock,
		final BorrowedItems borrowed,
		final RecipeHolder<CraftingRecipe> recipe,
		final boolean useMaxItems,
		final boolean allowDroppingItemsToClear
	) {
		RemotePlaceRecipe placer = new RemotePlaceRecipe(menu, inventory, stock, borrowed, useMaxItems, gridWidth, gridHeight, inputGridSlots);
		if (!allowDroppingItemsToClear && !placer.testClearGrid()) {
			return RecipeBookMenu.PostPlaceAction.NOTHING;
		}

		StackedItemContents availableItems = new StackedItemContents();
		inventory.fillStackedContents(availableItems);
		menu.fillCraftSlotsStackedContents(availableItems);
		stock.fillStackedContents(availableItems);
		return placer.tryPlaceRecipe(recipe, availableItems);
	}

	private RemotePlaceRecipe(
		final ServerPlaceRecipe.CraftingMenuAccess<CraftingRecipe> menu,
		final Inventory inventory,
		final RemoteStock stock,
		final BorrowedItems borrowed,
		final boolean useMaxItems,
		final int gridWidth,
		final int gridHeight,
		final List<Slot> inputGridSlots
	) {
		this.menu = menu;
		this.inventory = inventory;
		this.stock = stock;
		this.borrowed = borrowed;
		this.player = (ServerPlayer)inventory.player;
		this.useMaxItems = useMaxItems;
		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.inputGridSlots = inputGridSlots;
	}

	private RecipeBookMenu.PostPlaceAction tryPlaceRecipe(final RecipeHolder<CraftingRecipe> recipe, final StackedItemContents availableItems) {
		if (availableItems.canCraft(recipe.value(), null)) {
			this.placeRecipe(recipe, availableItems);
			this.inventory.setChanged();
			return RecipeBookMenu.PostPlaceAction.NOTHING;
		} else {
			this.clearGrid();
			this.inventory.setChanged();
			return RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE;
		}
	}

	private void clearGrid() {
		this.borrowed.returnFrom(this.inputGridSlots, this.player);
		for (Slot slot : this.inputGridSlots) {
			ItemStack itemStackCopy = slot.getItem().copy();
			this.inventory.placeItemBackInInventory(itemStackCopy, false, Prediction.SERVER_ONLY);
			slot.set(itemStackCopy);
		}

		this.menu.clearCraftingContent();
	}

	private void placeRecipe(final RecipeHolder<CraftingRecipe> recipe, final StackedItemContents availableItems) {
		boolean recipeMatchesPlaced = this.menu.recipeMatches(recipe);
		int biggestCraftableStack = availableItems.getBiggestCraftableStack(recipe.value(), null);
		if (recipeMatchesPlaced) {
			for (Slot inputSlot : this.inputGridSlots) {
				ItemStack itemStack = inputSlot.getItem();
				if (!itemStack.isEmpty() && Math.min(biggestCraftableStack, itemStack.getMaxStackSize()) < itemStack.getCount() + 1) {
					return;
				}
			}
		}

		int amountToCraft = this.calculateAmountToCraft(biggestCraftableStack, recipeMatchesPlaced);
		List<Holder<Item>> itemsUsedPerIngredient = new ArrayList<>();
		if (availableItems.canCraft(recipe.value(), amountToCraft, itemsUsedPerIngredient::add)) {
			int adjustedAmountToCraft = clampToMaxStackSize(amountToCraft, itemsUsedPerIngredient);
			if (adjustedAmountToCraft != amountToCraft) {
				itemsUsedPerIngredient.clear();
				if (!availableItems.canCraft(recipe.value(), adjustedAmountToCraft, itemsUsedPerIngredient::add)) {
					return;
				}
			}

			this.clearGrid();
			PlaceRecipeHelper.placeRecipe(
				this.gridWidth,
				this.gridHeight,
				recipe.value(),
				recipe.value().placementInfo().slotsToIngredientIndex(),
				(ingredientIndex, gridIndex, gridXPos, gridYPos) -> {
					if (ingredientIndex != -1) {
						Slot targetGridSlot = this.inputGridSlots.get(gridIndex);
						Holder<Item> itemUsed = itemsUsedPerIngredient.get(ingredientIndex);
						int remainingCount = adjustedAmountToCraft;

						while (remainingCount > 0) {
							remainingCount = this.moveItemToGrid(targetGridSlot, itemUsed, remainingCount);
							if (remainingCount == -1) {
								return;
							}
						}
					}
				}
			);
		}
	}

	private static int clampToMaxStackSize(int value, final List<Holder<Item>> items) {
		for (Holder<Item> item : items) {
			value = Math.min(value, item.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
		}

		return value;
	}

	private int calculateAmountToCraft(final int biggestCraftableStack, final boolean recipeMatchesPlaced) {
		if (this.useMaxItems) {
			return biggestCraftableStack;
		}

		if (recipeMatchesPlaced) {
			int smallestStackSize = Integer.MAX_VALUE;

			for (Slot inputSlot : this.inputGridSlots) {
				ItemStack itemStack = inputSlot.getItem();
				if (!itemStack.isEmpty() && smallestStackSize > itemStack.getCount()) {
					smallestStackSize = itemStack.getCount();
				}
			}

			if (smallestStackSize != Integer.MAX_VALUE) {
				smallestStackSize++;
			}

			return smallestStackSize;
		} else {
			return 1;
		}
	}

	private int moveItemToGrid(final Slot targetSlot, final Holder<Item> item, final int count) {
		ItemStack itemInTargetSlot = targetSlot.getItem();
		ItemStack takenStack;
		int inventorySlotId = this.inventory.findSlotMatchingCraftingIngredient(item, itemInTargetSlot);
		if (inventorySlotId != -1) {
			ItemStack inventoryItem = this.inventory.getItem(inventorySlotId);
			if (count < inventoryItem.getCount()) {
				takenStack = this.inventory.removeItem(inventorySlotId, count);
			} else {
				takenStack = this.inventory.removeItemNoUpdate(inventorySlotId);
			}
		} else {
			takenStack = this.stock.take(item, itemInTargetSlot, count, this.borrowed::record);
			if (takenStack.isEmpty()) {
				return -1;
			}
		}

		int takenCount = takenStack.getCount();
		if (itemInTargetSlot.isEmpty()) {
			targetSlot.set(takenStack);
		} else {
			itemInTargetSlot.grow(takenCount);
		}

		return count - takenCount;
	}

	private boolean testClearGrid() {
		List<ItemStack> freeSlots = Lists.newArrayList();
		List<ItemStack> goingBack = Lists.newArrayList();
		int freeSlotsInInventory = this.getAmountOfFreeSlotsInInventory();

		for (Slot inputSlot : this.inputGridSlots) {
			ItemStack itemStack = inputSlot.getItem().copy();
			// Borrowed items go back to their chests, not the inventory.
			int alreadyGoingBack = goingBack.stream().filter(s -> ItemStack.isSameItemSameComponents(s, itemStack)).mapToInt(ItemStack::getCount).sum();
			int returning = Math.min(itemStack.getCount(), this.borrowed.amountLent(itemStack) - alreadyGoingBack);
			if (returning > 0) {
				goingBack.add(itemStack.split(returning));
			}

			if (!itemStack.isEmpty()) {
				int slotId = this.inventory.getSlotWithRemainingSpace(itemStack);
				if (slotId == -1 && freeSlots.size() <= freeSlotsInInventory) {
					for (ItemStack itemStackInList : freeSlots) {
						if (ItemStack.isSameItem(itemStackInList, itemStack)
							&& itemStackInList.getCount() != itemStackInList.getMaxStackSize()
							&& itemStackInList.getCount() + itemStack.getCount() <= itemStackInList.getMaxStackSize()) {
							itemStackInList.grow(itemStack.getCount());
							itemStack.setCount(0);
							break;
						}
					}

					if (!itemStack.isEmpty()) {
						if (freeSlots.size() >= freeSlotsInInventory) {
							return false;
						}

						freeSlots.add(itemStack);
					}
				} else if (slotId == -1) {
					return false;
				}
			}
		}

		return true;
	}

	private int getAmountOfFreeSlotsInInventory() {
		int freeSlots = 0;

		for (ItemStack item : this.inventory.getNonEquipmentItems()) {
			if (item.isEmpty()) {
				freeSlots++;
			}
		}

		return freeSlots;
	}
}
