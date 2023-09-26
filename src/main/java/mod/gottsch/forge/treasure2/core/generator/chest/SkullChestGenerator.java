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
package mod.gottsch.forge.treasure2.core.generator.chest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import mod.gottsch.neo.gottschcore.enums.IRarity;
import mod.gottsch.neo.gottschcore.random.RandomHelper;
import mod.gottsch.forge.treasure2.Treasure;
import mod.gottsch.forge.treasure2.core.block.AbstractTreasureChestBlock;
import mod.gottsch.forge.treasure2.core.block.StandardChestBlock;
import mod.gottsch.forge.treasure2.core.block.TreasureBlocks;
import mod.gottsch.forge.treasure2.core.block.entity.ITreasureChestBlockEntity;
import mod.gottsch.forge.treasure2.core.enums.Rarity;
import mod.gottsch.forge.treasure2.core.item.LockItem;
import mod.gottsch.forge.treasure2.core.lock.LockLayout;
import mod.gottsch.forge.treasure2.core.registry.KeyLockRegistry;
import net.minecraft.util.RandomSource;

/**
 * 
 * @author Mark Gottschling on Dec 4, 2019
 *
 */
public class SkullChestGenerator extends AbstractChestGenerator {
	
	/**
	 * 
	 */
	public SkullChestGenerator() {
		super();
	}
	
	/**
	 * Always select a skull chest.
	 */
	@Override
	public StandardChestBlock  selectChest(final RandomSource random, final IRarity rarity) {
		StandardChestBlock chest = (StandardChestBlock) TreasureBlocks.SKULL_CHEST.get();
		return chest;
	}
	
	/**
	 * will have at least one lock.
	 */
	@Override
	public int randomizedNumberOfLocksByChestType(RandomSource random, LockLayout type) {
		// determine the number of locks to add
		int numLocks = RandomHelper.randomInt(random, 1, type.getMaxLocks());		
		Treasure.LOGGER.debug("# of locks to use: {})", numLocks);
		
		return numLocks;
	}
	
	/**
	 * Select Locks
	 * @param chest
	 */
	@Override
	public void addLocks(RandomSource random, AbstractTreasureChestBlock chest, ITreasureChestBlockEntity chestBlockEntity, IRarity rarity) {
		// select a rarity locks
		List<LockItem> locks = new ArrayList<>();
		locks.addAll(KeyLockRegistry.getLocks(Rarity.UNCOMMON).stream().map(l -> l.get()).collect(Collectors.toList()));
		locks.addAll(KeyLockRegistry.getLocks(Rarity.SCARCE).stream().map(l -> l.get()).collect(Collectors.toList()));

		addLocks(random, chest, chestBlockEntity, locks);
		locks.clear();
	}
}
