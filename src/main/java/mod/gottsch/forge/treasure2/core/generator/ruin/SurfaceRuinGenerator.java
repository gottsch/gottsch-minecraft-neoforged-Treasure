/*
 * This file is part of  Treasure2.
 * Copyright (c) 2019 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.treasure2.core.generator.ruin;

import java.util.List;
import java.util.Optional;

import mod.gottsch.neo.gottschcore.block.BlockContext;
import mod.gottsch.neo.gottschcore.size.Quantity;
import mod.gottsch.neo.gottschcore.spatial.Coords;
import mod.gottsch.neo.gottschcore.spatial.ICoords;
import mod.gottsch.neo.gottschcore.world.IWorldGenContext;
import mod.gottsch.neo.gottschcore.world.WorldInfo;
import mod.gottsch.neo.gottschcore.world.gen.structure.BlockInfoContext;
import mod.gottsch.neo.gottschcore.world.gen.structure.GottschTemplate;
import mod.gottsch.neo.gottschcore.world.gen.structure.PlacementSettings;
import mod.gottsch.neo.gottschcore.world.gen.structure.StructureMarkers;
import mod.gottsch.forge.treasure2.Treasure;
import mod.gottsch.forge.treasure2.core.config.Config;
import mod.gottsch.forge.treasure2.core.config.StructureConfiguration.StructMeta;
import mod.gottsch.forge.treasure2.core.generator.ChestGeneratorData;
import mod.gottsch.forge.treasure2.core.generator.GeneratorResult;
import mod.gottsch.forge.treasure2.core.generator.GeneratorUtil;
import mod.gottsch.forge.treasure2.core.generator.TemplateGeneratorData;
import mod.gottsch.forge.treasure2.core.generator.template.TemplateGenerator;
import mod.gottsch.forge.treasure2.core.registry.TreasureTemplateRegistry;
import mod.gottsch.forge.treasure2.core.structure.StructureCategory;
import mod.gottsch.forge.treasure2.core.structure.StructureType;
import mod.gottsch.forge.treasure2.core.structure.TemplateHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * 
 * @author Mark Gottschling on Dec 13, 2019
 *
 */
public class SurfaceRuinGenerator implements IRuinGenerator<GeneratorResult<ChestGeneratorData>> {
	
	private static final double REQUIRED_BASE_SIZE = 75;
	private static final double REQUIRED_AIR_SIZE = 50;

	/**
	 * 
	 */
	public SurfaceRuinGenerator() {}
	
	@Override
	public Optional<GeneratorResult<ChestGeneratorData>> generate(IWorldGenContext context, ICoords originalSpawnCoords) {
		return generate(context, originalSpawnCoords, null);
	}
	
	@Override
	public Optional<GeneratorResult<ChestGeneratorData>> generate(IWorldGenContext context, ICoords originalSpawnCoords, TemplateHolder holder) {
	
		GeneratorResult<ChestGeneratorData> result = new GeneratorResult<>(ChestGeneratorData.class);

		/*
		 * Setup
		 */
		// create the generator
		TemplateGenerator generator = new TemplateGenerator();
		
		// get the template
		if (holder == null) {
			Optional<TemplateHolder> optionalHolder = selectTemplate(context, originalSpawnCoords, StructureCategory.TERRANEAN, StructureType.RUIN);
			if (optionalHolder.isPresent()) {
				holder = optionalHolder.get();
			}
		}
		if (holder == null) {
			return Optional.empty();	
		}

		GottschTemplate template = (GottschTemplate) holder.getTemplate();
		Treasure.LOGGER.debug("selected template holder -> {}", holder.getLocation());
		if (template == null) {
			Treasure.LOGGER.debug("could not find random template");
			return Optional.empty();
		}
		Treasure.LOGGER.debug("original spawn coords -> {}", originalSpawnCoords.toShortString());

		// find the 'entrance' block
		ICoords entranceCoords = TreasureTemplateRegistry.getOffsetFrom(context.random(), template, StructureMarkers.ENTRANCE);
		if (entranceCoords == null) {
			Treasure.LOGGER.debug("Unable to locate entrance position.");
			return Optional.empty();
		}
		Treasure.LOGGER.debug("entrance coords -> {}", entranceCoords.toShortString());
		
		// select a random rotation
//		Rotation rotation = Rotation.values()[context.random().nextInt(Rotation.values().length)];
//		Rotation rotation = Rotation.CLOCKWISE_90;
		Rotation rotation = Rotation.NONE;
		Treasure.LOGGER.debug("rotation used -> {}", rotation);
		
		// setup placement
		PlacementSettings placement = new PlacementSettings();
		placement.setRotation(rotation).setRandom(context.random());
	
		// NOTE these values are still relative to origin (spawnCoords);
		ICoords newEntrance = new Coords(GottschTemplate.transformedVec3d(placement, entranceCoords.toVec3()));
		Treasure.LOGGER.debug("new entrance coords -> {}", newEntrance.toShortString());
	
		// determine the actual spawn coords
		ICoords templateSize = new Coords(holder.getTemplate().getSize(placement.getRotation()));
		Treasure.LOGGER.debug("template size / footprint -> {}", templateSize.toShortString());
		Treasure.LOGGER.debug("original coords -> {}", originalSpawnCoords.toShortString());

		/*
		 * we want to align the new entrance (rotated structure) to the center of the chunk ie. original spawn
		 */
		BlockPos transformedSize = template.getSize(rotation);
		Treasure.LOGGER.debug("transformed size / footprint -> {}", transformedSize.toShortString());

		///////////////
		// TODO move to method
		// calculate the 'end' block of the template ie coords + size


		// TODO what is this doing? it is wrong, getting the end coords is simply adding the transformed size
		// to the transformed spawn coords
		// TODO this is acutally kinda right, just named poorly. it is attempting to determine the new
		// spawn coords after the structure has been rotated.

		// NOTE a non-rotated spawn coords is x -1 from the actual block placement.
		// ie if spawn coords = (0, 0, 0), then the first block is generated at (1, 0, 0).
		// so when trying to discover the actual footprint, need to x+1 to the spawn coords.
		ICoords newSpawnCoords = switch(rotation) {
			case CLOCKWISE_90 -> originalSpawnCoords.add(-(transformedSize.getX()-1), 0, 0);
			case CLOCKWISE_180 -> originalSpawnCoords.add(-(transformedSize.getX()-1), 0, -(transformedSize.getZ()-1));
			case COUNTERCLOCKWISE_90 -> originalSpawnCoords.add(0, 0, -(transformedSize.getZ()-1));
			default -> originalSpawnCoords;
		};
		Treasure.LOGGER.debug("new rotated spawn coords -> {}", newSpawnCoords.toShortString());

		///////////////

		// TODO now align middle of rotated footprint to middle of chunk

//				ICoords alignedSpawnCoords = align(originalSpawnCoords, newEntrance, transformedSize, placement);
		ICoords alignedSpawnCoords = originalSpawnCoords;
		Treasure.LOGGER.debug("aligned spawn coords -> {}", alignedSpawnCoords.toShortString());

		// TODO want to center the template in the middle of the chunk - NOT the entrance
		
		// NOTE these checks don't really belong in a generator as their task is to just generate.
		// however, the template is unknown outside this call and thus the rotate, placement, size and actual coords would be unknown.
		/**
		 * Environment Checks
		 */
		alignedSpawnCoords = WorldInfo.getDryLandSurfaceCoords(context.level(), context.chunkGenerator(), alignedSpawnCoords);

		Treasure.LOGGER.debug("surface coords -> {}", alignedSpawnCoords.toShortString());
		if (alignedSpawnCoords == Coords.EMPTY) {
			return Optional.empty();
		}
				
		// check if it has % land base using the newSpawnCoords
		for (int i = 0; i < 3; i++) {
			Treasure.LOGGER.debug("finding solid base index -> {} at coords -> {}", i, newSpawnCoords.toShortString());
			if (!WorldInfo.isSolidBase(context.level(), newSpawnCoords, templateSize.getX(), templateSize.getZ(), REQUIRED_BASE_SIZE)) {
				if (i == 2) {
					Treasure.LOGGER.debug("Coords -> [{}] does not meet {}% solid base requirements for size -> {} x {}",newSpawnCoords.toShortString(), REQUIRED_BASE_SIZE, templateSize.getX(), templateSize.getZ());
					return Optional.empty();
				}
				else {
					newSpawnCoords = newSpawnCoords.add(0, -1, 0);
					alignedSpawnCoords.add(0, -1, 0);
					Treasure.LOGGER.debug("move aligned spawn coords down for solid base check -> {}", alignedSpawnCoords.toShortString());
				}
			}
			else {
				break;
			}
		}
		
		Treasure.LOGGER.debug("using solid base coords -> {}", alignedSpawnCoords.toShortString());
		
		// check if the plane above the actual spawn coords is % air
		Treasure.LOGGER.debug("checking for {} % air at coords -> {} for dimensions -> {} x {}", REQUIRED_AIR_SIZE, alignedSpawnCoords.add(0, 1, 0), templateSize.getX(), templateSize.getZ());
		if (!WorldInfo.isAirBase(context.level(), alignedSpawnCoords.add(0, 1, 0), templateSize.getX(), templateSize.getZ(), REQUIRED_AIR_SIZE)) {
			Treasure.LOGGER.debug("Coords -> [{}] does not meet {} % air base requirements for size -> {} x {}", REQUIRED_AIR_SIZE, alignedSpawnCoords.toShortString(), templateSize.getX(), templateSize.getZ());
			return Optional.empty();
		}
		
		/**
		 * Build
		 */
		Optional<StructMeta> meta = Config.getStructMeta(holder.getLocation());
		ICoords offsetCoords = Coords.EMPTY;
		if (meta.isPresent()) {
			offsetCoords = meta.get().getOffset().asCoords();
		}
		else {
			// TEMP dump map
			Treasure.LOGGER.debug("dump struct meta map -> {}", Config.structConfigMetaMap);
			Treasure.LOGGER.debug("... was looking for -> {}", holder.getLocation());
		}
		
		// update original spawn coords' y-value to be that of aligned spawn coords.
		// this is the coords that need to be supplied to the template generator to allow
		// the structure to generator in the correct place
//		originalSpawnCoords = new Coords(originalSpawnCoords.getX(), alignedSpawnCoords.getY(), originalSpawnCoords.getZ());
		// NOTE this doesn't make sense to use the original spawn coords + aligned-Y --> that doesn't move the structure at all.
		Treasure.LOGGER.debug("using spawn coords to generate -> {}", alignedSpawnCoords); //originalSpawnCoords
		
		GeneratorResult<TemplateGeneratorData> genResult = generator.generate(context, template, placement, /*originalSpawnCoords*/alignedSpawnCoords, offsetCoords);
		 if (!genResult.isSuccess()) {
			 return Optional.empty();
		 }
		Treasure.LOGGER.debug("surface gen result -> {}", genResult);
		
		///////////// TESTING ////////////////////////
		// TODO move to method
		// TODO add a y-index loop with a max # of iterations
		// use the newSpawnCoords and fill in empty spaces below with dirt
		for (int x = 0; x < transformedSize.getX() + 1; x++) { // TODO the inexplicable +1 offset
			for (int z = 0; z < transformedSize.getZ(); z++) {
				ICoords c = newSpawnCoords.add(x, -1, z);
				if (context.level().getBlockState(c.toPos()).isAir()) {
					context.level().setBlock(c.toPos(), Blocks.DIRT.defaultBlockState(), 3);
				}
			}
		}

		// interrogate info for spawners and any other special block processing (except chests that are handler by caller
		List<BlockInfoContext> bossChestContexts =
					(List<BlockInfoContext>) genResult.getData().getMap().get(GeneratorUtil.getMarkerBlock(StructureMarkers.BOSS_CHEST));
		List<BlockInfoContext> chestContexts =
				(List<BlockInfoContext>) genResult.getData().getMap().get(GeneratorUtil.getMarkerBlock(StructureMarkers.CHEST));
		List<BlockInfoContext> spawnerContexts =
				(List<BlockInfoContext>) genResult.getData().getMap().get(GeneratorUtil.getMarkerBlock(StructureMarkers.SPAWNER));
		List<BlockInfoContext> proximityContexts =
				(List<BlockInfoContext>) genResult.getData().getMap().get(GeneratorUtil.getMarkerBlock(StructureMarkers.PROXIMITY_SPAWNER));

		/*
		 *  NOTE currently only 1 chest is allowed per structure - the rest are ignored.
		 */
		// check if there is a boss chest(s)
		// TODO turn these checks into methods --> getChestContext();
		BlockInfoContext chestContext = null;
		if (bossChestContexts != null && bossChestContexts.size() > 0) {
			if (bossChestContexts.size() > 1) {
				chestContext = bossChestContexts.get(context.random().nextInt(bossChestContexts.size()));
			}
			else {
				chestContext = bossChestContexts.get(0);
			}			
		}		

		// if a boss chest wasn't found, search for regular chests
		if (chestContext == null) {
			if (chestContexts != null && chestContexts.size() > 0) {
				if (chestContexts.size() > 1) {
					chestContext = chestContexts.get(context.random().nextInt(chestContexts.size()));
				}
				else {
					chestContext = chestContexts.get(0);
				}			
			}			
		}

		ICoords chestCoords = null;
		if (chestContext != null) {
			Treasure.LOGGER.debug("chest context coords -> {}", chestContext.getCoords());
			// move the chest coords to the first solid block beneath it.
			// NOTE can't use this method as it will disregard the entire structure as the chunkGenerator only looks at the land mass.
			// TODO need to use the old non-chunk generator version of doing this. ie stepping down a block and check.
			// NOTE however, since there isn't a decay processor, there is no need to perform this operation.
//			chestCoords = WorldInfo.getDryLandSurfaceCoords(context.level(), context.chunkGenerator(), chestContext.getCoords());
			chestCoords = chestContext.getCoords();
			if (chestCoords == Coords.EMPTY) {
				chestCoords = null;
			}
			chestContext.setCoords(chestCoords);
		}
		if (chestCoords == null) {
			return Optional.empty();
		}
		
		if (proximityContexts != null)
			Treasure.LOGGER.debug("Proximity spawners size -> {}", proximityContexts.size());
		else
			Treasure.LOGGER.debug("No proximity spawners found.");
		
		// populate vanilla spawners
		buildVanillaSpawners(context, spawnerContexts);
		
		// populate proximity spawners
		buildOneTimeSpawners(context, proximityContexts, new Quantity(1,2), 5D);
		
		// copy all data from genResult
		result.getData().setSpawnCoords(genResult.getData().getSpawnCoords());
		
		// update with chest context
		result.getData().setCoords(chestContext.getCoords());
		result.getData().setState(chestContext.getState());
		
		return Optional.of(result);
	}
	
	/**
	 * TODO replace call to this with one in GottschCore. it not exist then use the chunkGenerator
	 * NOTE candidate for GottschCore
	 * @param serverLevelAccessor
	 * @param coords
	 * @return
	 */
	public static ICoords getSolidSurfaceCoords(final ServerLevelAccessor serverLevelAccessor, final ICoords coords) {
		boolean isSurfaceBlock = false;
		ICoords newCoords = coords;
		
		while (!isSurfaceBlock) {
			// get the cube that is 1 below current position
			BlockContext blockContext = new BlockContext(serverLevelAccessor.getLevel(), newCoords.down(1));
//			Treasure.LOGGER.debug("below block -> {} @ {}", cube.getState().getBlock().getRegistryName(), cube.getCoords().toShortString());
			// exit if not valid Y coordinate
			if (!WorldInfo.isHeightValid(blockContext.getCoords())) {
				return Coords.EMPTY;
			}	

			if (blockContext.isAir() || blockContext.isReplaceable()
					|| blockContext.isLeaves() || blockContext.isFluid()
					|| blockContext.isBurning()) {
//				Treasure.LOGGER.debug("block is air, leaves, replacable, liquid or burning");
				newCoords = newCoords.down(1);
			}
			else {
				isSurfaceBlock = true;
			}		
		}
		return newCoords;
	}
	
	/**
	 * TODO move to abstract or interface
	 * @param spawnCoords
	 * @param newEntrance
	 * @param transformedSize
	 * @param placement
	 * @return
	 */
	private ICoords align(ICoords spawnCoords, ICoords newEntrance, BlockPos transformedSize, PlacementSettings placement) {
		ICoords startCoords = null;
		// NOTE work with rotations only for now

		// TODO this only works with NO_ROTATION... need to add all other rotations
		// first offset spawnCoords by newEntrance
		startCoords = spawnCoords.add(-newEntrance.getX(), 0, -newEntrance.getZ());
		
		return startCoords;
	}
}