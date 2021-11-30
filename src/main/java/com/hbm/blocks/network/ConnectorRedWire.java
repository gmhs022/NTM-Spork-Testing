package com.hbm.blocks.network;

import com.hbm.tileentity.network.TileEntityConnector;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class ConnectorRedWire extends PylonBase {

	public ConnectorRedWire(Material mat) {
		super(mat);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityConnector();
	}

}