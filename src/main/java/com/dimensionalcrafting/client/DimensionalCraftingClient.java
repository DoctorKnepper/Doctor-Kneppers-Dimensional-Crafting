package com.dimensionalcrafting.client;

import com.dimensionalcrafting.crafting.RemoteStockPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

public class DimensionalCraftingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(RemoteStockPayload.TYPE, (payload, context) -> {
			LocalPlayer player = context.player();
			if (player.containerMenu.containerId == payload.containerId()) {
				ClientRemoteStock.set(player.containerMenu, payload.items());
				// The recipe book recounts ingredients whenever the inventory's change counter moves.
				player.getInventory().setChanged();
			}
		});
	}
}
