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

package malte0811.industrialwires.mech_mb;

import blusunrize.immersiveengineering.api.MultiblockHandler;
import blusunrize.immersiveengineering.api.crafting.IngredientStack;
import malte0811.industrialwires.IndustrialWires;
import malte0811.industrialwires.blocks.converter.MechanicalMBBlockType;
import malte0811.industrialwires.blocks.converter.TileEntityMechMB;
import malte0811.industrialwires.util.LocalSidedWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static malte0811.industrialwires.blocks.converter.MechanicalMBBlockType.*;

public class MultiblockMechMB implements MultiblockHandler.IMultiblock {
	public static MultiblockMechMB INSTANCE;
	@Override
	public String getUniqueName() {
		return IndustrialWires.MODID+":mech_mb";
	}

	@Override
	public boolean isBlockTrigger(IBlockState state) {
		return MechMBPart.isValidDefaultCenter(state);
	}

	private boolean checkEnd(LocalSidedWorld w, BlockPos.PooledMutableBlockPos p) {
		p.setPos(0, 0, 0);
		if (!MechMBPart.isValidDefaultCenter(w.getBlockState(p))) {
			return false;
		}
		p.setPos(0, -1, 0);
		return MechMBPart.isHeavyEngineering(w.getBlockState(p));
	}

	private boolean isBearingPerfect(LocalSidedWorld w, BlockPos.PooledMutableBlockPos p) {
		p.setPos(0, 0, 0);
		return MechMBPart.isPerfectBearing(w.getBlockState(p));
	}

	private void formEnd(LocalSidedWorld w, BlockPos.PooledMutableBlockPos p, MechanicalMBBlockType type,
			BiConsumer<TileEntityMechMB, Boolean> init) {
		p.setPos(0, 0, 0);
		w.setBlockState(p, IndustrialWires.mechanicalMB.getStateFromMeta(type.ordinal()));
		TileEntity te = w.getTileEntity(p);
		if (te instanceof TileEntityMechMB) {
			init.accept((TileEntityMechMB) te, true);
		}
		p.setPos(0, -1, 0);
		w.setBlockState(p, IndustrialWires.mechanicalMB.getStateFromMeta(NO_MODEL.ordinal()));
		te = w.getTileEntity(p);
		if (te instanceof TileEntityMechMB) {
			init.accept((TileEntityMechMB) te, false);
		}
	}

	@Override
	public boolean createStructure(World world, BlockPos pos, EnumFacing side, EntityPlayer player) {
		if (side.getAxis().isVertical())
			return false;
		BlockPos.PooledMutableBlockPos mutPos = BlockPos.PooledMutableBlockPos.retain();
		try {
			LocalSidedWorld w = new LocalSidedWorld(world, pos, side.getOpposite(), false);
			if (!checkEnd(w, mutPos)) {
				return false;
			}
			boolean foundAll = false;
			List<MechMBPart> parts = new ArrayList<>();
			int lastLength = 1;
			double inertia = 0;
			boolean isLossless1 = isBearingPerfect(w, mutPos);;
			boolean isLossless = false;
			while (!foundAll) {
				mutPos.setPos(0, 0, lastLength);
				w.setOrigin(w.getRealPos(mutPos));
				MechMBPart next = null;
				List<MechMBPart> instances = new ArrayList<>(MechMBPart.INSTANCES.values());
				instances.sort(MechMBPart.SORT_BY_COUNT.reversed());
				MechMBPart last = instances.get(0);
				for (MechMBPart part:instances) {
					if (MechMBPart.SORT_BY_COUNT.compare(last, part) != 0 && checkEnd(w, mutPos)) {
						foundAll = true;
						if(isLossless1 != isBearingPerfect(w, mutPos)) {
							return false;
						} else if(isLossless1 && isBearingPerfect(w, mutPos)) {
							isLossless = true;
						}

						break;
					}
					last = part;
					if (part.canForm(w)) {
						next = part;
						String key = MechMBPart.REGISTRY.inverse().get(part.getClass());
						MechMBPart.cacheNewInstance(key);
						break;
					}
				}
				if (next != null) {
					parts.add(next);
					lastLength = next.getLength();
					inertia += next.getInertia();
				} else if (!foundAll) {
					return false;
				}
			}
			if (parts.isEmpty()) {
				return false;
			}
			double finalInertia = inertia;
			boolean finalIsLossless = isLossless;
			w.setOrigin(pos);
			formEnd(w, mutPos, END, (te, master) -> {
				if (master) {
					te.offset = BlockPos.ORIGIN;
					te.setMechanical(parts.toArray(new MechMBPart[0]), 0);
					te.energyState = new MechEnergy(finalInertia, 0);
					te.isLossless = finalIsLossless;
				} else {
					te.offset = new BlockPos(0, -1, 0);
				}
				te.facing = side;
				te.formed = true;
			});
			lastLength = 1;
			Consumer<TileEntityMechMB> init = (te) -> {
				te.offset = te.getPos().subtract(pos);
				te.facing = side;
				te.formed = true;
			};
			for (MechMBPart part : parts) {
				mutPos.setPos(0, 0, lastLength);
				w = new LocalSidedWorld(world, w.getRealPos(mutPos), w.getFacing(), w.isMirrored());
				part.form(w, init);
				lastLength = part.getLength();
			}
			mutPos.setPos(0, 0, lastLength);
			w = new LocalSidedWorld(w.getWorld(), w.getRealPos(mutPos), w.getFacing(), w.isMirrored());
			formEnd(w, mutPos, OTHER_END, (te, __) -> init.accept(te));
			return true;
		} finally {
			mutPos.release();
		}
	}

	private ItemStack[][][] fakeStructure = {{{new ItemStack(Blocks.COMMAND_BLOCK)}}};
	private IngredientStack[] fakeMats = {new IngredientStack(fakeStructure[0][0][0])};
	@Override
	public ItemStack[][][] getStructureManual() {
		return fakeStructure;
	}

	@Override
	public IngredientStack[] getTotalMaterials() {
		return fakeMats;
	}

	@Override
	public boolean overwriteBlockRender(ItemStack stack, int iterator) {
		return false;
	}

	@Override
	public float getManualScale() {
		return 1;
	}

	@Override
	public boolean canRenderFormedStructure() {
		return false;
	}

	@Override
	public void renderFormedStructure() {

	}
}
