package com.dimensionalcrafting.crafting;

import com.dimensionalcrafting.storage.ContainerOwnership;
import com.dimensionalcrafting.storage.OwnedContainers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Everything a player can craft with besides their own inventory: their ender chest plus
 * every chest and barrel they own, in any dimension and at any distance. Containers in
 * unloaded chunks are loaded on demand; nearest containers come first so they're used up first.
 */
public final class RemoteStock {
	/** Where an item came from: an owned chest or barrel, or (when {@code pos} is null) the player's ender chest. */
	public record Source(@Nullable GlobalPos pos) {
		public static final Source ENDER_CHEST = new Source(null);
	}

	private record Entry(Source source, Container container) {
	}

	private final List<Entry> containers;
	private final int ownedContainerCount;

	private RemoteStock(final List<Entry> containers, final int ownedContainerCount) {
		this.containers = containers;
		this.ownedContainerCount = ownedContainerCount;
	}

	public static RemoteStock gather(final ServerPlayer player) {
		ServerLevel playerLevel = player.level();
		MinecraftServer server = playerLevel.getServer();
		OwnedContainers owned = OwnedContainers.get(server);
		List<GlobalPos> positions = new ArrayList<>(owned.ownedBy(player.getUUID()));
		BlockPos origin = player.blockPosition();
		positions.sort(Comparator.<GlobalPos>comparingInt(p -> p.dimension() == playerLevel.dimension() ? 0 : 1)
			.thenComparingDouble(p -> p.pos().distSqr(origin)));

		List<Entry> containers = new ArrayList<>(positions.size() + 1);
		containers.add(new Entry(Source.ENDER_CHEST, player.getEnderChestInventory()));
		int ownedCount = 0;
		for (GlobalPos globalPos : positions) {
			Container container = loadOwnedContainer(server, owned, globalPos);
			if (container != null) {
				containers.add(new Entry(new Source(globalPos), container));
				ownedCount++;
			}
		}

		return new RemoteStock(containers, ownedCount);
	}

	/** Finds the container an item was taken from again, if it still exists and still belongs to the player. */
	public static @Nullable Container resolve(final ServerPlayer player, final Source source) {
		if (source.pos() == null) {
			return player.getEnderChestInventory();
		}

		OwnedContainers owned = OwnedContainers.get(player.level().getServer());
		if (!player.getUUID().equals(owned.ownerOf(source.pos()))) {
			return null;
		}

		return loadOwnedContainer(player.level().getServer(), owned, source.pos());
	}

	/** Loads the chunk if needed. Drops the ownership record if the container is gone. */
	private static @Nullable Container loadOwnedContainer(final MinecraftServer server, final OwnedContainers owned, final GlobalPos globalPos) {
		ServerLevel level = server.getLevel(globalPos.dimension());
		if (level == null) {
			// Dimension isn't loaded right now (e.g. a datapack was removed); keep the record in case it comes back.
			return null;
		}

		BlockPos pos = globalPos.pos();
		if (!level.isInWorldBounds(pos)) {
			owned.remove(globalPos);
			return null;
		}

		LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		if (ContainerOwnership.isTrackable(chunk.getBlockState(pos)) && chunk.getBlockEntity(pos) instanceof Container container) {
			return container;
		}

		// Broken by an explosion, replaced with a command, etc.
		owned.remove(globalPos);
		return null;
	}

	public int ownedContainerCount() {
		return this.ownedContainerCount;
	}

	public void fillStackedContents(final StackedItemContents contents) {
		for (Entry entry : this.containers) {
			Container container = entry.container();
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				contents.accountSimpleStack(container.getItem(slot));
			}
		}
	}

	/**
	 * Removes up to {@code count} of {@code item} from a single remote slot, using the same rules
	 * as vanilla's recipe book: no damaged, enchanted or renamed items, and matching components
	 * if the grid slot already holds something. {@code onTaken} is told where the items came from.
	 */
	public ItemStack take(final Holder<Item> item, final ItemStack existingInGrid, final int count, final BiConsumer<Source, ItemStack> onTaken) {
		for (Entry entry : this.containers) {
			Container container = entry.container();
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty()
					&& stack.is(item)
					&& Inventory.isUsableForCrafting(stack)
					&& (existingInGrid.isEmpty() || ItemStack.isSameItemSameComponents(existingInGrid, stack))) {
					ItemStack taken = container.removeItem(slot, count);
					container.setChanged();
					onTaken.accept(entry.source(), taken.copy());
					return taken;
				}
			}
		}

		return ItemStack.EMPTY;
	}

	/** Total count of each craftable item, for the client's recipe book. */
	public List<RemoteStockPayload.Entry> summary() {
		Map<Holder<Item>, Integer> totals = new LinkedHashMap<>();
		for (Entry entry : this.containers) {
			Container container = entry.container();
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty() && Inventory.isUsableForCrafting(stack)) {
					totals.merge(stack.typeHolder(), stack.getCount(), Integer::sum);
				}
			}
		}

		List<RemoteStockPayload.Entry> entries = new ArrayList<>(totals.size());
		totals.forEach((item, count) -> entries.add(new RemoteStockPayload.Entry(item, count)));
		return entries;
	}
}
