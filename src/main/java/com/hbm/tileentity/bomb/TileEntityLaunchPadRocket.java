package com.hbm.tileentity.bomb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.dim.CelestialBody;
import com.hbm.entity.missile.EntityRideableRocket;
import com.hbm.extprop.HbmPlayerProps;
import com.hbm.handler.CompatHandler;
import com.hbm.handler.RocketStruct;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.container.ContainerLaunchPadRocket;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUILaunchPadRocket;
import com.hbm.items.ItemVOTVdrive;
import com.hbm.items.ItemVOTVdrive.Target;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.ItemCustomRocket;
import com.hbm.lib.Library;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardReceiver;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityLaunchPadRocket extends TileEntityMachineBase implements IControlReceiver, IEnergyReceiverMK2, IFluidStandardReceiver, IGUIProvider, CompatHandler.OCComponent {

	public long power;
	public final long maxPower = 100_000;

	public int solidFuel = 0;
	public int maxSolidFuel = 0;

	public FluidTank[] tanks;

	public boolean canSeeSky = true;
	public RocketStruct rocket;
	public int height;

	public TileEntityLaunchPadRocket() {
		super(5); // 0 rocket, 1 drive, 2 battery, 3/4 liquid/solid fuel in/out
		tanks = new FluidTank[RocketStruct.MAX_STAGES * 2]; // enough tanks for any combination of rocket stages
		for(int i = 0; i < tanks.length; i++) tanks[i] = new FluidTank(Fluids.NONE, 64_000);
	}

	@Override
	public String getName() {
		return "container.launchPadRocket";
	}

	@SuppressWarnings("unchecked")
	@Override
	public void updateEntity() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		if(!worldObj.isRemote) {
			ItemVOTVdrive.getTarget(slots[1], worldObj);

			// Setup tanks required for the current rocket
			updateTanks();

			// Connections
			if(worldObj.getTotalWorldTime() % 20 == 0) {
				for(DirPos pos : getConPos()) {
					trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());

					if(hasRocket()) {
						for(FluidTank tank : tanks) {
							if(tank.getTankType() == Fluids.NONE) continue;
							trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
						}
					}
				}
			}

			// Fills, note that the liquid input also takes solid fuel
			power = Library.chargeTEFromItems(slots, 2, power, maxPower);
			for(FluidTank tank : tanks) tank.loadTank(3, 4, slots);
			if(slots[3] != null && slots[3].getItem() == ModItems.rocket_fuel && solidFuel < maxSolidFuel) {
				decrStackSize(3, 1);
				solidFuel += 250;
				if(solidFuel > maxSolidFuel) solidFuel = maxSolidFuel;
			}

			rocket = ItemCustomRocket.get(slots[0]);
			if(rocket != null) {
				int newHeight = MathHelper.floor_double(rocket.getHeight() - rocket.capsule.height + 1);
				if(newHeight <= 8) newHeight = 0;

				if(newHeight != height) {
					// Check that the pad is entirely unobstructed
					canSeeSky = !isPadObstructed();

					if(canSeeSky) {
						// Fill in the tower with structure blocks
						BlockDummyable.safeRem = true;

						int meta = ForgeDirection.UP.ordinal();

						// Build tower
						if(newHeight > height) {
							for(int oy = height + 3; oy < newHeight + 3; oy++) {
								if(yCoord + oy > 255) break;

								worldObj.setBlock(xCoord - rot.offsetX * 2 - dir.offsetX * 4, yCoord + oy, zCoord - rot.offsetZ * 2 - dir.offsetZ * 4, ModBlocks.launch_pad_rocket, meta, 3);
								worldObj.setBlock(xCoord - rot.offsetX * 2 - dir.offsetX * 5, yCoord + oy, zCoord - rot.offsetZ * 2 - dir.offsetZ * 5, ModBlocks.launch_pad_rocket, meta, 3);
								worldObj.setBlock(xCoord - rot.offsetX * 2 - dir.offsetX * 6, yCoord + oy, zCoord - rot.offsetZ * 2 - dir.offsetZ * 6, ModBlocks.launch_pad_rocket, meta, 3);

								worldObj.setBlock(xCoord - rot.offsetX * 3 - dir.offsetX * 4, yCoord + oy, zCoord - rot.offsetZ * 3 - dir.offsetZ * 4, ModBlocks.launch_pad_rocket, meta, 3);
								worldObj.setBlock(xCoord - rot.offsetX * 4 - dir.offsetX * 4, yCoord + oy, zCoord - rot.offsetZ * 4 - dir.offsetZ * 4, ModBlocks.launch_pad_rocket, meta, 3);

								worldObj.setBlock(xCoord - rot.offsetX * 3 - dir.offsetX * 6, yCoord + oy, zCoord - rot.offsetZ * 3 - dir.offsetZ * 6, ModBlocks.launch_pad_rocket, meta, 3);
								worldObj.setBlock(xCoord - rot.offsetX * 4 - dir.offsetX * 6, yCoord + oy, zCoord - rot.offsetZ * 4 - dir.offsetZ * 6, ModBlocks.launch_pad_rocket, meta, 3);
							}
						} else {
							for(int oy = height + 3; oy >= newHeight + 3; oy--) {
								if(yCoord + oy > 255) continue;

								worldObj.setBlockToAir(xCoord - rot.offsetX * 2 - dir.offsetX * 4, yCoord + oy, zCoord - rot.offsetZ * 2 - dir.offsetZ * 4);
								worldObj.setBlockToAir(xCoord - rot.offsetX * 2 - dir.offsetX * 5, yCoord + oy, zCoord - rot.offsetZ * 2 - dir.offsetZ * 5);
								worldObj.setBlockToAir(xCoord - rot.offsetX * 2 - dir.offsetX * 6, yCoord + oy, zCoord - rot.offsetZ * 2 - dir.offsetZ * 6);

								worldObj.setBlockToAir(xCoord - rot.offsetX * 3 - dir.offsetX * 4, yCoord + oy, zCoord - rot.offsetZ * 3 - dir.offsetZ * 4);
								worldObj.setBlockToAir(xCoord - rot.offsetX * 4 - dir.offsetX * 4, yCoord + oy, zCoord - rot.offsetZ * 4 - dir.offsetZ * 4);

								worldObj.setBlockToAir(xCoord - rot.offsetX * 3 - dir.offsetX * 6, yCoord + oy, zCoord - rot.offsetZ * 3 - dir.offsetZ * 6);
								worldObj.setBlockToAir(xCoord - rot.offsetX * 4 - dir.offsetX * 6, yCoord + oy, zCoord - rot.offsetZ * 4 - dir.offsetZ * 6);
							}
						}

						// Build standable platform after removing old platform
						if(height >= 8) {
							worldObj.setBlockToAir(xCoord - rot.offsetX * 2 - dir.offsetX * 3, yCoord + height + 2, zCoord - rot.offsetZ * 2 - dir.offsetZ * 3);

							worldObj.setBlockToAir(xCoord - rot.offsetX * 1 - dir.offsetX * 1, yCoord + height + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 1);
							worldObj.setBlockToAir(xCoord - rot.offsetX * 1 - dir.offsetX * 2, yCoord + height + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 2);
							worldObj.setBlockToAir(xCoord - rot.offsetX * 1 - dir.offsetX * 3, yCoord + height + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 3);
							worldObj.setBlockToAir(xCoord - rot.offsetX * 1 - dir.offsetX * 4, yCoord + height + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 4);
							worldObj.setBlockToAir(xCoord - rot.offsetX * 1 - dir.offsetX * 5, yCoord + height + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 5);
							worldObj.setBlockToAir(xCoord - rot.offsetX * 1 - dir.offsetX * 6, yCoord + height + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 6);

							worldObj.setBlockToAir(xCoord - dir.offsetX * 1, yCoord + height + 2, zCoord - dir.offsetZ * 1);
							worldObj.setBlockToAir(xCoord - dir.offsetX * 2, yCoord + height + 2, zCoord - dir.offsetZ * 2);
							worldObj.setBlockToAir(xCoord - dir.offsetX * 3, yCoord + height + 2, zCoord - dir.offsetZ * 3);
							worldObj.setBlockToAir(xCoord - dir.offsetX * 4, yCoord + height + 2, zCoord - dir.offsetZ * 4);
							worldObj.setBlockToAir(xCoord - dir.offsetX * 5, yCoord + height + 2, zCoord - dir.offsetZ * 5);
							worldObj.setBlockToAir(xCoord - dir.offsetX * 6, yCoord + height + 2, zCoord - dir.offsetZ * 6);

							worldObj.setBlockToAir(xCoord + rot.offsetX * 1 - dir.offsetX * 1, yCoord + height + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 1);
							worldObj.setBlockToAir(xCoord + rot.offsetX * 1 - dir.offsetX * 2, yCoord + height + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 2);
							worldObj.setBlockToAir(xCoord + rot.offsetX * 1 - dir.offsetX * 3, yCoord + height + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 3);
							worldObj.setBlockToAir(xCoord + rot.offsetX * 1 - dir.offsetX * 4, yCoord + height + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 4);
							worldObj.setBlockToAir(xCoord + rot.offsetX * 1 - dir.offsetX * 5, yCoord + height + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 5);
							worldObj.setBlockToAir(xCoord + rot.offsetX * 1 - dir.offsetX * 6, yCoord + height + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 6);
						}

						if(newHeight >= 8) {
							worldObj.setBlock(xCoord - rot.offsetX * 2 - dir.offsetX * 3, yCoord + newHeight + 2, zCoord - rot.offsetZ * 2 - dir.offsetZ * 3, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);

							worldObj.setBlock(xCoord - rot.offsetX * 1 - dir.offsetX * 1, yCoord + newHeight + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 1, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord - rot.offsetX * 1 - dir.offsetX * 2, yCoord + newHeight + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 2, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord - rot.offsetX * 1 - dir.offsetX * 3, yCoord + newHeight + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 3, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord - rot.offsetX * 1 - dir.offsetX * 4, yCoord + newHeight + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 4, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);
							worldObj.setBlock(xCoord - rot.offsetX * 1 - dir.offsetX * 5, yCoord + newHeight + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 5, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);
							worldObj.setBlock(xCoord - rot.offsetX * 1 - dir.offsetX * 6, yCoord + newHeight + 2, zCoord - rot.offsetZ * 1 - dir.offsetZ * 6, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);

							worldObj.setBlock(xCoord - dir.offsetX * 1, yCoord + newHeight + 2, zCoord - dir.offsetZ * 1, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord - dir.offsetX * 2, yCoord + newHeight + 2, zCoord - dir.offsetZ * 2, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord - dir.offsetX * 3, yCoord + newHeight + 2, zCoord - dir.offsetZ * 3, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord - dir.offsetX * 4, yCoord + newHeight + 2, zCoord - dir.offsetZ * 4, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);
							worldObj.setBlock(xCoord - dir.offsetX * 5, yCoord + newHeight + 2, zCoord - dir.offsetZ * 5, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);
							worldObj.setBlock(xCoord - dir.offsetX * 6, yCoord + newHeight + 2, zCoord - dir.offsetZ * 6, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);

							worldObj.setBlock(xCoord + rot.offsetX * 1 - dir.offsetX * 1, yCoord + newHeight + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 1, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord + rot.offsetX * 1 - dir.offsetX * 2, yCoord + newHeight + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 2, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord + rot.offsetX * 1 - dir.offsetX * 3, yCoord + newHeight + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 3, ModBlocks.launch_pad_rocket, dir.ordinal(), 3);
							worldObj.setBlock(xCoord + rot.offsetX * 1 - dir.offsetX * 4, yCoord + newHeight + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 4, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);
							worldObj.setBlock(xCoord + rot.offsetX * 1 - dir.offsetX * 5, yCoord + newHeight + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 5, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);
							worldObj.setBlock(xCoord + rot.offsetX * 1 - dir.offsetX * 6, yCoord + newHeight + 2, zCoord + rot.offsetZ * 1 - dir.offsetZ * 6, ModBlocks.launch_pad_rocket, rot.ordinal(), 3);
						}

						BlockDummyable.safeRem = false;
					}

					height = newHeight;
				}
			}

			networkPackNT(250);
		}

		List<EntityPlayer> sideLadderPlayers = worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox(xCoord + 0.25, yCoord, zCoord + 0.25, xCoord + 0.75, yCoord + 3, zCoord + 0.75).offset(-rot.offsetX * 6.5 - dir.offsetX * 5, 0, -rot.offsetZ * 6.5 - dir.offsetZ * 5));
		for(EntityPlayer player : sideLadderPlayers)
			HbmPlayerProps.getData(player).isOnLadder = true;

		if(height >= 8) {
			List<EntityPlayer> mainLadderPlayers = worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox(xCoord + 0.25, yCoord + 3, zCoord + 0.25, xCoord + 0.75, yCoord + 3 + height, zCoord + 0.75).offset(-rot.offsetX * 2.5 - dir.offsetX * 5, 0, -rot.offsetZ * 2.5 - dir.offsetZ * 5));
			for(EntityPlayer player : mainLadderPlayers)
				HbmPlayerProps.getData(player).isOnLadder = true;
		}
	}

	private boolean isPadObstructed() {
		for(int ox = 0; ox <= 0; ox++) {
			for(int oz = 0; oz <= 0; oz++) {
				if(!worldObj.canBlockSeeTheSky(xCoord + ox, yCoord + 2, zCoord + oz)) {
					return true;
				}
			}
		}

		return false;
	}

	private DirPos[] conPos;

	public DirPos[] getConPos() {
		if(conPos == null) {
			conPos = new DirPos[13]; // 12 + 1 inputs

			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

			int i = 0;

			for(int or = 1; or < 5; or++) {
				for(int oy = 0; oy < 3; oy++) {
					conPos[i++] = new DirPos(xCoord - rot.offsetX * or - dir.offsetX * 8, yCoord + oy, zCoord - rot.offsetZ * or - dir.offsetZ * 8, dir.getOpposite());
				}
			}
			conPos[i++] = new DirPos(xCoord + rot.offsetX * 3 - dir.offsetX * 8, yCoord, zCoord + rot.offsetZ * 3 - dir.offsetZ * 8, dir.getOpposite());
		}

		return conPos;
	}

	public void launch(EntityPlayer player) {
		if(!canLaunch()) return;

		EntityRideableRocket rocket = new EntityRideableRocket(worldObj, xCoord + 0.5F, yCoord + 3.0F, zCoord + 0.5F, slots[0]).withProgram(slots[1]).launchedBy(player);
		worldObj.spawnEntityInWorld(rocket);

		// Deplete all fills
		for(int i = 0; i < tanks.length; i++) tanks[i] = new FluidTank(Fluids.NONE, 64_000);
		solidFuel = maxSolidFuel = 0;

		power -= maxPower * 0.75;

		slots[0] = null;
		slots[1] = null;
	}

	private boolean hasRocket() {
		return ItemCustomRocket.get(slots[0]) != null;
	}

	private boolean hasDrive() {
		return slots[1] != null && slots[1].getItem() instanceof ItemVOTVdrive;
	}

	private boolean areTanksFull() {
		for(FluidTank tank : tanks) if(tank.getTankType() != Fluids.NONE && tank.getFill() < tank.getMaxFill()) return false;
		if(solidFuel < maxSolidFuel) return false;
		return true;
	}

	private boolean canReachDestination() {
		// Check that the drive is processed
		if(!ItemVOTVdrive.getProcessed(slots[1])) {
			return false;
		}

		Target from = CelestialBody.getTarget(worldObj, xCoord, zCoord);
		Target to = ItemVOTVdrive.getTarget(slots[1], worldObj);

		RocketStruct rocket = ItemCustomRocket.get(slots[0]);

		if(!to.isValid && rocket.capsule.part != ModItems.rp_station_core_20) return false;
		if(to.isValid && rocket.capsule.part == ModItems.rp_station_core_20) return false;

		// Check if the stage can make the journey
		return rocket.hasSufficientFuel(from.body, to.body, from.inOrbit, to.inOrbit);
	}

	public boolean canLaunch() {
		return hasRocket() && hasDrive() && power >= maxPower * 0.75 && areTanksFull() && canReachDestination();
	}

	private void updateTanks() {
		if(!hasRocket()) return;

		RocketStruct rocket = ItemCustomRocket.get(slots[0]);
		Map<FluidType, Integer> fuels = rocket.getFillRequirement();

		// If the rocket is already fueled, unmark it and fill the tanks
		boolean hasFuel = ItemCustomRocket.hasFuel(slots[0]);
		if(hasFuel) ItemCustomRocket.setFuel(slots[0], false);

		// Remove solid fuels (listed as NONE fluid) from tank updates
		if(fuels.containsKey(Fluids.NONE)) {
			maxSolidFuel = fuels.get(Fluids.NONE);
			if(hasFuel) solidFuel = maxSolidFuel;
			fuels.remove(Fluids.NONE);
		} else {
			maxSolidFuel = 0;
		}

		// Check to see if any of the current tanks already fulfil fuelling requirements
		List<FluidTank> keepTanks = new ArrayList<FluidTank>();
		for(FluidTank tank : tanks) {
			if(fuels.containsKey(tank.getTankType())) {
				tank.changeTankSize(fuels.get(tank.getTankType()));
				keepTanks.add(tank);
				fuels.remove(tank.getTankType());
			}
		}

		// Add new tanks
		for(Entry<FluidType, Integer> entry : fuels.entrySet()) {
			keepTanks.add(new FluidTank(entry.getKey(), entry.getValue()));
		}

		// Fill tanks if rocket had fuel
		if(hasFuel) {
			for(FluidTank tank : keepTanks) {
				tank.setFill(tank.getMaxFill());
			}
		}

		// Sort and fill the tank array to place NONE at the end
		keepTanks.sort((a, b) -> b.getTankType().getID() - a.getTankType().getID());
		while(keepTanks.size() < RocketStruct.MAX_STAGES * 2) {
			keepTanks.add(new FluidTank(Fluids.NONE, 64_000));
		}

		tanks = keepTanks.toArray(new FluidTank[RocketStruct.MAX_STAGES * 2]);
	}

	public List<String> findIssues() {
		List<String> issues = new ArrayList<String>();

		if(!hasRocket()) return issues;

		// Check that the rocket is fully fueled and capable of leaving our starting planet
		RocketStruct rocket = ItemCustomRocket.get(slots[0]);

		if(!canSeeSky) {
			issues.add(EnumChatFormatting.RED + "Pad is obstructed");
		}

		if(power < maxPower * 0.75) {
			issues.add(EnumChatFormatting.RED + "Insufficient power");
		}

		for(FluidTank tank : tanks) {
			if(tank.getTankType() == Fluids.NONE) continue;
			int fill = tank.getFill();
			int maxFill = tank.getMaxFill();
			String tankName = tank.getTankType().getLocalizedName();
			if(tankName.contains(" ")) {
				String[] split = tankName.split(" ");
				tankName = split[split.length - 1];
			}
			if(fill < maxFill) {
				issues.add(EnumChatFormatting.YELLOW + "" + fill + "/" + maxFill + "mB " + tankName);
			} else {
				issues.add(EnumChatFormatting.GREEN + "" + fill + "/" + maxFill + "mB " + tankName);
			}
		}

		if(maxSolidFuel > 0) {
			if(solidFuel < maxSolidFuel) {
				issues.add(EnumChatFormatting.YELLOW + "" + solidFuel + "/" + maxSolidFuel + "kg Solid Fuel");
			} else {
				issues.add(EnumChatFormatting.GREEN + "" + solidFuel + "/" + maxSolidFuel + "kg Solid Fuel");
			}
		}

		if(!hasDrive()) {
			issues.add(EnumChatFormatting.YELLOW + "No destination drive installed");
			return issues;
		}

		if(!ItemVOTVdrive.getProcessed(slots[1])) {
			issues.add(EnumChatFormatting.RED + "Destination drive needs processing");
			return issues;
		}

		// Check that the rocket is actually capable of reaching our destination
		Target from = CelestialBody.getTarget(worldObj, xCoord, zCoord);
		Target to = ItemVOTVdrive.getTarget(slots[1], worldObj);

		if(to.inOrbit && !to.isValid && rocket.capsule.part != ModItems.rp_station_core_20) {
			issues.add(EnumChatFormatting.RED + "Station not yet launched");
		}

		if(to.inOrbit && to.isValid && rocket.capsule.part == ModItems.rp_station_core_20) {
			issues.add(EnumChatFormatting.RED + "Station already launched");
		}

		if(!rocket.hasSufficientFuel(from.body, to.body, from.inOrbit, to.inOrbit)) {
			issues.add(EnumChatFormatting.RED + "Rocket can't reach destination");
		}

		return issues;
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);

		buf.writeLong(power);
		buf.writeInt(solidFuel);
		buf.writeInt(maxSolidFuel);

		buf.writeInt(height);
		buf.writeBoolean(canSeeSky);

		if(rocket != null) {
			buf.writeBoolean(true);
			rocket.writeToByteBuffer(buf);
		} else {
			buf.writeBoolean(false);
		}

		for(int i = 0; i < tanks.length; i++) tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);

		power = buf.readLong();
		solidFuel = buf.readInt();
		maxSolidFuel = buf.readInt();

		height = buf.readInt();
		canSeeSky = buf.readBoolean();

		if(buf.readBoolean()) {
			rocket = RocketStruct.readFromByteBuffer(buf);
		} else {
			rocket = null;
		}

		for(int i = 0; i < tanks.length; i++) tanks[i].deserialize(buf);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", power);
		nbt.setInteger("solid", solidFuel);
		nbt.setInteger("maxSolid", maxSolidFuel);
		nbt.setInteger("height", height);
		nbt.setBoolean("sky", canSeeSky);
		for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "t" + i);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		power = nbt.getLong("power");
		solidFuel = nbt.getInteger("solid");
		maxSolidFuel = nbt.getInteger("maxSolid");
		height = nbt.getInteger("height");
		canSeeSky = nbt.getBoolean("sky");
		for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "t" + i);
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {
		if(data.getBoolean("launch")) {
			launch(player);
		}
	}

	// yo i was promised some crazy things if i did this so here you go
	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "ntm_rocket_pad";
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getEnergyInfo(Context context, Arguments args) {
		return new Object[] {getPower(), getMaxPower()};
	}

	@Callback(direct = true) // this doesn't return a set amount of tanks sadly.
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFuel(Context context, Arguments args) {
		List<Object[]> returnValues = new ArrayList<>();
		for (FluidTank tank : tanks) {
			if(tank.getTankType() != Fluids.NONE) {
				returnValues.add(new Object[] {
						tank.getFill(),
						tank.getMaxFill(),
						tank.getTankType().getName()
				});
			}
		}
		/* the return format should look something like the following:
			{{tank_1_fill, tank_1_max, tank_1_type},
			{tank_2_fill, tank_2_max, tank_2_type}}
		 */
		return returnValues.toArray();
	}

	@Callback(direct = true) // this doesn't return a set amount of tanks sadly
	@Optional.Method(modid = "OpenComputers")
	public Object[] getSolidFuel(Context context, Arguments args) {
		return new Object[] {solidFuel, maxSolidFuel};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] canLaunch(Context context, Arguments args) {
		return new Object[] {canLaunch()};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getRocketStats(Context context, Arguments args) {
		if(hasRocket()) {
			return new Object[] {
					rocket.stages.size(),
					rocket.getLaunchMass(),
					rocket.getHeight()
			};
		}
		return new Object[] {null, ""};
	}

	@Callback(direct = true, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] launch(Context context, Arguments args) {
		// doesn't really "launch" it per-say, just spawns the rocket, so I guess this works
		launch(null);
		// update: it so worked!
		return new Object[] {};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getDestination(Context context, Arguments args) {
		if(hasDrive()) { // ok maybe I should actually check if there's an item there first
			return new Object[] {null, "No destination drive."};
		}
		Target target = ItemVOTVdrive.getTarget(slots[1], null);
		if(target.body != null) {
			return new Object[] {target.body.name.toLowerCase()};
		}
		return new Object[] {null, "Drive has no destination."};
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public boolean canConnectNode(ForgeDirection side) {
		// Get direction of ports.
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		// Only connect if port is facing outwards, mainly to prevent component clutter with the ports connecting to eachother.
		return side == dir;
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String[] methods() {
		return new String[] {
				"getEnergyInfo",
				"getFuel",
				"getSolidFuel",
				"canLaunch",
				"getRocketStats",
				"getDestination",
				"launch"
		};
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public Object[] invoke(String method, Context context, Arguments args) throws Exception {
		switch(method) {
			case ("getEnergyInfo"):
				return getEnergyInfo(context, args);
			case ("getFuel"):
				return getFuel(context, args);
			case ("getSolidFuel"):
				return getSolidFuel(context, args);
			case ("canLaunch"):
				return canLaunch(context, args);
			case ("getRocketStats"):
				return getRocketStats(context, args);
			case ("getDestination"):
				return getDestination(context, args);
			case ("launch"):
				return launch(context, args);
		}
		throw new NoSuchMethodException();
	}

	@Override public long getPower() { return power; }
	@Override public void setPower(long power) { this.power = power; }
	@Override public long getMaxPower() { return maxPower; }
	@Override public FluidTank[] getAllTanks() { return this.tanks; }
	@Override public FluidTank[] getReceivingTanks() { return this.tanks; }

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerLaunchPadRocket(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUILaunchPadRocket(player.inventory, this);
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return INFINITE_EXTENT_AABB; // hi martin ;)
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		if(worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) {
			return false;
		} else {
			return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 1024;
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

}
