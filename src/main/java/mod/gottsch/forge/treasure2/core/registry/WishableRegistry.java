/*
 * This file is part of  Treasure2.
 * Copyright (c) 2022 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.treasure2.core.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import mod.gottsch.neo.gottschcore.enums.IRarity;
import mod.gottsch.forge.treasure2.core.util.ModUtil;
import mod.gottsch.forge.treasure2.core.wishable.IWishableHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 
 * @author Mark Gottschling on Nov 25, 2022
 *
 */
public class WishableRegistry {
	/*
	 * a Table registry for rarity/key lookups for wishable items.
	 * NOTE registering Items - not RegistryObject<Item>, and therefor needs to happen after register items event.
	 */
	private static final Multimap<IRarity, Item> BY_RARITY;
	private static final Map<ResourceLocation, Item> BY_NAME;
	private static final Map<ResourceLocation, IRarity> RARITY_BY_NAME;

	private static final Map<Item, IWishableHandler> HANDLER_MAP;
	
	static {
		BY_RARITY = ArrayListMultimap.create();
		BY_NAME = Maps.newHashMap();
		RARITY_BY_NAME = Maps.newHashMap();
		HANDLER_MAP = Maps.newHashMap();
	}
	
	public static void registerHandler(Item item, IWishableHandler handler) {
		HANDLER_MAP.put(item, handler);
	}
	
	public static Optional<IWishableHandler> getHandler(Item item) {
		if (HANDLER_MAP.containsKey(item)) {
			return Optional.of(HANDLER_MAP.get(item));
		}
		return Optional.empty();
	}
	
	public static void register(Item item) {	
		ResourceLocation name = ModUtil.getName(item);
		if (!BY_NAME.containsKey(name)) {
			BY_NAME.put(name, item);
		}
	}
	
	public static void registerByRarity(IRarity rarity, Item item) {
		ResourceLocation name = ModUtil.getName(item);
		BY_RARITY.put(rarity, item);
		RARITY_BY_NAME.put(name, rarity);
	}
	
	public static List<Item> getAll() {
		return new ArrayList<>(BY_NAME.values());
	}
	
	public static void clearByRarity() {
		BY_RARITY.clear();		
		RARITY_BY_NAME.clear();
	}
	
	public static Optional<IRarity> getRarity(ResourceLocation name) {
		if (RARITY_BY_NAME.containsKey(name)) {
			return Optional.of(RARITY_BY_NAME.get(name));
		}
		return Optional.empty();
	}
	
	public static Optional<IRarity> getRarity(Item wishable) {
		ResourceLocation name = ModUtil.getName(wishable);
		return getRarity(name);
	}

	public static boolean isRegistered(Item item) {
		ResourceLocation name = ModUtil.getName(item);
		return isRegistered(name);	
	}
	
	public static boolean isRegistered(ResourceLocation name) {
		return BY_NAME.containsKey(name);	
	}
}
