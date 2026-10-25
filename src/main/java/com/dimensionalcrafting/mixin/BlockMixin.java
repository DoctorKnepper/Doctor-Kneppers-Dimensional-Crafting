package com.dimensionalcrafting.mixin;

import com.dimensionalcrafting.storage.ContainerOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chests and barrels don't override setPlacedBy, so hooking the base method catches every player placement. */
@Mixin(Block.class)
public class BlockMixin {
	@Inject(method = "setPlacedBy", at = @At("HEAD"))
	private void dimensionalcrafting$recordOwner(
		final Level level, final BlockPos pos, final BlockState state, final @Nullable LivingEntity by, final ItemStack itemStack, final CallbackInfo ci
	) {
		if (level instanceof ServerLevel serverLevel && by instanceof ServerPlayer player) {
			ContainerOwnership.onPlaced(serverLevel, pos, state, player);
		}
	}
}
