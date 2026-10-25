package com.dimensionalcrafting.client;

import com.dimensionalcrafting.crafting.RemoteStockPayload;
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** The last remote-chest summary the server sent, tied to the menu it was sent for. */
public final class ClientRemoteStock {
	private static WeakReference<AbstractContainerMenu> menu = new WeakReference<>(null);
	private static List<RemoteStockPayload.Entry> items = List.of();

	private ClientRemoteStock() {
	}

	public static void set(final AbstractContainerMenu forMenu, final List<RemoteStockPayload.Entry> newItems) {
		menu = new WeakReference<>(forMenu);
		items = List.copyOf(newItems);
	}

	/**
	 * Adds the remote items to the recipe book's counts. Only applies to the exact client menu
	 * the summary was sent for, so it never leaks into other screens or the integrated server's
	 * copy of the menu.
	 */
	public static void fill(final AbstractContainerMenu forMenu, final StackedItemContents contents) {
		if (menu.get() != forMenu) {
			return;
		}

		for (RemoteStockPayload.Entry entry : items) {
			int maxStackSize = entry.item().value().getDefaultMaxStackSize();
			for (int remaining = entry.count(); remaining > 0; remaining -= maxStackSize) {
				contents.accountStack(new ItemStack(entry.item(), Math.min(remaining, maxStackSize)));
			}
		}
	}
}
