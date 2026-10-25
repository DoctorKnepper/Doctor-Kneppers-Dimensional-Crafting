package com.dimensionalcrafting.storage;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides which containers belong to whom:
 * <ul>
 *   <li>a chest or barrel you place is yours;</li>
 *   <li>an unowned one (placed before this mod was installed, or found in a structure) becomes yours the first time you open it;</li>
 *   <li>breaking it forgets the owner.</li>
 * </ul>
 */
public final class ContainerOwnership {
	private ContainerOwnership() {
	}

	/** Chests (including trapped and copper chests) and barrels. Ender chests are handled separately, per player. */
	public static boolean isTrackable(final BlockState state) {
		return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock;
	}

	public static void onPlaced(final ServerLevel level, final BlockPos pos, final BlockState state, final ServerPlayer player) {
		if (isTrackable(state)) {
			OwnedContainers.get(level.getServer()).setOwner(GlobalPos.of(level.dimension(), pos.immutable()), player.getUUID());
		}
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level instanceof ServerLevel serverLevel && !player.isSpectator()) {
				// Mirrors vanilla: sneaking with something in hand uses the item instead of opening the block.
				boolean opensBlock = !player.isSecondaryUseActive() || player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
				BlockPos pos = hitResult.getBlockPos();
				if (opensBlock && isTrackable(level.getBlockState(pos))) {
					OwnedContainers owned = OwnedContainers.get(serverLevel.getServer());
					GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
					if (owned.ownerOf(globalPos) == null) {
						owned.setOwner(globalPos, player.getUUID());
					}
				}
			}

			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && isTrackable(state)) {
				OwnedContainers.get(serverLevel.getServer()).remove(GlobalPos.of(level.dimension(), pos.immutable()));
			}
		});
	}
}
