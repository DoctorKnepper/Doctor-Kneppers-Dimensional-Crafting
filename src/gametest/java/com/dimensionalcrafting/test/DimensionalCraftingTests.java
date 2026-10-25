package com.dimensionalcrafting.test;

import com.dimensionalcrafting.DimensionalCrafting;
import com.dimensionalcrafting.crafting.DimensionalCraftingMenu;
import com.dimensionalcrafting.crafting.RemoteStock;
import com.dimensionalcrafting.storage.OwnedContainers;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("removal")
public class DimensionalCraftingTests {
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);

	private static RecipeHolder<?> recipe(final GameTestHelper helper, final String id) {
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
		return helper.getLevel().getServer().getRecipeManager().byKey(key).orElseThrow(() -> new AssertionError("missing recipe " + id));
	}

	private static DimensionalCraftingMenu openTable(final GameTestHelper helper, final ServerPlayer player) {
		helper.setBlock(TABLE, DimensionalCrafting.DIMENSIONAL_CRAFTING_TABLE);
		BlockPos tablePos = helper.absolutePos(TABLE);
		player.snapTo(tablePos.getX() + 0.5, tablePos.getY() + 1, tablePos.getZ() + 0.5);
		return new DimensionalCraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), tablePos));
	}

	/**
	 * Places an empty chest owned by {@code owner} directly in {@code level}. The test world is kept
	 * between runs, so the chest is cleared in case an earlier run left one with items at this spot.
	 */
	private static Container ownedChest(final ServerLevel level, final BlockPos pos, final UUID owner) {
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		OwnedContainers.get(level.getServer()).setOwner(GlobalPos.of(level.dimension(), pos), owner);
		Container chest = (Container)level.getBlockEntity(pos);
		chest.clearContent();
		return chest;
	}

	private static void finish(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest
	public void placingAChestMakesItYours(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
		helper.placeAt(player, player.getMainHandItem(), new BlockPos(2, 0, 2), Direction.UP);

		BlockPos chestPos = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.assertBlockPresent(Blocks.CHEST, new BlockPos(2, 1, 2));
		UUID owner = OwnedContainers.get(helper.getLevel().getServer()).ownerOf(GlobalPos.of(helper.getLevel().dimension(), chestPos));
		helper.assertTrue(player.getUUID().equals(owner), "placed chest should belong to the placer, was " + owner);
		finish(helper, player);
	}

	@GameTest
	public void openingAnUnownedChestClaimsIt(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos rel = new BlockPos(3, 1, 3);
		helper.setBlock(rel, Blocks.BARREL);
		BlockPos abs = helper.absolutePos(rel);
		GlobalPos globalPos = GlobalPos.of(helper.getLevel().dimension(), abs);
		OwnedContainers owned = OwnedContainers.get(helper.getLevel().getServer());
		helper.assertTrue(owned.ownerOf(globalPos) == null, "barrel set by the test should start unowned");

		player.snapTo(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5);
		player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
		helper.assertTrue(player.getUUID().equals(owned.ownerOf(globalPos)), "opening an unowned barrel should claim it");

		// Someone else opening it afterwards must not steal it.
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		other.gameMode.useItemOn(other, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
		helper.assertTrue(player.getUUID().equals(owned.ownerOf(globalPos)), "a second player must not take over an owned barrel");
		helper.getLevel().getServer().getPlayerList().remove(other);
		finish(helper, player);
	}

	@GameTest(maxTicks = 400)
	public void craftsFromFarAwayAndOtherDimensionChests(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel overworld = helper.getLevel();
		ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "test server has no nether");

		BlockPos farPos = helper.absolutePos(TABLE).offset(30_000, 0, 30_000);
		// Derived from the test's own position, which changes every run, so runs never share this chest.
		BlockPos netherPos = new BlockPos(helper.absolutePos(TABLE).getX() / 8, 64, helper.absolutePos(TABLE).getZ() / 8);
		ownedChest(overworld, farPos, player.getUUID()).setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
		ownedChest(nether, netherPos, player.getUUID()).setItem(5, new ItemStack(Items.STICK, 2));

		// Give the far chunk time to unload so the table has to load it again itself.
		helper.runAfterDelay(200, () -> {
			boolean farChunkStillLoaded = overworld.getChunkSource()
				.hasChunk(SectionPos.blockToSectionCoord(farPos.getX()), SectionPos.blockToSectionCoord(farPos.getZ()));
			DimensionalCrafting.LOGGER.info("[gametest] far chest chunk loaded before crafting: {}", farChunkStillLoaded);

			DimensionalCraftingMenu menu = openTable(helper, player);
			RecipeBookMenu.PostPlaceAction action = menu.handlePlacement(false, false, recipe(helper, "minecraft:wooden_pickaxe"), overworld, player.getInventory());
			helper.assertTrue(action == RecipeBookMenu.PostPlaceAction.NOTHING, "placement should succeed, got " + action);
			helper.assertTrue(menu.getResultSlot().getItem().is(Items.WOODEN_PICKAXE), "result should be a wooden pickaxe, got " + menu.getResultSlot().getItem());

			Container far = (Container)overworld.getBlockEntity(farPos);
			Container netherChest = (Container)nether.getBlockEntity(netherPos);
			helper.assertTrue(far.isEmpty(), "planks should have been taken from the far chest");
			helper.assertTrue(netherChest.isEmpty(), "sticks should have been taken from the nether chest");
			helper.assertTrue(player.getInventory().isEmpty(), "nothing should be routed through the player's inventory");
			menu.removed(player);
			finish(helper, player);
		});
	}

	@GameTest
	public void usesInventoryBeforeChests(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().add(new ItemStack(Items.OAK_LOG, 1));
		Container chest = ownedChest(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), player.getUUID());
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 10));

		DimensionalCraftingMenu menu = openTable(helper, player);
		menu.handlePlacement(false, false, recipe(helper, "minecraft:oak_planks"), helper.getLevel(), player.getInventory());
		helper.assertTrue(menu.getResultSlot().getItem().is(Items.OAK_PLANKS), "should be able to craft planks");
		helper.assertTrue(chest.getItem(0).getCount() == 10, "the chest should be untouched while the inventory has the log");
		helper.assertTrue(!player.getInventory().contains(new ItemStack(Items.OAK_LOG)), "the inventory log should have been used");
		finish(helper, player);
	}

	@GameTest
	public void ignoresOtherPlayersChests(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Container theirs = ownedChest(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), UUID.randomUUID());
		theirs.setItem(0, new ItemStack(Items.OAK_LOG, 10));

		DimensionalCraftingMenu menu = openTable(helper, player);
		RecipeBookMenu.PostPlaceAction action = menu.handlePlacement(false, false, recipe(helper, "minecraft:oak_planks"), helper.getLevel(), player.getInventory());
		helper.assertTrue(action == RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE, "should only show a ghost recipe, got " + action);
		helper.assertTrue(theirs.getItem(0).getCount() == 10, "another player's chest must not be touched");
		finish(helper, player);
	}

	@GameTest
	public void usesTheEnderChest(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getEnderChestInventory().setItem(13, new ItemStack(Items.COBBLESTONE, 8));

		DimensionalCraftingMenu menu = openTable(helper, player);
		menu.handlePlacement(false, false, recipe(helper, "minecraft:furnace"), helper.getLevel(), player.getInventory());
		helper.assertTrue(menu.getResultSlot().getItem().is(Items.FURNACE), "should craft a furnace from ender chest cobblestone");
		helper.assertTrue(player.getEnderChestInventory().isEmpty(), "ender chest cobblestone should be used up");
		finish(helper, player);
	}

	@GameTest
	public void shiftClickFillsMaxStacksAcrossChests(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Container a = ownedChest(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), player.getUUID());
		Container b = ownedChest(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 4)), player.getUUID());
		a.setItem(0, new ItemStack(Items.OAK_LOG, 40));
		b.setItem(26, new ItemStack(Items.OAK_LOG, 40));

		DimensionalCraftingMenu menu = openTable(helper, player);
		menu.handlePlacement(true, false, recipe(helper, "minecraft:oak_planks"), helper.getLevel(), player.getInventory());
		int inGrid = menu.getInputGridSlots().stream().mapToInt(s -> s.getItem().getCount()).sum();
		int left = a.getItem(0).getCount() + b.getItem(26).getCount();
		helper.assertTrue(inGrid == 64, "max placement should fill a full stack of logs, got " + inGrid);
		helper.assertTrue(left == 16, "16 logs should remain across both chests, got " + left);
		finish(helper, player);
	}

	@GameTest
	public void closingSendsBorrowedItemsBack(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos farPos = helper.absolutePos(TABLE).offset(-12_000, 0, 9_000);
		Container far = ownedChest(helper.getLevel(), farPos, player.getUUID());
		far.setItem(3, new ItemStack(Items.OAK_PLANKS, 64));
		player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 2));

		DimensionalCraftingMenu menu = openTable(helper, player);
		menu.handlePlacement(false, false, recipe(helper, "minecraft:chest"), helper.getLevel(), player.getInventory());
		helper.assertTrue(menu.getResultSlot().getItem().is(Items.CHEST), "should be able to craft a chest");
		helper.assertTrue(countIn(far, Items.OAK_PLANKS) == 58, "6 planks should be borrowed (2 came from the inventory)");

		menu.removed(player);
		helper.assertTrue(countIn((Container)helper.getLevel().getBlockEntity(farPos), Items.OAK_PLANKS) == 64, "borrowed planks should go back to the far chest");
		helper.assertTrue(countIn(player.getInventory(), Items.OAK_PLANKS) == 2, "the player's own planks should come back to the inventory");
		finish(helper, player);
	}

	@GameTest
	public void switchingRecipesSendsBorrowedItemsBack(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Container chest = ownedChest(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), player.getUUID());
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));

		DimensionalCraftingMenu menu = openTable(helper, player);
		menu.handlePlacement(false, false, recipe(helper, "minecraft:chest"), helper.getLevel(), player.getInventory());
		menu.handlePlacement(false, false, recipe(helper, "minecraft:crafting_table"), helper.getLevel(), player.getInventory());
		helper.assertTrue(menu.getResultSlot().getItem().is(Items.CRAFTING_TABLE), "should now show a crafting table");
		helper.assertTrue(countIn(chest, Items.OAK_PLANKS) == 60, "only the crafting table's 4 planks should be out of the chest, has " + countIn(chest, Items.OAK_PLANKS));
		helper.assertTrue(player.getInventory().isEmpty(), "switching recipes must not move chest items into the inventory");
		finish(helper, player);
	}

	@GameTest
	public void craftedIngredientsStayUsed(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Container chest = ownedChest(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), player.getUUID());
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));

		DimensionalCraftingMenu menu = openTable(helper, player);
		menu.handlePlacement(false, false, recipe(helper, "minecraft:chest"), helper.getLevel(), player.getInventory());
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().is(Items.CHEST), "taking the result should give a chest, carried " + menu.getCarried());
		menu.removed(player);
		helper.assertTrue(countIn(chest, Items.OAK_PLANKS) == 56, "the 8 crafted planks are gone for good, chest has " + countIn(chest, Items.OAK_PLANKS));
		helper.assertTrue(countIn(player.getInventory(), Items.CHEST) == 1, "the crafted chest should end up in the inventory");
		finish(helper, player);
	}

	private static int countIn(final Container container, final net.minecraft.world.item.Item item) {
		int count = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).is(item)) {
				count += container.getItem(slot).getCount();
			}
		}

		return count;
	}

	@GameTest
	public void forgetsBrokenChests(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos abs = helper.absolutePos(new BlockPos(4, 1, 4));
		ownedChest(helper.getLevel(), abs, player.getUUID());
		helper.getLevel().setBlockAndUpdate(abs, Blocks.AIR.defaultBlockState());

		RemoteStock stock = RemoteStock.gather(player);
		helper.assertTrue(stock.ownedContainerCount() == 0, "a removed chest should not count");
		helper.assertTrue(OwnedContainers.get(helper.getLevel().getServer()).ownerOf(GlobalPos.of(helper.getLevel().dimension(), abs)) == null,
			"a removed chest should be forgotten");
		finish(helper, player);
	}

	@GameTest
	public void tableRecipeIsLoaded(final GameTestHelper helper) {
		RecipeHolder<?> holder = recipe(helper, "dimensionalcrafting:dimensional_crafting_table");
		helper.assertTrue(holder.value().placementInfo().ingredients().size() == 9, "table recipe should use all nine slots");
		helper.succeed();
	}
}
