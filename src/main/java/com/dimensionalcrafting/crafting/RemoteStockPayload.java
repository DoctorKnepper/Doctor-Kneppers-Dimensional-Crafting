package com.dimensionalcrafting.crafting;

import com.dimensionalcrafting.DimensionalCrafting;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/** Tells the client what's in the player's remote chests so the recipe book can show those recipes as craftable. */
public record RemoteStockPayload(int containerId, List<Entry> items) implements CustomPacketPayload {
	public record Entry(Holder<Item> item, int count) {
		static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
			Item.STREAM_CODEC, Entry::item, ByteBufCodecs.VAR_INT, Entry::count, Entry::new
		);
	}

	public static final CustomPacketPayload.Type<RemoteStockPayload> TYPE = new CustomPacketPayload.Type<>(DimensionalCrafting.id("remote_stock"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RemoteStockPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.CONTAINER_ID, RemoteStockPayload::containerId, Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), RemoteStockPayload::items, RemoteStockPayload::new
	);

	@Override
	public CustomPacketPayload.Type<RemoteStockPayload> type() {
		return TYPE;
	}
}
