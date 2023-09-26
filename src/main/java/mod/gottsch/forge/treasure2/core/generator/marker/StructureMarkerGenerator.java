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
package mod.gottsch.forge.treasure2.core.generator.marker;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import mod.gottsch.neo.gottschcore.size.DoubleRange;
import mod.gottsch.neo.gottschcore.spatial.Coords;
import mod.gottsch.neo.gottschcore.spatial.ICoords;
import mod.gottsch.neo.gottschcore.world.IWorldGenContext;
import mod.gottsch.neo.gottschcore.world.WorldInfo;
import mod.gottsch.neo.gottschcore.world.gen.structure.BlockInfoContext;
import mod.gottsch.neo.gottschcore.world.gen.structure.GottschTemplate;
import mod.gottsch.neo.gottschcore.world.gen.structure.PlacementSettings;
import mod.gottsch.neo.gottschcore.world.gen.structure.StructureMarkers;
import mod.gottsch.forge.treasure2.Treasure;
import mod.gottsch.forge.treasure2.core.block.TreasureBlocks;
import mod.gottsch.forge.treasure2.core.block.entity.TreasureProximitySpawnerBlockEntity;
import mod.gottsch.forge.treasure2.core.config.Config;
import mod.gottsch.forge.treasure2.core.config.StructureConfiguration.StructMeta;
import mod.gottsch.forge.treasure2.core.generator.GeneratorData;
import mod.gottsch.forge.treasure2.core.generator.GeneratorResult;
import mod.gottsch.forge.treasure2.core.generator.GeneratorUtil;
import mod.gottsch.forge.treasure2.core.generator.TemplateGeneratorData;
import mod.gottsch.forge.treasure2.core.generator.template.ITemplateGenerator;
import mod.gottsch.forge.treasure2.core.generator.template.TemplateGenerator;
import mod.gottsch.forge.treasure2.core.registry.TreasureTemplateRegistry;
import mod.gottsch.forge.treasure2.core.structure.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraftforge.common.DungeonHooks;

/**
 * @author Mark Gottschling on Jan 28, 2019
 *
 */
public class StructureMarkerGenerator implements IMarkerGenerator<GeneratorResult<GeneratorData>> {

	/**
	 * 
	 */
	public StructureMarkerGenerator() {
	}

	@Override
	public Optional<GeneratorResult<GeneratorData>> generate(IWorldGenContext context, ICoords coords) {
		GeneratorResult<GeneratorData> result = new GeneratorResult<>(GeneratorData.class);

		// get the template
		Optional<TemplateHolder> optionalHolder = selectTemplate(context, coords, StructureCategory.TERRANEAN, StructureType.MARKER);
		if (optionalHolder.isEmpty()) {
			return Optional.empty();	
		}
		TemplateHolder holder = optionalHolder.get();

		GottschTemplate template = (GottschTemplate) holder.getTemplate();
		Treasure.LOGGER.debug("selected template holder -> {}", holder.getLocation());
		if (template == null) {
			Treasure.LOGGER.debug("could not find random template");
			return Optional.empty();
		}
		
		// TODO could move offset to TemplateGenerator : getOffset() which checks both the offsetblock and the meta
		// get the offset
		int offset = 0;
//		ICoords offsetCoords = ((GottschTemplate2)holder.getTemplate()).findCoords(random, TreasureTemplateRegistry.getMarkerBlock(StructureMarkers.OFFSET));
//		ICoords offsetCoords = TreasureTemplateRegistry.getOffsetFrom(context.random(), template.get(), StructureMarkers.OFFSET);
//		if (offsetCoords != null) {
//			offset = -offsetCoords.getY();
//		}
//		ICoords offsetCoords = Config.structConfigMetaMap.get(holder.getLocation()).getOffset().asCoords();
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
		
		// find entrance
//		ICoords entranceCoords =((GottschTemplate2)holder. getTemplate()).findCoords(random, TreasureTemplateRegistry.getMarkerBlock(StructureMarkers.ENTRANCE));
		ICoords entranceCoords =TreasureTemplateRegistry.getOffsetFrom(context.random(), template, StructureMarkers.ENTRANCE);
		if (entranceCoords == null) {
			Treasure.LOGGER.debug("Unable to locate entrance position.");
			return Optional.empty();
		}

		// select a rotation
		Rotation rotation = Rotation.values()[context.random().nextInt(Rotation.values().length)];
		Treasure.LOGGER.debug("above ground rotation used -> {}", rotation);
				
		// setup placement
		PlacementSettings placement = new PlacementSettings();
		placement.setRotation(rotation).setRandom(context.random());
		
		// TODO move into TemplateGenerator
		// NOTE these values are still relative to origin (spawnCoords);
		ICoords newEntrance = new Coords(GottschTemplate.transformedVec3d(placement, entranceCoords.toVec3()));
		
		/*
		 *  adjust spawn coords to line up room entrance with pit
		 */
		BlockPos transformedSize = template.getSize(rotation);
		ICoords spawnCoords = ITemplateGenerator.alignEntranceToCoords(/*spawnCoords*/coords, newEntrance, transformedSize, placement);
				
		// if offset is 2 or less, then determine if the solid ground percentage is valid
		if (offset >= -2) {
			if (!WorldInfo.isSolidBase(context.level(), spawnCoords, transformedSize.getX(), transformedSize.getZ(), 70)) {
				Treasure.LOGGER.debug("Coords -> [{}] does not meet {}% solid base requirements for size -> {} x {}", spawnCoords.toShortString(), 70, transformedSize.getX(), transformedSize.getY());
				 Optional<GeneratorResult<GeneratorData>> genResult = new GravestoneMarkerGenerator().generate(context, coords);
				 return genResult;
			}
		}

		// generate the structure
		GeneratorResult<TemplateGeneratorData> genResult = new TemplateGenerator().generate(context, template, placement, spawnCoords, offsetCoords);
		if (!genResult.isSuccess()) {
			return Optional.empty();
		}

		// interrogate info for spawners and any other special block processing (except chests that are handler by caller
		List<BlockInfoContext> spawnerContexts =
				(List<BlockInfoContext>) genResult.getData().getMap().get(GeneratorUtil.getMarkerBlock(StructureMarkers.SPAWNER));
		List<BlockInfoContext> proximityContexts =
				(List<BlockInfoContext>) genResult.getData().getMap().get(GeneratorUtil.getMarkerBlock(StructureMarkers.PROXIMITY_SPAWNER));
		
		// TODO exact same as SubmergedRuinGenerator... need to put them in an abstract/interface common to all structure generators
		// populate vanilla spawners
		for (BlockInfoContext c : spawnerContexts) {
			ICoords c2 = spawnCoords.add(c.getCoords());
			context.level().setBlock(c2.toPos(), Blocks.SPAWNER.defaultBlockState(), 3);
			SpawnerBlockEntity te = (SpawnerBlockEntity) context.level().getBlockEntity(c2.toPos());
			EntityType<?> r = DungeonHooks.getRandomDungeonMob(context.random());
			te.getSpawner().setEntityId(r, context.level().getLevel(), context.random(), c.getCoords().toPos());
		}
		
		// populate proximity spawners
		for (BlockInfoContext c : proximityContexts) {
			ICoords c2 = spawnCoords.add(c.getCoords());
	    	context.level().setBlock(c2.toPos(), TreasureBlocks.PROXIMITY_SPAWNER.get().defaultBlockState(), 3);
			// TODO if can not set, the BE should have a default to execute this code if not set on creation.
			try {
				TreasureProximitySpawnerBlockEntity te = (TreasureProximitySpawnerBlockEntity) context.level().getBlockEntity(c2.toPos());
				EntityType<?> r = DungeonHooks.getRandomDungeonMob(context.random());

				te.setMobName(EntityType.getKey(r));
				te.setMobNum(new DoubleRange(1, 2));
				te.setProximity(10D);
			} catch (Exception e) {
				Treasure.LOGGER.error("can't access the proximity spawner ->", e);
			}
//		    Treasure.LOGGER.debug("Creating proximity spawner @ {} -> [mobName={}, spawnRange={}", c.getCoords().toShortString(), r, te.getProximity());
		}		

		result.setData(genResult.getData());
		return Optional.of(result);
	}
	
	/**
	 * TODO this method is used in other structure generators... add the structurecategory and structureType to the params and abstract it out
	 * @param random
	 * @return
	 */
	@Deprecated
	private Optional<GottschTemplate> getRandomTemplate(Random random) {
		Optional<GottschTemplate> result = Optional.empty();
		
		List<TemplateHolder> holders = TreasureTemplateRegistry.getTemplate(StructureCategory.TERRANEAN, StructureType.MARKER);
	
		if (holders != null && !holders.isEmpty()) {
			TemplateHolder holder = holders.get(random.nextInt(holders.size()));
			GottschTemplate template = (GottschTemplate) holder.getTemplate();
			if (template == null) {
				Treasure.LOGGER.debug("could not find template");
				return Optional.empty();
			}
			Treasure.LOGGER.debug("selected template holder.location -> {}, tags -> {}", holder.getLocation(), holder.getTags());
			result = Optional.of(template);
		}
		return result;
	}
}