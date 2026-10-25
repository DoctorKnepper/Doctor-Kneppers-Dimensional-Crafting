package com.dimensionalcrafting.crafting;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DimensionalCraftingTableBlock extends CraftingTableBlock {
	private static final Component CONTAINER_TITLE = Component.translatable("container.dimensionalcrafting.dimensional_crafting");

	public DimensionalCraftingTableBlock(final BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(
		final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult
	) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(state.getMenuProvider(level, pos));
			serverPlayer.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
			if (serverPlayer.containerMenu instanceof DimensionalCraftingMenu menu) {
				RemoteStock stock = RemoteStock.gather(serverPlayer);
				menu.sendStock(stock);
				serverPlayer.sendOverlayMessage(linkedMessage(stock.ownedContainerCount()));
			}
		}

		return InteractionResult.SUCCESS;
	}

	private static Component linkedMessage(final int count) {
		return switch (count) {
			case 0 -> Component.translatable("message.dimensionalcrafting.dimensional_crafting.linked_none").withStyle(ChatFormatting.GRAY);
			case 1 -> Component.translatable("message.dimensionalcrafting.dimensional_crafting.linked_one").withStyle(ChatFormatting.LIGHT_PURPLE);
			default -> Component.translatable("message.dimensionalcrafting.dimensional_crafting.linked_many", count).withStyle(ChatFormatting.LIGHT_PURPLE);
		};
	}

	@Override
	protected MenuProvider getMenuProvider(final BlockState state, final Level level, final BlockPos pos) {
		return new SimpleMenuProvider(
			(containerId, inventory, player) -> new DimensionalCraftingMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)), CONTAINER_TITLE
		);
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(
				ParticleTypes.PORTAL,
				pos.getX() + random.nextDouble(),
				pos.getY() + 1.0 + random.nextDouble() * 0.4,
				pos.getZ() + random.nextDouble(),
				(random.nextDouble() - 0.5) * 0.5,
				-random.nextDouble() * 0.6,
				(random.nextDouble() - 0.5) * 0.5
			);
		}
	}
}
