package com.dimensionalcrafting;

import com.dimensionalcrafting.crafting.DimensionalCraftingTableBlock;
import com.dimensionalcrafting.crafting.DimensionalCraftingTableItem;
import com.dimensionalcrafting.crafting.RemoteStockPayload;
import com.dimensionalcrafting.storage.ContainerOwnership;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers the Dimensional Crafting Table and the chest ownership tracking it relies on. */
public class DimensionalCrafting implements ModInitializer {
	public static final String MOD_ID = "dimensionalcrafting";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<Block> DIMENSIONAL_CRAFTING_TABLE_ID = ResourceKey.create(Registries.BLOCK, id("dimensional_crafting_table"));
	public static final Block DIMENSIONAL_CRAFTING_TABLE = Registry.register(
		BuiltInRegistries.BLOCK,
		DIMENSIONAL_CRAFTING_TABLE_ID,
		new DimensionalCraftingTableBlock(
			BlockBehaviour.Properties.of()
				.setId(DIMENSIONAL_CRAFTING_TABLE_ID)
				.mapColor(MapColor.COLOR_PURPLE)
				.instrument(NoteBlockInstrument.BASEDRUM)
				.requiresCorrectToolForDrops()
				.strength(25.0F, 1200.0F)
				.sound(SoundType.STONE)
				.lightLevel(state -> 7)
		)
	);
	public static final ResourceKey<Item> DIMENSIONAL_CRAFTING_TABLE_ITEM_ID = ResourceKey.create(Registries.ITEM, id("dimensional_crafting_table"));
	public static final Item DIMENSIONAL_CRAFTING_TABLE_ITEM = registerBlockItem(
		DIMENSIONAL_CRAFTING_TABLE_ITEM_ID,
		new DimensionalCraftingTableItem(
			DIMENSIONAL_CRAFTING_TABLE,
			new Item.Properties().setId(DIMENSIONAL_CRAFTING_TABLE_ITEM_ID).useBlockDescriptionPrefix().rarity(Rarity.RARE).fireResistant()
		)
	);

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static Item registerBlockItem(final ResourceKey<Item> key, final BlockItem item) {
		item.registerBlocks(Item.BY_BLOCK, item);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(RemoteStockPayload.TYPE, RemoteStockPayload.STREAM_CODEC);
		ContainerOwnership.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(output -> output.insertAfter(Items.CRAFTING_TABLE, DIMENSIONAL_CRAFTING_TABLE_ITEM));
		LOGGER.info("Dimensional Crafting Table loaded");
	}
}
