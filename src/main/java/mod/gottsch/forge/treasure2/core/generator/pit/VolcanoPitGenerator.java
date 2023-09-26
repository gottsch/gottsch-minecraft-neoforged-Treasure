/*
 * This file is part of  Treasure2.
 * Copyright (c) 2020 Mark Gottschling (gottsch)
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
import java.util.Random;

import mod.gottsch.neo.gottschcore.block.BlockContext;
import mod.gottsch.neo.gottschcore.random.RandomHelper;
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
 * A Volcano Pit requires at least 15 blocks under the surface to construct.
 * @author Mark Gottschling on Sep 5, 2020
 *
 */
public class VolcanoPitGenerator extends AbstractPitGenerator {
	private static final int MIN_VOLCANO_RADIUS = 4;
    private static final int MAX_VOLCANO_RADIUS = 8;
    private static final int MIN_VERTICAL_DISTANCE = 15;
    
	/**
	 * 
	 */
	public VolcanoPitGenerator() {
		getBlockLayers().add(50, Blocks.AIR);
		getBlockLayers().add(25, Blocks.SAND);
		getBlockLayers().add(15, Blocks.COBBLESTONE);
		getBlockLayers().add(15, Blocks.GRAVEL);
		getBlockLayers().add(10, DEFAULT_LOG);
	}
	
    @Override
	public Optional<GeneratorResult<ChestGeneratorData>> generate(IWorldGenContext context, ICoords surfaceCoords, ICoords spawnCoords) {
		Optional<GeneratorResult<ChestGeneratorData>> result = super.generate(context, surfaceCoords, spawnCoords);
		if (result.isPresent()) {
			Treasure.LOGGER.debug("generated VolcanoPit at -> {}", spawnCoords.toShortString());
		}
		return result;
	}
	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param spawnCoords
	 * @param surfaceCoords
	 * @return
	 */
	@Override
	public ICoords buildPit(IWorldGenContext context, ICoords coords, ICoords surfaceCoords, WeightedCollection<Integer, Block> col) {
		ICoords nextCoords = null;
		ICoords expectedCoords = null;
        
        // determine size of volcano
		int radius = RandomHelper.randomInt(context.random(), MIN_VOLCANO_RADIUS, MAX_VOLCANO_RADIUS); // min of 4, so diameter = 9 (4*2 + 1 (center)), area = 9x9
        
		// select 2/3 point of pit length - topmost coords of volcano chamber / bottom of pit shaft
		int shaftStartY = coords.getY() + ((surfaceCoords.getY() - coords.getY()) / 3 * 2);

		// build lava around base
		buildLavaBaseLayer(context, coords.down(1), radius);

        nextCoords = coords;
        while (nextCoords.getY() < (shaftStartY - 4)) {
            nextCoords = buildLayer(context, nextCoords, radius, Blocks.AIR, true);
        }

        // taper in until 2/3 point is reached
        while (nextCoords.getY() < shaftStartY && radius > 1) {
            nextCoords = buildLayer(context, nextCoords, radius--, Blocks.AIR, false);
        }

        // build one layer of logs
        nextCoords = buildLogLayer(context, nextCoords, DEFAULT_LOG);
        
        // build shaft
		for (int yIndex = nextCoords.getY() + 1; yIndex <= surfaceCoords.getY() - SURFACE_OFFSET_Y; yIndex++) {
			// if the block to be replaced is air block then skip to the next pos
			BlockContext blockContext = new BlockContext(context.level(), new Coords(coords.getX(), yIndex, coords.getZ()));
			if (blockContext.isAir()) {
				continue;
			}

			// get the next type of block layer to build
			Block block = col.next();
			if (block == DEFAULT_LOG) {
				// special log build layer
				nextCoords = buildLogLayer(context, blockContext.getCoords(), block); // could have difference classes and implement buildLayer differently
			}
			else {
				nextCoords = buildLayer(context, blockContext.getCoords(), block);
			}

			// get the expected coords
			expectedCoords = blockContext.getCoords().add(0, 1, 0);
			
			// check if the return coords is different than the anticipated coords and resolve
			yIndex = autoCorrectIndex(yIndex, nextCoords, expectedCoords);
		}		
		return nextCoords;
	}
	
	/**
	 * 
	 */
	@Override
	public void buildAboveChestLayers(IWorldGenContext context, ICoords spawnCoords) {
		
	}
	
    /**
     * 
     */
    private ICoords buildLayer(IWorldGenContext context, ICoords coords, int radius, Block block, boolean addDecorations) {
		int radiusSquared = radius * radius;
		Integer[] distancesMet = new Integer[radius + 1];
		ICoords spawnCoords = null;
		for (int xOffset = -(radius); xOffset <= radius; xOffset++) {
			for (int zOffset = -(radius); zOffset <= radius; zOffset++) {
				boolean isDistanceMet = false;
				spawnCoords = coords.add(xOffset, 0, zOffset);
				if (distancesMet[Math.abs(xOffset)] != null) {
					if (Math.abs(zOffset) <= distancesMet[Math.abs(xOffset)]) {
						isDistanceMet = true;
					}
				}
				else {
					if (coords.getDistanceSq(spawnCoords) < radiusSquared) {
						distancesMet[Math.abs(xOffset)] = Math.abs(zOffset);
						isDistanceMet = true;
					}
				}

				if (isDistanceMet) {
					Random random = new Random();
                    GeneratorUtil.replaceWithBlock(context.level(), spawnCoords, block);
                    
                    if (addDecorations) {
	                    if (xOffset < 0) {
	                        ICoords replaceCoords = spawnCoords.west(1);
	                        addDecorations(context, replaceCoords);
	                    }
	                    else if (xOffset > 0) {
	                        ICoords replaceCoords = spawnCoords.east(1);
	                        addDecorations(context, replaceCoords);
	                    }
	
	                    if (zOffset < 0) {
	                        ICoords replaceCoords = spawnCoords.north(1);
	                        addDecorations(context, replaceCoords);
	                    }
	                    else if (zOffset > 0) {
	                        ICoords replaceCoords = spawnCoords.south(1);
	                        addDecorations(context, replaceCoords);
	                    }
                    }
                }
			}
        }
        
        return coords.up(1);
    }

    /**
     * 
     */
    private void addDecorations(IWorldGenContext context, ICoords coords) {
        if (context.level().getBlockState(coords.toPos()).getBlock() != Blocks.AIR) {
            if (RandomHelper.checkProbability(context.random(), 30)) {
                context.level().setBlock(coords.toPos(), Blocks.BLACKSTONE.defaultBlockState(), 3);
            }
            else if (RandomHelper.checkProbability(context.random(), 10)) {
            	context.level().setBlock(coords.toPos(), Blocks.LAVA.defaultBlockState(), 3);
            }
        }
    }

	/**
	 * 
	 * @param world
	 * @param coords
	 */
	private void buildLavaBaseLayer(IWorldGenContext context, ICoords coords, int radius) {
        Treasure.LOGGER.debug("building lava baselayer from @ {} ", coords.toShortString());

        // for circular chamber
        buildLayer(context, coords, radius, Blocks.LAVA, false);

        // add the chest
        GeneratorUtil.replaceWithBlock(context.level(), coords, Blocks.STONE);	
	}
	
	@Override
	public int getMinSurfaceToSpawnDistance() {
		return MIN_VERTICAL_DISTANCE;
	}
}