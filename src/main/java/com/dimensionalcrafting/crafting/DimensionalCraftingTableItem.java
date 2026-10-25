package com.dimensionalcrafting.crafting;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class DimensionalCraftingTableItem extends BlockItem {
	public DimensionalCraftingTableItem(final Block block, final Item.Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(
		final ItemStack itemStack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag tooltipFlag
	) {
		builder.accept(Component.translatable("block.dimensionalcrafting.dimensional_crafting_table.tooltip").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("block.dimensionalcrafting.dimensional_crafting_table.tooltip.owned").withStyle(ChatFormatting.DARK_GRAY));
	}
}
