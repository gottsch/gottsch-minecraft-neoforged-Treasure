/*
 * This file is part of  Treasure2.
 * Copyright (c) 2018 Mark Gottschling (gottsch)
 * 
 * All rights reserved.
 *
 * Treasure2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Treasure2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Treasure2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.treasure2.core.generator.pit;

import java.util.Optional;

import mod.gottsch.neo.gottschcore.block.BlockContext;
import mod.gottsch.neo.gottschcore.random.WeightedCollection;
import mod.gottsch.neo.gottschcore.spatial.Coords;
import mod.gottsch.neo.gottschcore.spatial.ICoords;
import mod.gottsch.neo.gottschcore.world.IWorldGenContext;
import mod.gottsch.forge.treasure2.Treasure;
import mod.gottsch.forge.treasure2.core.generator.ChestGeneratorData;
import mod.gottsch.forge.treasure2.core.generator.GeneratorResult;
import mod.gottsch.forge.treasure2.core.generator.GeneratorUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 
 * @author Mark Gottschling
 *
 */
public class TntTrapPitGenerator extends AbstractPitGenerator {
	
	/**
	 * 
	 */
	public TntTrapPitGenerator() {
		super();
	}
	
	@Override
	public Optional<GeneratorResult<ChestGeneratorData>> generate(IWorldGenContext context, ICoords surfaceCoords, ICoords spawnCoords) {
		Optional<GeneratorResult<ChestGeneratorData>> result = super.generate(context, surfaceCoords, spawnCoords);
		if (result.isPresent()) {
			Treasure.LOGGER.debug("generated TntTrapPit at -> {}", spawnCoords.toShortString());
		}
		return result;
	}
	
	@Override
	public ICoords buildPit(IWorldGenContext context, ICoords coords, ICoords surfaceCoords, WeightedCollection<Integer, Block> col) {
		ICoords nextCoords = null;
		ICoords expectedCoords = null;
		
		// select mid-point of pit length - coords for trap
		int midY = (surfaceCoords.getY() + coords.getY())/2;
		ICoords midCoords = new Coords(coords.getX(), midY, coords.getZ());
		int deltaY = surfaceCoords.delta(midCoords).getY();
		
//		Treasure.logger.debug("TNT Trap pit from {} to {}", coords.getY() + Y_OFFSET, surfaceCoords.getY() - Y_SURFACE_OFFSET);
		// randomly fill shaft
		for (int yIndex = coords.getY() + OFFSET_Y; yIndex <= surfaceCoords.getY() - SURFACE_OFFSET_Y; yIndex++) {
			
			// if the block to be replaced is air block then skip to the next pos
			BlockContext blockContext = new BlockContext(context.level(), new Coords(coords.getX(), yIndex, coords.getZ()));
			if (blockContext.isAir()) {
				continue;
			}

			// check for midpoint and that there is enough room to build the trap
			if (yIndex == midCoords.getY() && deltaY > 4) {
				// build trap layer
				nextCoords = buildTrapLayer(context, blockContext.getCoords(), DEFAULT_LOG); // could have difference classes and implement buildLayer differently
			}
			else {
				// get the next type of block layer to build
				Block block = col.next();
				if (block == DEFAULT_LOG) {
					// special log build layer
					nextCoords = buildLogLayer(context, blockContext.getCoords(), block); // could have difference classes and implement buildLayer differently
				}
				else {
					nextCoords = buildLayer(context, blockContext.getCoords(), block);
				}
			}
			// get the expected coords
			expectedCoords = blockContext.getCoords().add(0, 1, 0);
			
			// check if the return coords is different than the anticipated coords and resolve
			yIndex = autoCorrectIndex(yIndex, nextCoords, expectedCoords);
//			Treasure.logger.debug("yIndex: {}", yIndex);
		}		
		return nextCoords;
	}
	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param coords
	 * @param block
	 * @return
	 */
	public ICoords buildTrapLayer(IWorldGenContext context, final ICoords coords, final Block block) {
		ICoords nextCoords = null;
		if (block == DEFAULT_LOG) {
			nextCoords = buildLogLayer(context, coords, block);
		}
		else {
			nextCoords = buildLayer(context, coords, block);
		}

		// ensure that the difference is only 1 between nextCoords and coords
//		if (nextCoords.delta(coords).getY() > 1) return nextCoords;

//		Block redstone = Blocks.REDSTONE_WIRE;
		GeneratorUtil.replaceWithBlock(context.level(), nextCoords.add(0, 0, 0), Blocks.TNT);
		GeneratorUtil.replaceWithBlock(context.level(), nextCoords.add(1, 0, 0), Blocks.TNT);
		GeneratorUtil.replaceWithBlock(context.level(), nextCoords.add(0, 0, 1), Blocks.TNT);
		GeneratorUtil.replaceWithBlock(context.level(), nextCoords.add(1, 0, 1), Blocks.TNT);
		
		nextCoords = nextCoords.up(1);
		
		// add aother  log layer
		nextCoords = buildLogLayer(context, nextCoords, block);
		// core 4-square pressure plate (above log)
		Block plate = Blocks.OAK_PRESSURE_PLATE;
		GeneratorUtil.replaceWithBlock(context.level(), nextCoords, plate);
		GeneratorUtil.replaceWithBlock(context.level(), nextCoords.add(1, 0, 0), plate);
		GeneratorUtil.replaceWithBlock(context.level(), nextCoords.add(0, 0, 1), plate);
		GeneratorUtil.replaceWithBlock(context.level(), nextCoords.add(1, 0, 1), plate);
						
		// get the next coords
		nextCoords = nextCoords.up(1);
		// return the next coords
		return nextCoords;
	}
}