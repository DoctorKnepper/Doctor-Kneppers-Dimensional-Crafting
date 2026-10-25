package com.dimensionalcrafting.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Remembers which items in the crafting grid were pulled from remote containers, so that clearing
 * the grid (picking another recipe, or closing the table) sends them back where they came from
 * instead of dumping them into the player's inventory.
 */
public final class BorrowedItems {
	private record Loan(RemoteStock.Source from, ItemStack stack) {
	}

	private final List<Loan> loans = new ArrayList<>();

	public void record(final RemoteStock.Source from, final ItemStack taken) {
		for (Loan loan : this.loans) {
			if (loan.from().equals(from) && ItemStack.isSameItemSameComponents(loan.stack(), taken)) {
				loan.stack().grow(taken.getCount());
				return;
			}
		}

		this.loans.add(new Loan(from, taken.copy()));
	}

	/** How many items like {@code stack} are on loan. */
	public int amountLent(final ItemStack stack) {
		int total = 0;
		for (Loan loan : this.loans) {
			if (ItemStack.isSameItemSameComponents(loan.stack(), stack)) {
				total += loan.stack().getCount();
			}
		}

		return total;
	}

	/**
	 * Crafting consumes grid items, so never count more as borrowed than is still in the grid.
	 * The most recent loans are written off first.
	 */
	public void clampTo(final List<Slot> grid) {
		for (int i = this.loans.size() - 1; i >= 0; i--) {
			ItemStack lent = this.loans.get(i).stack();
			int excess = this.amountLent(lent) - countInGrid(grid, lent);
			if (excess > 0) {
				lent.shrink(Math.min(excess, lent.getCount()));
				if (lent.isEmpty()) {
					this.loans.remove(i);
				}
			}
		}
	}

	/** Moves borrowed items out of the grid and back into their containers. Anything that can't go back stays in the grid. */
	public void returnFrom(final List<Slot> grid, final ServerPlayer player) {
		this.clampTo(grid);
		// Clear first: moving items out of the grid fires slotsChanged, which calls back into clampTo.
		List<Loan> toReturn = List.copyOf(this.loans);
		this.loans.clear();
		for (Loan loan : toReturn) {
			Container target = RemoteStock.resolve(player, loan.from());
			if (target == null) {
				continue;
			}

			int remaining = loan.stack().getCount();
			for (Slot slot : grid) {
				ItemStack inGrid = slot.getItem();
				if (remaining > 0 && ItemStack.isSameItemSameComponents(inGrid, loan.stack())) {
					int moved = insert(target, inGrid, Math.min(remaining, inGrid.getCount()));
					remaining -= moved;
					if (moved > 0) {
						slot.set(inGrid.isEmpty() ? ItemStack.EMPTY : inGrid);
					}
				}
			}
		}
	}

	private static int countInGrid(final List<Slot> grid, final ItemStack like) {
		int count = 0;
		for (Slot slot : grid) {
			if (ItemStack.isSameItemSameComponents(slot.getItem(), like)) {
				count += slot.getItem().getCount();
			}
		}

		return count;
	}

	/** Moves up to {@code max} items from {@code from} into {@code container}, topping up matching stacks before using empty slots. */
	private static int insert(final Container container, final ItemStack from, final int max) {
		int moved = 0;
		for (int pass = 0; pass < 2 && moved < max; pass++) {
			for (int slot = 0; slot < container.getContainerSize() && moved < max; slot++) {
				ItemStack existing = container.getItem(slot);
				if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, from)) {
					int room = container.getMaxStackSize(existing) - existing.getCount();
					int n = Math.min(room, max - moved);
					if (n > 0) {
						existing.grow(n);
						from.shrink(n);
						moved += n;
					}
				} else if (pass == 1 && existing.isEmpty() && container.canPlaceItem(slot, from)) {
					int n = Math.min(container.getMaxStackSize(from), max - moved);
					container.setItem(slot, from.split(n));
					moved += n;
				}
			}
		}

		if (moved > 0) {
			container.setChanged();
		}

		return moved;
	}
}
