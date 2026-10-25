package com.dimensionalcrafting.storage;

import com.dimensionalcrafting.DimensionalCrafting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World-wide record of which player owns which chest or barrel. Vanilla has no
 * notion of ownership, so this is what lets a Dimensional Crafting Table find
 * "all of your chests" without scanning the world.
 */
public class OwnedContainers extends SavedData {
	private record Entry(GlobalPos pos, UUID owner) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner)
			)
			.apply(i, Entry::new));
	}

	public static final Codec<OwnedContainers> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().optionalFieldOf("containers", List.of()).forGetter(OwnedContainers::entries)
		)
		.apply(i, OwnedContainers::new));
	public static final SavedDataType<OwnedContainers> TYPE = new SavedDataType<>(
		DimensionalCrafting.id("owned_containers"), OwnedContainers::new, CODEC, DataFixTypes.SAVED_DATA_WANDERING_TRADER
	);

	private final Map<GlobalPos, UUID> owners = new HashMap<>();
	private final Map<UUID, Set<GlobalPos>> byOwner = new HashMap<>();

	public OwnedContainers() {
	}

	private OwnedContainers(final List<Entry> entries) {
		for (Entry entry : entries) {
			this.owners.put(entry.pos(), entry.owner());
			this.byOwner.computeIfAbsent(entry.owner(), k -> new LinkedHashSet<>()).add(entry.pos());
		}
	}

	public static OwnedContainers get(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	private List<Entry> entries() {
		List<Entry> entries = new ArrayList<>(this.owners.size());
		this.owners.forEach((pos, owner) -> entries.add(new Entry(pos, owner)));
		return entries;
	}

	public @Nullable UUID ownerOf(final GlobalPos pos) {
		return this.owners.get(pos);
	}

	public void setOwner(final GlobalPos pos, final UUID owner) {
		UUID previous = this.owners.put(pos, owner);
		if (owner.equals(previous)) {
			return;
		}

		if (previous != null) {
			this.forgetFromOwner(previous, pos);
		}

		this.byOwner.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(pos);
		this.setDirty();
	}

	public void remove(final GlobalPos pos) {
		UUID previous = this.owners.remove(pos);
		if (previous != null) {
			this.forgetFromOwner(previous, pos);
			this.setDirty();
		}
	}

	public List<GlobalPos> ownedBy(final UUID owner) {
		return List.copyOf(this.byOwner.getOrDefault(owner, Set.of()));
	}

	private void forgetFromOwner(final UUID owner, final GlobalPos pos) {
		Set<GlobalPos> positions = this.byOwner.get(owner);
		if (positions != null) {
			positions.remove(pos);
			if (positions.isEmpty()) {
				this.byOwner.remove(owner);
			}
		}
	}
}
