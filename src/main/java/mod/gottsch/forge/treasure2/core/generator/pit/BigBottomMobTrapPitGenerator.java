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

import mod.gottsch.neo.gottschcore.block.entity.ProximitySpawnerBlockEntity;
import mod.gottsch.neo.gottschcore.size.DoubleRange;
import mod.gottsch.neo.gottschcore.spatial.Coords;
import mod.gottsch.neo.gottschcore.spatial.ICoords;
import mod.gottsch.neo.gottschcore.world.IWorldGenContext;
import mod.gottsch.forge.treasure2.Treasure;
import mod.gottsch.forge.treasure2.core.block.TreasureBlocks;
import mod.gottsch.forge.treasure2.core.generator.ChestGeneratorData;
import mod.gottsch.forge.treasure2.core.generator.GeneratorResult;
import mod.gottsch.forge.treasure2.core.generator.GeneratorUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.DungeonHooks;


/**
 * Generates lava blocks outside the main pit to prevent players from digging down on the edges
 * @author Mark Gottschling on Dec 9, 2018
 *
 */
public class BigBottomMobTrapPitGenerator extends AbstractPitGenerator {
	
	/**
	 * 
	 */
	public BigBottomMobTrapPitGenerator() {
		super();
	}
	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param surfaceCoords
	 * @param spawnCoords
	 * @return
	 */
	@Override
	public Optional<GeneratorResult<ChestGeneratorData>> generate(IWorldGenContext context, ICoords surfaceCoords, ICoords spawnCoords) {
		GeneratorResult<ChestGeneratorData> result = new GeneratorResult<>(ChestGeneratorData.class);
		result.getData().setSpawnCoords(spawnCoords);
		result.getData().setCoords(spawnCoords);
		
		// is the chest placed in a cavern
		boolean inCavern = false;
		
		// check above if there is a free space - chest may have spawned in underground cavern, ravine, dungeon etc
		BlockState blockState = context.level().getBlockState(spawnCoords.add(0, 1, 0).toPos());
		
		// if there is air above the origin, then in cavern. (pos in isAir() doesn't matter)
		if (blockState == null || blockState.isAir()) {
			Treasure.LOGGER.debug("spawn coords is in cavern.");
			inCavern = true;
		}
		
		if (inCavern) {
			Treasure.LOGGER.debug("finding cavern ceiling.");
			spawnCoords = GeneratorUtil.findSubterraneanCeiling(context.level(), spawnCoords.add(0, 1, 0));
			if (spawnCoords == null) {
				Treasure.LOGGER.warn("unable to locate cavern ceiling.");
				return Optional.empty();
			}
			// update chest coords
			result.getData().setSpawnCoords(spawnCoords);
			result.getData().setCoords(spawnCoords);
		}
	
		// generate shaft
		int yDist = (surfaceCoords.getY() - spawnCoords.getY()) - 2;
		Treasure.LOGGER.debug("Distance to ySurface =" + yDist);
	
		ICoords nextCoords = null;
		if (yDist > 6) {			
			Treasure.LOGGER.debug("Generating shaft @ " + spawnCoords.toShortString());
			// at chest level
			nextCoords = build6WideLayer(context, spawnCoords, Blocks.AIR);
			
			// above the chest
			nextCoords = build6WideLayer(context, nextCoords, Blocks.AIR);
			nextCoords = build6WideLayer(context, nextCoords, Blocks.AIR);
			nextCoords = buildLogLayer(context, nextCoords, DEFAULT_LOG);
			nextCoords = buildLayer(context, nextCoords, Blocks.SAND);
			
			// shaft enterance
			buildLogLayer(context, surfaceCoords.add(0, -3, 0), DEFAULT_LOG);
			buildLayer(context, surfaceCoords.add(0, -4, 0), Blocks.SAND);
			buildLogLayer(context, surfaceCoords.add(0, -5, 0), DEFAULT_LOG);

			// build the trap
			buildTrapLayer(context, spawnCoords, null);
			
			// build the pit
			// NOTE must add nextCoords by Y_OFFSET, because the AbstractPitGen.buildPit() starts with the Y_OFFSET, which is above the standard chest area.
			buildPit(context, nextCoords.down(OFFSET_Y), surfaceCoords, getBlockLayers());
		}			
		// shaft is only 2-6 blocks long - can only support small covering
		else if (yDist >= 2) {
			// simple short pit
			return new SimpleShortPitGenerator().generate(context, surfaceCoords, spawnCoords);
		}		
		Treasure.LOGGER.debug("generated BigBottomMobTrap Pit at -> {}", spawnCoords.toShortString());
		return Optional.ofNullable(result);
	}	

	/**
	 * 
	 * @param world
	 * @param random
	 * @param coords
	 * @param block
	 * @return
	 */
	private ICoords build6WideLayer(IWorldGenContext context, ICoords coords, Block block) {
		ICoords startCoords = coords.add(-2, 0, -2);
		for (int x = startCoords.getX(); x < startCoords.getX() + 6; x++) {
			for (int z = startCoords.getZ(); z < startCoords.getZ() + 6; z++) {
				GeneratorUtil.replaceWithBlockState(context.level(), new Coords(x, coords.getY(), z), block.defaultBlockState());
			}
		}
		return coords.add(0, 1, 0);
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
  	
		// spawn random registered mobs on either side of the chest
    	context.level().setBlock(coords.add(-1, 0, 0).toPos(), TreasureBlocks.PROXIMITY_SPAWNER.get().defaultBlockState(), 3);
    	ProximitySpawnerBlockEntity te = (ProximitySpawnerBlockEntity) context.level().getBlockEntity(coords.add(-1, 0, 0).toPos());
    	if (te == null) {
    		Treasure.LOGGER.debug("proximity spawner TE is null @ {}", coords.toShortString());
    		return coords;
    	}
    	EntityType<?> r = DungeonHooks.getRandomDungeonMob(context.random());
    	Treasure.LOGGER.debug("spawn mob entity -> {}", r);
    	if (r != null) {
	    	te.setMobName(EntityType.getKey(r));
	    	te.setMobNum(new DoubleRange(2, 4));
	    	te.setProximity(5D);
	    	Treasure.LOGGER.debug("placed proximity spawner @ {}", coords.add(-1,0,0).toShortString());
    	}
    	context.level().setBlock(coords.add(1, 0, 0).toPos(), TreasureBlocks.PROXIMITY_SPAWNER.get().defaultBlockState(), 3);
    	te = (ProximitySpawnerBlockEntity) context.level().getBlockEntity(coords.add(1, 0, 0).toPos());
    	if (te == null) {
    		Treasure.LOGGER.debug("proximity spawner TE is null @ {}", coords.toShortString());
    	}
    	r = DungeonHooks.getRandomDungeonMob(context.random());
    	Treasure.LOGGER.debug("spawn mob entity -> {}", r);
    	if (r != null) {
	    	te.setMobName(EntityType.getKey(r));
	    	te.setMobNum(new DoubleRange(2, 4));
	    	te.setProximity(5.5D);		// slightly larger proximity to fire first without entity collision
	    	Treasure.LOGGER.debug("placed proximity spawner @ {}", coords.add(1,0,0).toShortString());
    	}
		return coords;
	}
	
}