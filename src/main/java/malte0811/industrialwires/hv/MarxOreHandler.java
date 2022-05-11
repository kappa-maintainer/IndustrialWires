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

package malte0811.industrialwires.hv;

import blusunrize.immersiveengineering.api.ApiUtils;
import blusunrize.immersiveengineering.api.crafting.IngredientStack;
import blusunrize.immersiveengineering.common.util.Utils;
import malte0811.industrialwires.IndustrialWires;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static net.minecraftforge.oredict.OreDictionary.getOres;

public class MarxOreHandler {
	private static final List<OreInfo> oreData = new ArrayList<>();
	public static double defaultEnergy = 100_000;
	public static double modifier;

	public static void preInit() {
		// Vanilla ores
		putOre("oreIron", .5, 4, "dustIron", "nuggetIron");
		putOre("oreGold", 1, 4, "dustGold", "nuggetGold");
		putOre("oreDiamond", 2, 4, "gemDiamond");
		putOre("oreEmerald", 2.25, 4, "gemEmerald");
		putOre("oreLapis", .75, 10, "gemLapis");
		putOre("oreCoal", .75, 8, Items.COAL, 0);
		putOre("oreRedstone", 1, 12, "dustRedstone");
		putOre("oreQuartz", 1, 6, "gemQuartz");
		// IE ores
		String[] ores = {"Copper", "Aluminum", "Lead", "Silver", "Nickel", "Tin"};
		for (String ore : ores) {
			putOre("ore" + ore, .75, 4, "dust" + ore, "nugget" + ore);
		}
		putOre("oreUranium", 1.25, 4,
				IndustrialWires.hasIC2?"crushedUranium":"dustUranium", "nuggetUranium");
		if(IndustrialWires.hasII) {
			String[] iiores = {"Platinum", "Zinc", "Lead", "Silver", "Nickel", "Tin"};

			putOre("orePlatinum", 1, 4, "dustPlatinum", "nuggetPlatinum");
			putOre("oreZinc", .75, 4, "dustZinc", "nuggetZinc");
			putOre("oreTungsten", 2, 4, "dustTungsten", "nuggetTungsten");
			putOre("oreSalt", .5, 4, "dustSalt");
			putOre("oreFluorite", .75, 4, "gemFluorite");
			putOre("orePhosphorus", .75, 4, "gemPhosphorus");

		}
	}

	public static void init() {
		oreData.removeIf(
				(info)->
                        info.exampleInput.isEmpty() || info.exampleInput.stream().allMatch(ItemStack::isEmpty)
                                || (info.output == null || info.output.get().isEmpty())
                                && (info.outputSmall == null || info.outputSmall.get().isEmpty())
                                && (info.blockOut == null || info.blockOut.get() == null)
		);
	}

	public static void putOre(String oreName, double avgEnergy, double maxYield, String oreOut) {
		putOre(oreName, avgEnergy, maxYield, oreOut, null);
	}
	public static void putOre(String oreName, double avgEnergy, double maxYield, String oreOut, @Nullable String oreSmall) {
		put(new OreInfo(new OreChecker(oreName), getOres(oreName), avgEnergy, maxYield, oreOut, oreSmall));
	}
	public static void putOre(String oreName, double avgEnergy, double maxYield, Item oreOut, int meta) {
        put(new OreInfo(new OreChecker(oreName), getOres(oreName), avgEnergy, maxYield, oreOut, meta, null));
	}

	public static void put(MarxOreHandler.OreInfo output) {
		oreData.add(output);
	}

	public static void resetModifier() {
		modifier = .9+Utils.RAND.nextDouble()*.2;
	}

	public static ItemStack[] getYield(World world, BlockPos pos, double energy) {
		if (modifier<.89||modifier>1.11) {
			IndustrialWires.logger.error("The energy-modifier for Marx generators wasn't loaded correctly. It will be reset.");
			resetModifier();
		}
		for (OreInfo ore:oreData) {
			if (ore.isValid.test(world, pos)) {
				double idealE = modifier * ore.avgEnergy * defaultEnergy;
				if (energy >= .75 * idealE) {
					double sigma = idealE / 9;
					double dist = getNormalizedNormalDist(energy, sigma, idealE);
					double out = dist * ore.maxYield;
					int yield = (int) Math.floor(out);
					out -= yield;
					int yieldNuggets = (int) Math.round(out * ore.smallMax);
                    if (yieldNuggets >= ore.smallMax || (ore.outputSmall == null && yieldNuggets >= ore.smallMax / 2F)) {
                        yield++;
                        yieldNuggets = 0;
                    }
                    if (yield > 0 && yieldNuggets > 0 && ore.outputSmall != null && ore.output != null) {
                        return new ItemStack[]{
                                ApiUtils.copyStackWithAmount(ore.output.get(), yield),
                                ApiUtils.copyStackWithAmount(ore.outputSmall.get(), yieldNuggets)
                        };
                    } else if (yield > 0 && ore.output != null) {
                        return new ItemStack[]{
                                ApiUtils.copyStackWithAmount(ore.output.get(), yield)
                        };
                    } else if (yieldNuggets > 0 && ore.outputSmall != null) {
                        return new ItemStack[]{
                                ApiUtils.copyStackWithAmount(ore.outputSmall.get(), yieldNuggets)
                        };
                    }
                }
            }
        }
        return new ItemStack[0];
    }

    public static IBlockState getBlockYield(World world, BlockPos pos, double energy) {
        if (modifier < .89 || modifier > 1.11) {
            IndustrialWires.logger.error("The energy-modifier for Marx generators wasn't loaded correctly. It will be reset.");
            resetModifier();
        }
        for (OreInfo ore : oreData) {
            if (ore.isValid.test(world, pos)) {
                double idealE = modifier * ore.avgEnergy * defaultEnergy;
                if (energy >= .75 * idealE && energy <= 1.25 * idealE && ore.blockOut != null && ore.blockOut.get() != null) {
                    return ore.blockOut.get();
                }
            }
        }
        return Blocks.AIR.getDefaultState();
    }

    private static double getNormalizedNormalDist(double x, double sigma, double mu) {
        return Math.exp(-(x - mu) * (x - mu) / (2 * sigma * sigma));
    }

    public static List<OreInfo> getRecipes() {
        return oreData;
    }

    public static class OreInfo {
        //Input
        public BiPredicate<World, BlockPos> isValid;
        public List<ItemStack> exampleInput;
        //Output
        public final double avgEnergy;
        public final double maxYield;
        public final Supplier<ItemStack> output;
        @Nullable
        public final Supplier<ItemStack> outputSmall;//1/9 of output
        public final int smallMax;
        @Nullable
        public final Supplier<IBlockState> blockOut;

        public OreInfo(BiPredicate<World, BlockPos> isValid, List<ItemStack> exampleInput, double avg, double maxYield,
                       @Nullable Supplier<ItemStack> out, @Nullable Supplier<ItemStack> outSmall, int smallCount, @Nullable IBlockState blockOut) {
            avgEnergy = avg;
            this.maxYield = maxYield;
            output = out;
            outputSmall = outSmall;
            smallMax = smallCount;
            this.isValid = isValid;
            this.exampleInput = exampleInput;
            this.blockOut = () -> blockOut;
        }

        public OreInfo(BiPredicate<World, BlockPos> isValid, List<ItemStack> exampleInput, double avgEnergy, double maxYield,
                       @Nullable Item iOut, int mOut, @Nullable Item iOutSmall, int mOutSmall, @Nullable IBlockState blockOut) {
            this.avgEnergy = avgEnergy;
            this.maxYield = maxYield;
            this.output = iOut == null ? null : new IngredientStack(new ItemStack(iOut, 1, mOut))::getExampleStack;
            this.outputSmall = iOutSmall == null ? null : new IngredientStack(new ItemStack(iOutSmall, 1, mOutSmall))::getExampleStack;
            smallMax = 9;
            this.isValid = isValid;
            this.exampleInput = exampleInput;
            this.blockOut = () -> blockOut;
        }

        public OreInfo(BiPredicate<World, BlockPos> isValid, List<ItemStack> exampleInput, double avgEnergy,
                       double maxYield, @Nullable Item iOut, int mOut, @Nullable IBlockState blockOut) {
            this(isValid, exampleInput, avgEnergy, maxYield, iOut, mOut, null, 0, blockOut);
        }

        public OreInfo(BiPredicate<World, BlockPos> isValid, List<ItemStack> exampleInput, double avgEnergy, double maxYield,
                       @Nullable String oreOut, @Nullable String oreSmall) {
            this.avgEnergy = avgEnergy;
            this.maxYield = maxYield;
            this.output = oreOut == null ? null : new IngredientStack(oreOut)::getExampleStack;
            this.outputSmall = oreSmall == null ? null : new IngredientStack(oreSmall)::getExampleStack;
            smallMax = 9;
            this.isValid = isValid;
            this.exampleInput = exampleInput;
            this.blockOut = () -> null;
        }

        public OreInfo(BiPredicate<World, BlockPos> isValid, IBlockState blockState, double avg, double maxYield,
                       @Nullable Supplier<ItemStack> out, @Nullable Supplier<ItemStack> outSmall, int smallCount, @Nullable IBlockState blockOut) {

            avgEnergy = avg;
            this.maxYield = maxYield;
            output = out;
            outputSmall = outSmall;
            smallMax = smallCount;
            this.isValid = isValid;
            this.exampleInput = getItem(blockState);
            this.blockOut = () -> blockOut;
        }

        public OreInfo(BiPredicate<World, BlockPos> isValid, List<ItemStack> exampleInput, double avgEnergy, double maxYield,
                       String oreOut) {
            this(isValid, exampleInput, avgEnergy, maxYield, oreOut, null);
        }

        private static ArrayList<ItemStack> getItem(IBlockState in) {
            ItemStack item = in.getBlock().getPickBlock(in, null, null, null, null);
            ArrayList<ItemStack> ilist = new ArrayList<>();
            if (!item.isEmpty()) {
                ilist.add(item);
                return ilist;
            }
            ilist.add(new ItemStack(in.getBlock()));
            return ilist;
        }
    }



	public static class OreChecker implements BiPredicate<World, BlockPos> {
		String oreName;
		public OreChecker(String ore) {
			oreName = ore;
		}
		@Override
		public boolean test(World world, BlockPos here) {
			IBlockState state = world.getBlockState(here);
			ItemStack input = state.getBlock().getPickBlock(state, null, world, here, null);
            if (input.isEmpty()) return false;
			int[] ores = OreDictionary.getOreIDs(input);
			for (int id : ores) {
				String name = OreDictionary.getOreName(id);
				if (name.equals(oreName)) {
					return true;
				}
			}
			return false;
		}
	}
}
