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

import com.google.common.collect.ImmutableList;
import malte0811.industrialwires.IWConfig;
import malte0811.industrialwires.IndustrialWires;
import malte0811.industrialwires.blocks.converter.MechanicalMBBlockType;
import malte0811.industrialwires.util.LocalSidedWorld;
import malte0811.industrialwires.util.MBSideConfig.BlockFace;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;

import static net.minecraft.util.EnumFacing.UP;
import static net.minecraft.util.math.BlockPos.ORIGIN;

public class MechPartCommutator extends MechPartEnergyIO {
	public static ItemStack originalStack = ItemStack.EMPTY;

	@Override
	protected Waveform transform(Waveform wf, MechEnergy e) {
		return wf.getCommutated(e.getSpeed(), has4Phases());
	}

	@Override
	protected double getTransformationLimit(MechEnergy me) {
		double s = me.getSpeed();
		if (s<5) {
			return 0;
		} else if (s<10) {
			return ramp(5, 10, s);
		} else {
			return 1;
		}
	}

	@SuppressWarnings("SameParameterValue")
	private double ramp(double min, double max, double pos) {
		double diff = max-min;
		//Formulas come from Hermite interpolation
		if (max>min) {
			return MathHelper.clamp((pos-min)*(pos-min)*(1/(diff*diff)-2/(diff*diff*diff)*(pos-max)), 0, 1);
		} else {
			diff *= -1;
			double tmp = max;
			max = min;
			min = tmp;
			return MathHelper.clamp(1+(pos-min)*(pos-min)*(-1/(diff*diff)+2/(diff*diff*diff)*(pos-max)), 0, 1);
		}
	}

	@Override
	public double getInertia() {
		return 50;
	}

	@Override
	public double getMaxSpeed() {
		return IWConfig.MechConversion.allowMBEU()?100:-1;
	}

	@Override
	public ResourceLocation getRotatingBaseModel() {
		return new ResourceLocation(IndustrialWires.MODID, "block/mech_mb/shaft_comm.obj");
	}

	private static final ResourceLocation KINETIC_GEN_KEY =
			new ResourceLocation("ic2", "hv-rfproducer-classic");
	@Override
	public boolean canForm(LocalSidedWorld w) {
		if (!IWConfig.MechConversion.allowMBEU()) {
			return false;
		}
		//Center is an IC2 kinetic generator
		TileEntity te = w.getTileEntity(BlockPos.ORIGIN);
		if (te!=null) {
			ResourceLocation loc = TileEntity.getKey(te.getClass());
			return loc != null && loc.equals(KINETIC_GEN_KEY);
		}
		return false;
	}

	@Override
	public short getFormPattern(int offset) {
		return 0b000_010_000;
	}

	@Override
	public void breakOnFailure(MechEnergy energy) {
		//NOP
	}

	@Override
	public ItemStack getOriginalItem(BlockPos pos) {
		return pos.equals(ORIGIN)?originalStack:super.getOriginalItem(pos);
	}

	@Override
	public void disassemble() {
		super.disassemble();
		if (IndustrialWires.ic2TeBlock!=null) {
			NBTTagCompound dummyNbt = new NBTTagCompound();
			dummyNbt.setString("id", KINETIC_GEN_KEY.toString());
			world.setBlockState(BlockPos.ORIGIN, IndustrialWires.ic2TeBlock.getDefaultState().withProperty((IProperty) PropertyInteger.create("metadata", 0, 15), Integer.valueOf(8)));
			world.setTileEntity(BlockPos.ORIGIN, TileEntity.create(world.getWorld(), dummyNbt));
		}
	}

	@Override
	public MechanicalMBBlockType getType() {
		return MechanicalMBBlockType.SHAFT_COMMUTATOR;
	}

	protected double getMaxBuffer() {
		return 2.5e3;
	}

	protected boolean has4Phases() {
		return false;
	}

	private static final List<BlockFace> outputs = ImmutableList.of(
			new BlockFace(ORIGIN, UP)
	);
	public List<BlockFace> getEnergyConnections() {
		return outputs;
	}

	@Override
	public AxisAlignedBB getBoundingBox(BlockPos offsetPart) {
		return new AxisAlignedBB(0, .375-1/32D, 0, 1, 1, 1);
	}
}
