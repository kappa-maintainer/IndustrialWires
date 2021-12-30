/*
 * This file is part of Industrial Wires.
 * Copyright (C) 2016-2018 malte0811
 * Industrial Wires is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Industrial Wires is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Industrial Wires.  If not, see <http://www.gnu.org/licenses/>.
 */

package malte0811.industrialwires.compat;

import blusunrize.immersiveengineering.api.ApiUtils;
import blusunrize.immersiveengineering.api.tool.ToolboxHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import crafttweaker.CraftTweakerAPI;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.item.IBoxable;
import ic2.api.item.IC2Items;
import ic2.core.block.base.BlockMultiID;
import malte0811.industrialwires.IndustrialWires;
import malte0811.industrialwires.compat.CompatCapabilities.Charset;
import malte0811.industrialwires.hv.MarxOreHandler;
import malte0811.industrialwires.mech_mb.MechPartCommutator;
import mrtjp.projectred.api.ProjectRedAPI;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import pl.asie.charset.api.wires.IBundledEmitter;
import pl.asie.charset.api.wires.IBundledReceiver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Compat {
	public static final String IC2_ID = "ic2";
	public static final String CRAFTTWEAKER_ID = "crafttweaker";
	public static final String CHARSET_ID = "charset";
	private static final ResourceLocation IC2_TE = new ResourceLocation("ic2", "blockcompactedgenerator");
	public static BiFunction<String, String, List<ItemStack>> getIC2Item = (s, s2)->ImmutableList.of();
	static Consumer<MarxOreHandler.OreInfo> addMarx = (o) -> {
	};
	static Consumer<MarxOreHandler.OreInfo> removeMarx = (o) -> {
	};
	public static Consumer<TileEntity> loadIC2Tile = te -> {
	};
	public static Consumer<TileEntity> unloadIC2Tile = te -> {
	};
	public static IBlockAction<EnumFacing, byte[]> getBundledRS = (w, p, f) -> new byte[16];
	public static IBlockAction<Void, Void> updateBundledRS = (w, p, f) -> null;
	public static boolean enableOtherRS = false;
	private static Map<String, Class<? extends CompatModule>> modules = ImmutableMap.of(IC2_ID, CompatIC2.class,
			CRAFTTWEAKER_ID, CompatCT.class, ProjectRedAPI.modIDCore, CompatProjectRed.class,
			CHARSET_ID, CompatCharset.class);
	private static Method preInit;
	private static Method init;

	static {
		try {
			preInit = CompatModule.class.getMethod("preInit");
			init = CompatModule.class.getMethod("init");
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	public static ItemStack stackFromInfo(ItemStack stack, Template.BlockInfo info) {
		if (IC2_TE.equals(info.blockState.getBlock().getRegistryName()) && info.tileentityData != null) {
			stack = (ItemStack) ((BlockMultiID) info.blockState.getBlock()).getPickBlock(info.blockState, null, null, null, null);
			//IndustrialWires.logger.info(info.blockState + stack.getDisplayName());
		}

		return stack;
	}
	public static void preInit() {
		for (Map.Entry<String, Class<? extends CompatModule>> e:modules.entrySet()) {
			if (Loader.isModLoaded(e.getKey())) {
				try {
					preInit.invoke(e.getValue().newInstance());
				} catch (IllegalAccessException | InvocationTargetException | InstantiationException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	public static void init() {
		for (Map.Entry<String, Class<? extends CompatModule>> e:modules.entrySet()) {
			if (Loader.isModLoaded(e.getKey())) {
				try {
					init.invoke(e.getValue().newInstance());
				} catch (IllegalAccessException | InvocationTargetException | InstantiationException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	static abstract class CompatModule {
		public void preInit() {
		}

		public void init() {
		}
	}

	public static class CompatCT extends CompatModule {
		@Override
		public void preInit() {
			CraftTweakerAPI.registerClass(CTMarxGenerator.class);
		}
	}

	public static class CompatIC2 extends CompatModule {
		@Override
		public void preInit() {
			loadIC2Tile = (te) -> {
				if (!te.getWorld().isRemote) {
					MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent((IEnergyTile) te));
				}
			};
			unloadIC2Tile = (te) -> {
				if (!te.getWorld().isRemote) {
					MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent((IEnergyTile) te));
				}
			};
			getIC2Item = (type, variant) -> ImmutableList.of(IC2Items.getItem(type, variant));
		}

		public void init() {
			Item tinnedFood = IC2Items.getItem("filled_tin_can").getItem();
			ItemStack emptyMug = IC2Items.getItem("mug", "empty");
			ToolboxHandler.addFoodType((s) -> s.getItem() == tinnedFood);
			ToolboxHandler.addFoodType((s) ->
					s.getItem() == emptyMug.getItem() && !ItemStack.areItemStacksEqual(emptyMug, ApiUtils.copyStackWithAmount(s, 1))
			);
			Item cable = IC2Items.getItem("cable", "type:copper,insulation:0").getItem();
			ToolboxHandler.addWiringType((s, w) -> s.getItem() == cable);
			ToolboxHandler.addToolType((s) -> {
				Item a = s.getItem();
				return a instanceof IBoxable && ((IBoxable) a).canBeStoredInToolbox(s);
			});
			MechPartCommutator.originalStack = IC2Items.getItem("blockcompactedgenerator", "hv-rfproducer-classic");


		}
	}

	public static class CompatProjectRed extends CompatModule {

		@Override
		public void preInit() {
			enableOtherRS = true;
		}

		@Override
		public void init() {
			super.init();
			IBlockAction<EnumFacing, byte[]> oldGet = getBundledRS;
			getBundledRS = (w, p, f) -> {
				byte[] oldIn = oldGet.run(w, p, f);
				byte[] prIn = ProjectRedAPI.transmissionAPI.getBundledInput(w, p, f);
				if (prIn!=null) {
					for (int i = 0; i < 16; i++) {
						oldIn[i] = (byte) Math.ceil((prIn[i] & 255) / 17.0);
					}
				}
				return oldIn;
			};
			IBlockAction<Void, Void> oldUpdate = updateBundledRS;
			updateBundledRS = (w, p, f)-> {
				oldUpdate.run(w, p, f);
				w.notifyNeighborsOfStateChange(p, w.getBlockState(p).getBlock(), true);
				return null;
			};
		}
	}

	public static class CompatCharset extends CompatModule {

		@Override
		public void preInit() {
			enableOtherRS = true;
		}

		@Override
		public void init() {
			super.init();
			IBlockAction<EnumFacing, byte[]> old = getBundledRS;
			getBundledRS = (w, p, f) -> {
				byte[] oldIn = old.run(w, p, f);
				TileEntity te = w.getTileEntity(p.offset(f));
				if (te!=null && te.hasCapability(Charset.EMITTER_CAP, f.getOpposite())) {
					IBundledEmitter emitter = te.getCapability(Charset.EMITTER_CAP, f.getOpposite());
					assert emitter!=null;
					byte[] charIn = emitter.getBundledSignal();
					if (charIn!=null) {
						for (int i = 0;i<16;i++) {
							if (charIn[i]>oldIn[i]) {
								oldIn[i] = charIn[i];
							}
						}
					}
				}
				return oldIn;
			};
			IBlockAction<Void, Void> oldUpdate = updateBundledRS;
			updateBundledRS = (w, p, __)-> {
				oldUpdate.run(w, p, __);
				for (EnumFacing face : EnumFacing.VALUES) {
					TileEntity te = w.getTileEntity(p.offset(face.getOpposite()));
					if (te != null && te.hasCapability(Charset.RECEIVER_CAP, face)) {
						IBundledReceiver receiver = te.getCapability(Charset.RECEIVER_CAP, face);
						assert receiver != null;
						receiver.onBundledInputChange();
					}
				}
				return null;
			};
		}
	}
}
