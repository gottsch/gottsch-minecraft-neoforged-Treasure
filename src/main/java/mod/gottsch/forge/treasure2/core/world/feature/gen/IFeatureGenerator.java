/*
 * This file is part of  Treasure2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.treasure2.core.world.feature.gen;

import java.util.Optional;

import mod.gottsch.neo.gottschcore.enums.IRarity;
import mod.gottsch.neo.gottschcore.spatial.ICoords;
import mod.gottsch.neo.gottschcore.world.IWorldGenContext;
import mod.gottsch.neo.gottschcore.world.WorldGenContext;
import mod.gottsch.forge.treasure2.core.config.ChestFeaturesConfiguration.ChestRarity;
import mod.gottsch.forge.treasure2.core.generator.ChestGeneratorData;
import mod.gottsch.forge.treasure2.core.generator.GeneratorResult;
import mod.gottsch.forge.treasure2.core.generator.IGeneratorResult;
import mod.gottsch.forge.treasure2.core.generator.ruin.IRuinGenerator;
import mod.gottsch.forge.treasure2.core.world.feature.IFeatureGenContext;
import net.minecraft.resources.ResourceLocation;

/**
 * 
 * @author Mark Gottschling May 12, 2023
 *
 */
public interface IFeatureGenerator {
	public ResourceLocation getName();
	
	// TODO make generic return result
	public Optional<GeneratorResult<ChestGeneratorData>> generate(IFeatureGenContext featureGenContext, ICoords spawnCoords, IRarity rarity, ChestRarity chestRarity);

}
