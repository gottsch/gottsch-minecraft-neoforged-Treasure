/*
 * This file is part of  Treasure2.
 * Copyright (c) 2021 Mark Gottschling (gottsch)
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

import mod.gottsch.neo.gottschcore.spatial.ICoords;
import mod.gottsch.neo.gottschcore.world.IWorldGenContext;
import mod.gottsch.forge.treasure2.core.generator.ChestGeneratorData;
import mod.gottsch.forge.treasure2.core.generator.GeneratorResult;
import mod.gottsch.forge.treasure2.core.generator.IGeneratorResult;

/**
 * 
 * @author Mark Gottschling 2021
 *
 * @param <RESULT>
 */
public interface IPitGenerator<RESULT extends IGeneratorResult<?>> {

	public Optional<GeneratorResult<ChestGeneratorData>> generate(IWorldGenContext context, ICoords surfaceCoords, ICoords spawnCoords);
	
	public boolean generateBase(IWorldGenContext context, ICoords surfaceCoords, ICoords spawnCoords);

	public boolean generatePit(IWorldGenContext context, ICoords surfaceCoords, ICoords spawnCoords);
	
	public boolean generateEntrance(IWorldGenContext context, ICoords surfaceCoords, ICoords spawnCoords);

	public int getOffsetY();
	
	public void setOffsetY(int i);

	int getMinSurfaceToSpawnDistance();

}