package com.dimensionalcrafting.test;

import com.dimensionalcrafting.DimensionalCrafting;
import com.dimensionalcrafting.storage.OwnedContainers;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * End-to-end check of the client half: the recipe book must treat items in a far-away owned chest
 * as available at a Dimensional Crafting Table (and not at a plain crafting table), and clicking
 * the recipe must pull them into the grid.
 */
public class DimensionalCraftingClientTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("recipe give @a *");
			server.runCommand("time set noon");

			AtomicReference<BlockPos> vanillaTable = new AtomicReference<>();
			AtomicReference<BlockPos> dimensionalTable = new AtomicReference<>();
			AtomicReference<BlockPos> farChest = new AtomicReference<>();
			AtomicReference<RecipeDisplayId> chestRecipe = new AtomicReference<>();
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos feet = player.blockPosition();
				vanillaTable.set(feet.offset(2, 0, -1));
				dimensionalTable.set(feet.offset(2, 0, 1));
				level.setBlockAndUpdate(vanillaTable.get(), Blocks.CRAFTING_TABLE.defaultBlockState());
				level.setBlockAndUpdate(dimensionalTable.get(), DimensionalCrafting.DIMENSIONAL_CRAFTING_TABLE.defaultBlockState());

				farChest.set(new BlockPos(feet.getX() + 5000, 80, feet.getZ() - 5000));
				level.setBlockAndUpdate(farChest.get(), Blocks.CHEST.defaultBlockState());
				OwnedContainers.get(s).setOwner(GlobalPos.of(level.dimension(), farChest.get()), player.getUUID());
				((Container)level.getBlockEntity(farChest.get())).setItem(0, new ItemStack(Items.OAK_PLANKS, 64));

				ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> chestKey = ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("chest"));
				s.getRecipeManager().listDisplaysForRecipe(chestKey, entry -> chestRecipe.set(entry.id()));
			});
			if (chestRecipe.get() == null) {
				throw new AssertionError("no recipe book display for minecraft:chest");
			}

			context.runOnClient(mc -> {
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
				mc.player.getRecipeBook().setFiltering(RecipeBookType.CRAFTING, true);
			});

			// 1. A plain crafting table can't see the chest's planks.
			openTable(context, server, vanillaTable.get());
			context.waitTicks(10);
			context.takeScreenshot("dimensionalcrafting-1-vanilla-table");
			if (isCraftable(context, chestRecipe.get())) {
				throw new AssertionError("chest recipe should NOT be craftable at a vanilla crafting table");
			}
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitTicks(5);

			// 2. The Dimensional Crafting Table can.
			openTable(context, server, dimensionalTable.get());
			context.waitTicks(10);
			context.takeScreenshot("dimensionalcrafting-2-dimensional-table");
			if (!isCraftable(context, chestRecipe.get())) {
				throw new AssertionError("chest recipe should be craftable at the Dimensional Crafting Table");
			}

			// 3. Clicking the recipe pulls planks from the far chest into the grid.
			context.runOnClient(mc -> mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, chestRecipe.get(), false));
			context.waitFor(mc -> mc.player.containerMenu.getSlot(0).getItem().is(Items.CHEST), 100);
			context.waitTicks(5);
			context.takeScreenshot("dimensionalcrafting-3-recipe-placed");
			int left = server.computeOnServer(s -> ((Container)serverLevel(s).getBlockEntity(farChest.get())).getItem(0).getCount());
			if (left != 56) {
				throw new AssertionError("far chest should have 56 planks left, has " + left);
			}

			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitTicks(5);
			int back = server.computeOnServer(s -> ((Container)serverLevel(s).getBlockEntity(farChest.get())).getItem(0).getCount());
			if (back != 64) {
				throw new AssertionError("closing without crafting should send the 8 planks back to the far chest, it has " + back);
			}

			// 4. How the block itself looks in the world.
			context.getInput().lookAt(dimensionalTable.get());
			context.waitTicks(40);
			context.takeScreenshot("dimensionalcrafting-4-table-in-world");
		}
	}

	private static ServerLevel serverLevel(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst().level();
	}

	private static void openTable(final ClientGameTestContext context, final TestServerContext server, final BlockPos pos) {
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			player.level().getBlockState(pos).useWithoutItem(player.level(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		});
		context.waitForScreen(CraftingScreen.class);
	}

	private static boolean isCraftable(final ClientGameTestContext context, final RecipeDisplayId id) {
		return context.computeOnClient((Minecraft mc) -> {
			for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
				if (collection.getRecipes().stream().anyMatch(entry -> entry.id().equals(id))) {
					return collection.isCraftable(id);
				}
			}

			throw new AssertionError("chest recipe is not in the client recipe book");
		});
	}
}
