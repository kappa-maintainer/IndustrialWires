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

import blusunrize.immersiveengineering.api.IEEnums.SideConfig;
import blusunrize.immersiveengineering.common.util.Utils;
import malte0811.industrialwires.blocks.IWProperties;
import malte0811.industrialwires.util.ConversionUtil;
import malte0811.industrialwires.util.MBSideConfig;
import malte0811.industrialwires.util.MBSideConfig.BlockFace;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import java.util.List;

import static blusunrize.immersiveengineering.api.IEEnums.SideConfig.INPUT;
import static blusunrize.immersiveengineering.api.IEEnums.SideConfig.OUTPUT;
import static malte0811.industrialwires.mech_mb.Waveform.Phases.get;
import static malte0811.industrialwires.mech_mb.Waveform.Speed.EXTERNAL;
import static malte0811.industrialwires.mech_mb.Waveform.Speed.ROTATION;
import static malte0811.industrialwires.mech_mb.Waveform.Type.NONE;
import static malte0811.industrialwires.util.NBTKeys.*;
import static net.minecraftforge.energy.CapabilityEnergy.ENERGY;

public abstract class MechPartEnergyIO extends MechMBPart implements IMBPartElectric {
	private double bufferToMB;
	private Waveform wfToMB = Waveform.forParameters(NONE, get(has4Phases()), ROTATION);
	private double bufferToWorld;
	private Waveform wfToWorld = Waveform.forParameters(NONE, get(has4Phases()), ROTATION);
	private final IEnergyStorage[] capForge = {new EnergyStorageMMB(INPUT), new EnergyStorageMMB(OUTPUT)};
	private MBSideConfig sides = new MBSideConfig(getEnergyConnections());

	@Override
	public Waveform getProduced(MechEnergy state) {
		return transform(wfToMB, state);
	}

	protected Waveform transform(Waveform wf, MechEnergy e) {
		return wf;
	}

	protected double getTransformationLimit(MechEnergy me) {
		return 1;
	}

	@Override
	public double getAvailableEEnergy(MechEnergy energy) {
		return bufferToMB* getTransformationLimit(energy);
	}

	@Override
	public void extractEEnergy(double eEnergy, MechEnergy mEnergy) {
		if (eEnergy>0) {
			bufferToMB -= eEnergy/ getTransformationLimit(mEnergy);
		}
	}

	@Override
	public double requestEEnergy(Waveform waveform, MechEnergy energy) {
		Waveform transformed = transform(waveform, energy);
		if (!has4Phases()==transformed.isSinglePhase()) {
			double ret = getMaxBuffer() - bufferToWorld;
			return ret* getTransformationLimit(energy);
		}
		return 0;
	}

	@Override
	public void insertEEnergy(double given, Waveform waveform, MechEnergy mechEnergy) {
		wfToWorld = transform(waveform, mechEnergy);
		bufferToWorld += given;
	}

	@Override
	public <T> T getCapability(Capability<T> cap, EnumFacing side, BlockPos pos) {
		BlockFace s = new BlockFace(pos, side);
		SideConfig conf = sides.getConfigForFace(s);
		if (conf!=SideConfig.NONE) {
			if (cap == ENERGY) {
				return ENERGY.cast(capForge[conf.ordinal()-1]);
			}
		}
		return super.getCapability(cap, side, pos);
	}

	@Override
	public <T> boolean hasCapability(Capability<T> cap, EnumFacing side, BlockPos pos) {
		if (sides.getConfigForFace(new BlockFace(pos, side))!=SideConfig.NONE) {
			if (cap == ENERGY) {
				return true;
			}
		}
		return super.hasCapability(cap, side, pos);
	}

	@Override
	public void createMEnergy(MechEnergy e) {}

	@Override
	public double requestMEnergy(MechEnergy e) {
		return 0;
	}

	@Override
	public void insertMEnergy(double added) {
		int available = (int) (Math.min(ConversionUtil.ifPerJoule() * bufferToWorld,
				getMaxBuffer()/getEnergyConnections().size()));
		if (available > 0 && wfToWorld.isAC()) {//The IC2 net will deal with DC by itself
			bufferToWorld -= outputFE(available);
		}
	}

	@Override
	public double getInertia() {
		return 50;
	}

	@Override
	//Calculated for a shaft of slightly larger than a 0.125 radius cylinder
	public double getWeight() { return 3800;}

	@Override
	public double getMaxSpeed() {
		return -1;
	}

	@Override
	public void writeToNBT(NBTTagCompound out) {
		out.setDouble(BUFFER_IN, bufferToMB);
		out.setDouble(BUFFER_OUT, bufferToWorld);
		out.setString(BUFFER_IN+WAVEFORM, wfToMB.toString());
		out.setString(BUFFER_OUT+WAVEFORM, wfToWorld.toString());
		out.setTag(SIDE_CONFIG, sides.toNBT(getEnergyConnections()));
	}

	@Override
	public void readFromNBT(NBTTagCompound in) {
		bufferToMB = in.getDouble(BUFFER_IN);
		bufferToWorld = in.getDouble(BUFFER_OUT);
		wfToMB = Waveform.fromString(in.getString(BUFFER_IN+WAVEFORM));
		wfToWorld = Waveform.fromString(in.getString(BUFFER_OUT+WAVEFORM));
		sides = new MBSideConfig(getEnergyConnections(), in.getTagList(SIDE_CONFIG, Constants.NBT.TAG_INT));
	}

	@Override
	public int interact(@Nonnull EnumFacing side, @Nonnull Vec3i offset, @Nonnull EntityPlayer player,
						@Nonnull EnumHand hand, @Nonnull ItemStack heldItem) {
		if (Utils.isHammer(heldItem)) {
			BlockFace s = new BlockFace(new BlockPos(offset), side);
			if (sides.isValid(s)) {
				if (!world.isRemote) {
					sides.cycleSide(s);
					world.markForUpdate(BlockPos.ORIGIN);
				}
				return 0b11;
			}
		}
		return -1;
	}

	@Override
	public IBlockState getExtState(IBlockState in) {
		in = super.getExtState(in);
		if (in instanceof IExtendedBlockState) {
			in = ((IExtendedBlockState) in).withProperty(IWProperties.MB_SIDES, sides);
		}
		return in;
	}

	protected abstract double getMaxBuffer();

	protected abstract boolean has4Phases();

	public abstract List<BlockFace> getEnergyConnections();

	private double outputFE(int available) {
		double extracted = 0;
		for (BlockFace output : getEnergyConnections()) {
			if (output.face==null||sides.getConfigForFace(output)!=OUTPUT)
				continue;
			BlockPos outTE = output.offset.offset(output.face);
			TileEntity te = world.getTileEntity(outTE);
			EnumFacing sideReal = world.transformedToReal(output.face).getOpposite();
			if (te != null && te.hasCapability(CapabilityEnergy.ENERGY, sideReal)) {
				IEnergyStorage energy = te.getCapability(CapabilityEnergy.ENERGY, sideReal);
				if (energy != null && energy.canReceive()) {
					int received = energy.receiveEnergy(available, false);
					available -= received;
					extracted += ConversionUtil.joulesPerIf() * received;
				}
			}
		}
		return extracted;
	}

	class EnergyStorageMMB implements IEnergyStorage {
		private SideConfig type;

		EnergyStorageMMB(SideConfig type) {
			this.type = type;
		}

		@Override
		public int receiveEnergy(int maxReceive, boolean simulate) {
			if (type!=INPUT) {
				return 0;
			}
			double buffer = bufferToMB;
			double input = maxReceive * ConversionUtil.joulesPerIf();
			if (!wfToMB.isAC()) {
				buffer = 0;
			}
			input = Math.min(input, getMaxBuffer() - buffer);
			buffer += input;
			if (!simulate) {
				bufferToMB = buffer;
				wfToMB = Waveform.forParameters(Waveform.Type.AC, get(has4Phases()), EXTERNAL);
			}
			return (int) (ConversionUtil.ifPerJoule() * input);
		}

		@Override
		public int extractEnergy(int maxExtract, boolean simulate) {
			if (!wfToWorld.isAC() || type!=OUTPUT) {
				return 0;
			}
			double buffer = bufferToWorld;
			double output = maxExtract * ConversionUtil.joulesPerIf();
			output = Math.min(output, buffer);
			buffer -= output;
			if (!simulate) {
				bufferToWorld = buffer;
			}
			return (int) (ConversionUtil.ifPerJoule() * output);
		}

		@Override
		public int getEnergyStored() {
			return (int) (ConversionUtil.ifPerJoule() * (bufferToWorld + bufferToMB));
		}

		@Override
		public int getMaxEnergyStored() {
			return (int) (2 * ConversionUtil.ifPerJoule() * getMaxBuffer());
		}

		@Override
		public boolean canExtract() {
			return true;
		}

		@Override
		public boolean canReceive() {
			return true;
		}
	}
}
