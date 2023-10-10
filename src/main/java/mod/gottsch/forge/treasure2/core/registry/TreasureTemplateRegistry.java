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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import mod.gottsch.neo.gottschcore.loot.LootTableShell;
import mod.gottsch.neo.gottschcore.spatial.ICoords;
import mod.gottsch.neo.gottschcore.world.gen.structure.GottschTemplate;
import mod.gottsch.neo.gottschcore.world.gen.structure.StructureMarkers;
import mod.gottsch.forge.treasure2.Treasure;
import mod.gottsch.forge.treasure2.api.TreasureApi;
import mod.gottsch.forge.treasure2.core.config.Config;
import mod.gottsch.forge.treasure2.core.config.StructureConfiguration.StructMeta;
import mod.gottsch.forge.treasure2.core.structure.IStructureCategory;
import mod.gottsch.forge.treasure2.core.structure.IStructureType;
import mod.gottsch.forge.treasure2.core.structure.StructureCategory;
import mod.gottsch.forge.treasure2.core.structure.StructureType;
import mod.gottsch.forge.treasure2.core.structure.TemplateHolder;
import mod.gottsch.forge.treasure2.core.util.ModUtil;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;


/**
 * @author Mark Gottschling on Jan 10, 2021
 *
 */
public class TreasureTemplateRegistry {
	public static final String JAR_TEMPLATES_ROOT = "data/treasure2/structures/";
	public static final String DATAPACKS_TEMPLATES_ROOT = "data/treasure2/structures/";

	private static final Set<String> REGISTERED_MODS;
	private static final Map<String, Boolean> LOADED_MODS;
	protected static final Gson GSON_INSTANCE;

	/*
	MC 1.20.1: net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.blockLookup
	Name: k => f_243724_ => blockLookup
	Side: BOTH
	AT: public net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager f_243724_ # blockLookup
	Type: net/minecraft/core/HolderGetter
    */
	private static final String HOLDER_GETTER_SRG_NAME = "f_243724_";

	private static HolderGetter<Block> blockLookup;
	
	/*
	 * All structure templates by resource location.
	 */
	private final static Map<ResourceLocation, TemplateHolder> MAP = new HashMap<>();
	private final static Map<ResourceLocation, TemplateHolder> DATAPACK_MAP = new HashMap<>();

	/*
	 * All structure templates by category and type.
	 */
	private final static Table<IStructureCategory, IStructureType, List<TemplateHolder>> TABLE = HashBasedTable.create();
	private final static Table<IStructureCategory, IStructureType, List<TemplateHolder>> DATAPACK_TABLE = HashBasedTable.create();


	public static class AccessKey {
		public IStructureCategory category;
		public IStructureType type;
		public AccessKey(IStructureCategory category, IStructureType type) {
			this.category = category;
			this.type = type;
		}
	}

	/*
	 * Whitelist Guava Table by Type, Biome (resource location) -> TemplateHolder list.
	 */
	private final static Table<AccessKey, ResourceLocation, List<TemplateHolder>> WHITELIST_TABLE = HashBasedTable.create();
	private final static Table<AccessKey, ResourceLocation, List<TemplateHolder>> BLACKLIST_TABLE = HashBasedTable.create();

	/*
	 * standard list of marker blocks to scan for 
	 */
	private static List<Block> markerScanList;

	/*
	 * 
	 */
	private static Map<StructureMarkers, Block> markerMap;

	/*
	 * standard list of replacements blocks.
	 * NOTE needs to be <IBlockState, IBlockState> (for v1.12.x anyway)
	 */
	private static Map<BlockState, BlockState> replacementMap;

	/*
	 * use this map when structures are submerged instead of the default marker map
	 */
	private static Map<StructureMarkers, Block> waterMarkerMap;

	private static ServerLevel world;

	/*
	 * the path to the world save folder
	 */
	private static Path worldSaveFolder;

	static {
		REGISTERED_MODS = Sets.newHashSet();
		LOADED_MODS = Maps.newHashMap();

		GSON_INSTANCE = new GsonBuilder().create();

		// initialize table
		for (IStructureCategory category : StructureCategory.values()) {
			for (IStructureType type : StructureType.values()) {
				TABLE.put(category, type, new ArrayList<>(5));
				DATAPACK_TABLE.put(category, type, new ArrayList<>(3));
			}
		}

		// setup standard list of markers
		markerMap = Maps.newHashMapWithExpectedSize(10);
		markerMap.put(StructureMarkers.CHEST, Blocks.CHEST);
		markerMap.put(StructureMarkers.BOSS_CHEST, Blocks.ENDER_CHEST);
		markerMap.put(StructureMarkers.SPAWNER, Blocks.SPAWNER);
		markerMap.put(StructureMarkers.ENTRANCE, Blocks.GOLD_BLOCK);
		markerMap.put(StructureMarkers.OFFSET, Blocks.REDSTONE_BLOCK);
		markerMap.put(StructureMarkers.PROXIMITY_SPAWNER, Blocks.IRON_BLOCK);
		markerMap.put(StructureMarkers.NULL, Blocks.BEDROCK);

		// default marker scan list
		markerScanList = Arrays.asList(new Block[] {
				markerMap.get(StructureMarkers.CHEST),
				markerMap.get(StructureMarkers.BOSS_CHEST),
				markerMap.get(StructureMarkers.SPAWNER),
				markerMap.get(StructureMarkers.ENTRANCE),
				markerMap.get(StructureMarkers.OFFSET),
				markerMap.get(StructureMarkers.PROXIMITY_SPAWNER)
		});

		// init water marker map
		// setup standard list of markers
		waterMarkerMap = Maps.newHashMap(getMarkerMap());
		waterMarkerMap.put(StructureMarkers.NULL, Blocks.AIR);// <-- this is the difference between default

        replacementMap = Maps.newHashMap();
        replacementMap.put(Blocks.WHITE_WOOL.defaultBlockState(), Blocks.AIR.defaultBlockState());
        replacementMap.put(Blocks.BLACK_STAINED_GLASS.defaultBlockState(), Blocks.SPAWNER.defaultBlockState());
	}

	/**
	 * 
	 */
	private TreasureTemplateRegistry() {	}

	/**
	 * 
	 */
	public void clear() {
		TABLE.clear();
		DATAPACK_TABLE.clear();
		MAP.clear();
		DATAPACK_MAP.clear();
		
	}

	/**
	 * 
	 */
	public static void clearDatapacks() {
		DATAPACK_MAP.clear();
		DATAPACK_TABLE.clear();
	}
	
	/**
	 * 
	 */
	public static void clearAccesslists() {
		WHITELIST_TABLE.clear();
		BLACKLIST_TABLE.clear();
	}
	
	/**
	 * 
	 * @param modID
	 */
	public static void register(String modID) {
		REGISTERED_MODS.add(modID);

		Treasure.LOGGER.debug("reading templates...");
		Path jarPath = ModList.get().getModFileById(modID).getFile().getFilePath();
		Treasure.LOGGER.debug("jar path -> {}", jarPath);
		registerFromJar(jarPath);
	}

	/**
	 * 
	 * @param jarPath
	 */
	private static void registerFromJar(Path jarPath) {
		//		StructureType.getNames().forEach(category -> {
		List<Path> lootTablePaths;
		try {
			// get all the paths in folder
			lootTablePaths = ModUtil.getPathsFromResourceJAR(jarPath, JAR_TEMPLATES_ROOT);

			for (Path path : lootTablePaths) {
				// ensure each file ends with .nbt
				if (path.getFileName().toString().endsWith(".nbt")) {
					Treasure.LOGGER.debug("structure path -> {}", path);
					// load the shell from the jar
					Optional<GottschTemplate> template = loadFromJar(path, getMarkerScanList(), getReplacementMap()); // ie readTemplate
					// register
					if (template.isPresent()) {
						Treasure.LOGGER.debug("registering from jar -> {}", path);
						registerTemplate(path, template.get());
					}
				}
			}
		} catch (Exception e) {
			Treasure.LOGGER.error("error: " , e);
			e.printStackTrace();
		}
		//		});		
	}

	/**
	 * 
	 * @param path
	 * @param template
	 */
	private static void registerTemplate(Path path, GottschTemplate template) {
		// extract the category

		String categoryToken = path.getName(3).toString();
		Optional<IStructureCategory> category = TreasureApi.getStructureCategory(categoryToken);
		if (category.isEmpty()) { 
			return;
		}

		String typeToken = null;
		Optional<IStructureType> type = Optional.empty();
		if (path.getNameCount() > 4) {
			typeToken = path.getName(4).toString();
			type = TreasureApi.getStructureType(typeToken);
			if (type.isEmpty()) {
				return;
			}
		}

		// convert to resource location
		ResourceLocation resourceLocation = asResourceLocation(path);
		Treasure.LOGGER.debug("resource location -> {}", resourceLocation);

		// setup the template holder
		TemplateHolder holder = new TemplateHolder()
				//				.setMetaLocation(metaResourceLocation)
				.setLocation(resourceLocation)
				//				.setDecayRuleSetLocation(decayRuleSetResourceLocation)
				.setTemplate(template);				

		if (path.getNameCount() > 5) {
			// don't include the last element as that is the file name
			for (int i = 5; i < path.getNameCount()-1; i++) {
				holder.getTags().add(path.getName(i).toString());
			}
		}

		// add to map
		MAP.put(resourceLocation, holder);

		Treasure.LOGGER.debug("adding template to table with category -> {}, type -> {}", category.get(), type.get());
		// add to table
		if (!TABLE.contains(category.get(), type.get())) {
			TABLE.put(category.get(), type.get(), new ArrayList<>());
		}
		TABLE.get(category.get(), type.get()).add(holder);
		Treasure.LOGGER.debug("size of list for -> {}, {} -> {}", category.get(), type.get(), TABLE.get(category.get(), type.get()).size());
	}

	/**
	 * 
	 * @param path
	 * @return
	 */
	public static ResourceLocation asResourceLocation(Path path) {
		//		Treasure.LOGGER.debug("path in -> {}", path.toString());

		// extract the namespace (moot - should always be value of Treasure.MOD_ID)
		String namespace = path.getName(1).toString();
		// get everything after loot_tables/ as the name
		String name = path.toString().replace("\\", "/");
		if (name.startsWith("/")) {
			name = name.substring(1, name.length());
		}
		name = name.substring(name.indexOf("structures/") + 11).replace(".nbt", "");
		return new ResourceLocation(namespace, name);
	}

	/**
	 * reads a template from the minecraft jar
	 */
	public static Optional<GottschTemplate> loadFromJar(Path resourceFilePath, List<Block> markerBlocks, Map<BlockState, BlockState> replacementBlocks) {
		InputStream inputStream = null;

		try {
			Treasure.LOGGER.debug("attempting to load template from jar -> {}", resourceFilePath);
			inputStream = Treasure.instance.getClass().getClassLoader().getResourceAsStream(resourceFilePath.toString());
			return loadTemplateFromStream(inputStream, markerBlocks, replacementBlocks);

			// TODO change from Throwable
		} catch (Throwable e) {
			Treasure.LOGGER.error("error reading resource: ", e);
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
		return Optional.empty();
	}

	/**
	 * reads a template from an input stream
	 */
	private static Optional<GottschTemplate> loadTemplateFromStream(InputStream stream, List<Block> markerBlocks, 
			Map<BlockState, BlockState> replacementBlocks) throws IOException {

		CompoundTag nbt = NbtIo.readCompressed(stream);

//		if (!nbt.contains("DataVersion", 99)) {
//			nbt.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());//500);
//		}

		GottschTemplate template = new GottschTemplate();
		template.load(blockLookup, nbt, markerBlocks, replacementBlocks);
		//		Treasure.LOGGER.debug("adding template to map with key -> {}", id);
		//		this.getTemplates().put(id, template);
		return Optional.ofNullable(template);
	}

	/**
	 * 
	 * @param event
	 */
	public static void onWorldLoad(LevelEvent.Load event, Path worldSavePath) {
		Treasure.LOGGER.debug("TemplateRegistry.onWorldLoad...");
		register((ServerLevel)event.getLevel());
		setWorldSaveFolder(worldSavePath);
		clearDatapacks();
		clearAccesslists();
		if (!event.getLevel().isClientSide()) {
			TreasureApi.registerTemplates(Treasure.MODID);
			// regiser templates
			TreasureApi.registerTemplates(Treasure.MODID);
			Treasure.LOGGER.debug("template registry world load event...");
			loadDataPacks(getMarkerScanList(), getReplacementMap());
			registerAccesslists(Config.structureConfiguration.getStructMetas());
		}
	}

	@SuppressWarnings("unchecked")
	private static void register(ServerLevel level) {
		Treasure.LOGGER.debug("attempting to get HolderGetter from StructureTemplateManager...");
		Object obj = ObfuscationReflectionHelper.getPrivateValue(StructureTemplateManager.class, level.getServer().getStructureManager(), HOLDER_GETTER_SRG_NAME);
		if (obj instanceof HolderGetter) {
			Treasure.LOGGER.debug("obj -> {}", obj);
			blockLookup = ((HolderGetter<Block>) obj);
		} else {
			Treasure.LOGGER.debug("should be throwing a RuntimeException...");
			throw new RuntimeException("unable to attain Block HolderGetter");
		}
	}
	
	/**
	 * 
	 * @param zipPath
	 * @param resourceFilePath
	 * @return
	 */
	public static Optional<LootTableShell> loadFromZip(Path zipPath, Path resourceFilePath) {
		Optional<LootTableShell> resourceLootTable = Optional.empty();

		return resourceLootTable;
	}

	/**
	 * 
	 * @param zipFile
	 * @param resourceFilePath
	 * @return
	 */
	public static Optional<LootTableShell> loadFromZip(ZipFile zipFile, Path resourceFilePath) {
		Optional<LootTableShell> resourceLootTable = Optional.empty();

		return resourceLootTable;
	}

	/**
	 * Only load once - not per  registered mod.
	 * @param markerBlocks
	 * @param replacementBlocks
	 */
	public static void loadDataPacks(List<Block> markerBlocks, Map<BlockState, BlockState> replacementBlocks) {
		String worldSaveFolderPathName = getWorldSaveFolder().toString();

		StructureCategory.getNames().forEach(category -> {
			List<Path> templatePaths;
			// build the path
			Path folderPath = Paths.get(worldSaveFolderPathName, DATAPACKS_TEMPLATES_ROOT, category.toLowerCase());

			try {
				templatePaths = ModUtil.getPathsFromFlatDatapacks(folderPath);

				for (Path path : templatePaths) {
					Treasure.LOGGER.debug("template path from fs -> {}", path);
					// load the shell from the jar
					Optional<GottschTemplate> shell = loadFromFileSystem(path, markerBlocks, replacementBlocks);
					// extra step - strip the beginning path from the path, so it is just data/treasure2/...
					String p = path.toString().replace(worldSaveFolderPathName, "");
					// register
					registerDatapacksTemplate(Paths.get(p), shell.get());
				}
			} catch(NoSuchFileException e) {
				// silently sallow exception
			} catch (Exception e) {
				Treasure.LOGGER.error("An error occurred attempting to register a loot table from the world save datapacks folder: ", e);
			}			
		});

		/*
		 *  load/register datapacks .zip files from world save folder
		 */
		Treasure.LOGGER.debug("loading datapack template files ...");
		// get all .zip files in the folder (non-recursive)
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(worldSaveFolderPathName))) {
			for (Path jarPath : stream) {
				Treasure.LOGGER.debug("datapack path -> {}", jarPath);
				if (Files.isRegularFile(jarPath, new LinkOption[] {})) {
					if (jarPath.getFileName().toString().endsWith(".zip")) {
						// process this zip file
						Treasure.LOGGER.debug("datapack file -> {}", jarPath.toString());
						try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
							StructureCategory.getNames().forEach(category -> {
								List<Path> templatePaths;				
								try {
									templatePaths = ModUtil.getPathsFromResourceJAR(jarPath, DATAPACKS_TEMPLATES_ROOT + category.toLowerCase());

									for (Path path : templatePaths) {
										Treasure.LOGGER.debug("datapack template path -> {}", path);
										// load the shell from the jar
										Optional<GottschTemplate> shell = loadFromZip(zipFile, path, markerBlocks, replacementBlocks);
										// register
										registerDatapacksTemplate(path, shell.get());
									}
								} catch (Exception e) {
									// minimal message
									Treasure.LOGGER.warn("warning: unable to load datapack -> {}", jarPath + "/" + DATAPACKS_TEMPLATES_ROOT + category.toLowerCase());
								}
							});
						}
					}
				}
			}
		} catch(Exception e) {
			Treasure.LOGGER.error("error: unable to load datapack:", e);
		}
	}

	/**
	 * 
	 * @param path
	 * @return
	 */
	public static Optional<GottschTemplate> loadFromFileSystem(Path path, List<Block> markerBlocks, Map<BlockState, BlockState> replacementBlocks) {
		try {
			File file = path.toFile();
			if (file.exists()) {
				InputStream inputStream = new FileInputStream(file);
				Optional<GottschTemplate> template = loadTemplateFromStream(inputStream, markerBlocks, replacementBlocks);
				return template;
			}
		}
		catch(Exception e) {
			Treasure.LOGGER.warn("Unable to loot table manifest");
		}	
		return Optional.empty();
	}

	/**
	 * 
	 * @param zipFile
	 * @param resourceFilePath
	 * @param markerBlocks
	 * @param replacementBlocks
	 * @return
	 */
	public static Optional<GottschTemplate> loadFromZip(ZipFile zipFile, Path resourceFilePath, List<Block> markerBlocks, Map<BlockState, BlockState> replacementBlocks) {
		Optional<GottschTemplate> resourceTemplate = Optional.empty();

		try {
			ZipEntry zipEntry = zipFile.getEntry(resourceFilePath.toString());
			InputStream stream = zipFile.getInputStream(zipEntry);
			//			Reader reader = new InputStreamReader(stream);
			// load the loot table
			//			resourceTemplate =  Optional.of(loadLootTable(reader));
			resourceTemplate = loadTemplateFromStream(stream, markerBlocks, replacementBlocks);
			// close resources
			stream.close();

		} catch(Exception e) {
			Treasure.LOGGER.error(String.format("Couldn't load resource loot table from zip file ->  {}", resourceFilePath), e);
		}
		return resourceTemplate;
	}

	/**
	 * 
	 * @param path
	 * @param template
	 */
	private static void registerDatapacksTemplate(Path path, GottschTemplate template) {
		Treasure.LOGGER.warn("attempting to register from datapack -> {}", path);

		// extract the category
		String categoryToken = path.getName(3).toString();
		Optional<IStructureCategory> category = TreasureApi.getStructureCategory(categoryToken);
		if (category.isEmpty()) {
			Treasure.LOGGER.warn("structure category -> '{}' is not registgered", categoryToken);
			return;
		}

		String typeToken = null;
		Optional<IStructureType> type = Optional.empty();
		if (path.getNameCount() > 4) {
			typeToken = path.getName(4).toString();
			type = TreasureApi.getStructureType(typeToken);
			if (type.isEmpty()) {
				Treasure.LOGGER.warn("structure type -> '{}' is not registgered", typeToken);
				return;
			}
		}

		// convert to resource location
		ResourceLocation resourceLocation = asResourceLocation(path);
		Treasure.LOGGER.debug("resource location -> {}", resourceLocation);

		// setup the template holder
		TemplateHolder holder = new TemplateHolder()
				.setLocation(resourceLocation)
				.setTemplate(template);				

		if (path.getNameCount() > 5) {
			for (int i = 5; i < path.getNameCount(); i++) {
				holder.getTags().add(path.getName(i).toString());
			}
		}

		// add to map - will replace previous existing
		DATAPACK_MAP.put(resourceLocation, holder);

		// add to table
		if (!DATAPACK_TABLE.contains(category.get(), type.get())) {
			DATAPACK_TABLE.put(category.get(), type.get(),new ArrayList<>());
		}
		
		// compare resource location to all other resource locations 
		// TODO see loot table DATAPACK add, for multiple datapacks.
		List<TemplateHolder> holders = DATAPACK_TABLE.get(category.get(), type.get());
		Optional<TemplateHolder> foundTemplate = holders.stream().filter(h -> h.getLocation().equals(resourceLocation)).findFirst();
		// remove element if it matches the new holder location
		if (foundTemplate.isPresent()) {
			holders.remove(foundTemplate.get());
		}
		holders.add(holder);
		Treasure.LOGGER.debug("tabling datapack template -> [{}, {}] -> {}", category.get(), type.get(), holder.getLocation().toString());
	}

	public static Collection<TemplateHolder> getTemplate(StructureType structureType) {
		List<TemplateHolder> templateHolders = new ArrayList<>();
		// get all built-in templates
		TABLE.column(structureType).forEach((key, list) -> {
			templateHolders.addAll(list);
		});
		
		// get all datapack templates
		List<TemplateHolder> datapackTemplateHolders = new ArrayList<>();
		DATAPACK_TABLE.column(structureType).forEach((key, list) -> {
			datapackTemplateHolders.addAll(list);		
		});

		// if datapack holder exists with same name as built-in, replace the built-in.
		if (!datapackTemplateHolders.isEmpty()) {
			datapackTemplateHolders.stream().forEach(holder -> {
				Optional<TemplateHolder> foundHolder = templateHolders.stream().filter(h -> h.getLocation().equals(holder.getLocation())).findFirst();
				if (foundHolder.isPresent()) {
					templateHolders.remove(foundHolder.get());
				}
				templateHolders.add(holder);
			});
		}
		return templateHolders;
	}
	
	/**
	 * 
	 * @param name
	 * @return
	 */
	public static Optional<TemplateHolder> getTemplate(ResourceLocation name) {
		TemplateHolder templateHolder = DATAPACK_MAP.get(name);
		if (templateHolder == null) {
			templateHolder = MAP.get(name);
		}
		return Optional.ofNullable(templateHolder);
	}
	
	/**
	 * 
	 * @param random
	 * @param category
	 * @param type
	 * @return
	 */
	public static List<TemplateHolder> getTemplate(IStructureCategory category, IStructureType type) {
		final List<TemplateHolder> templateHolders = TABLE.get(category, type);
		final List<TemplateHolder> datapackTemplateHolders = DATAPACK_TABLE.get(category, type);

		// if datapack holder exists with same name as built-in, replace the built-in.
		if (templateHolders != null && datapackTemplateHolders != null 
				&& !datapackTemplateHolders.isEmpty()) {
			datapackTemplateHolders.stream().forEach(holder -> {
				Optional<TemplateHolder> foundHolder = templateHolders.stream().filter(h -> h.getLocation().equals(holder.getLocation())).findFirst();
				if (foundHolder.isPresent()) {
					templateHolders.remove(foundHolder.get());
				}
				templateHolders.add(holder);
			});
		}		
		Treasure.LOGGER.trace("selected template holders -> {} ", templateHolders);

		if (templateHolders == null) {
			return new ArrayList<>();
		}
		return templateHolders;
	}

	/**
	 * 
	 * @param category
	 * @param type
	 * @param biome
	 * @return
	 */
	public static List<TemplateHolder> getTemplate(IStructureCategory category, IStructureType type, ResourceLocation biome) {

		AccessKey key = new AccessKey(category, type);
		
		List<TemplateHolder> blacklistHolders = BLACKLIST_TABLE.get(key, biome);
		
		// grab all templates by category + type
		List<TemplateHolder> templateHolders = getTemplate(category, type);

		// filter out any in the black list
		if (templateHolders != null && !templateHolders.isEmpty() && blacklistHolders != null && !blacklistHolders.isEmpty()) {
					templateHolders = templateHolders.stream()
					.filter(h -> blacklistHolders.stream().noneMatch(b -> b.getLocation().equals(h.getLocation())))
					.collect(Collectors.toList());			
		}
		
		// filter if the template has a whitelist and this biome is not included
		if (templateHolders != null && !templateHolders.isEmpty()) {
			templateHolders = templateHolders.stream()
				.filter(h -> {
				StructMeta meta = Config.structConfigMetaMap.get(h.getLocation());
				if (meta != null) {
					if ((meta.getBiomeWhitelist() != null && !meta.getBiomeWhitelist().isEmpty())) {
						if (!meta.getBiomeWhitelist().contains(biome.toString())) {
							Treasure.LOGGER.debug("biome not found in whitelist");
							return false;
						}
					}
				}
				return true;
			})
			.collect(Collectors.toList());	
		}
		
		if (templateHolders == null || templateHolders.isEmpty()) {
			Treasure.LOGGER.debug("could not find template holders for category -> {}, type -> {}", category, type);
		}

		if (templateHolders == null) {
			templateHolders = new ArrayList<>();
		}
		return templateHolders;
	}

	/**
	 * @param structMetaList
	 */
	public static void registerAccesslists(List<StructMeta> structMetaList) {

		// clear lists
		BLACKLIST_TABLE.clear();
		WHITELIST_TABLE.clear();

		// process each struct meta in the list
		structMetaList.forEach(meta -> {
			// create location for template
			ResourceLocation templateLocation = ModUtil.asLocation(meta.getName());
			Path templatePath = Paths.get(templateLocation.getPath());

			// extract the category
			String categoryToken = templatePath.getName(0).toString();
			Optional<IStructureCategory> category = TreasureApi.getStructureCategory(categoryToken);
			if (category.isEmpty()) {
				Treasure.LOGGER.warn("structure category -> '{}' is not registgered", categoryToken);
				return;
			}

			String typeToken = templatePath.getName(1).toString();
			Optional<IStructureType> type = TreasureApi.getStructureType(typeToken);
			if (type.isEmpty()) {
				Treasure.LOGGER.warn("structure type -> '{}' is not registgered", typeToken);
				return;
			}

			// build key
			AccessKey key = new AccessKey(category.get(), type.get());

			// get the template holder
			Optional<TemplateHolder> holder = getHolderByResourceLocation(templateLocation);
			if (holder.isPresent()) {
				// process black list
				meta.getBiomeBlacklist().forEach(biomeName -> {
					// create resource location
					ResourceLocation biomeLocation = ModUtil.asLocation(biomeName);
					// ensure that it is indeed a biome
					Biome biome = ForgeRegistries.BIOMES.getValue(biomeLocation);
					if (biome == null) {
						Treasure.LOGGER.warn("biome -> '{}' is not registgered", biomeLocation);
						return;
					}						
					if (!BLACKLIST_TABLE.contains(key, biomeLocation)) {
						BLACKLIST_TABLE.put(key, biomeLocation, new ArrayList<>());
					}
					BLACKLIST_TABLE.get(key, biomeLocation).add(holder.get());
				});

				// process white list
				meta.getBiomeWhitelist().forEach(biomeName -> {
					// create resource location
					ResourceLocation biomeLocation = ModUtil.asLocation(biomeName);
					if (!WHITELIST_TABLE.contains(key, biomeLocation)) {
						WHITELIST_TABLE.put(key, biomeLocation, new ArrayList<>());
					}
					WHITELIST_TABLE.get(key, biomeLocation).add(holder.get());
				});				
			}
		});
	}

	private static Optional<TemplateHolder> getHolderByResourceLocation(ResourceLocation templateLocation) {
		// first check datapacks
		TemplateHolder holder = DATAPACK_MAP.get(templateLocation);
		if (holder == null) {
			holder = MAP.get(templateLocation);
		}
		return Optional.ofNullable(holder);
	}

	public static Path getWorldSaveFolder() {
		return TreasureTemplateRegistry.worldSaveFolder;
	}

	public static void setWorldSaveFolder(Path worldSaveFolder) {
		TreasureTemplateRegistry.worldSaveFolder = worldSaveFolder;
	}

	public static List<Block> getMarkerScanList() {
		return markerScanList;
	}

	public void setMarkerScanList(List<Block> scanList) {
		TreasureTemplateRegistry.markerScanList = scanList;
	}

	public static Map<StructureMarkers, Block> getMarkerMap() {
		return markerMap;
	}

	public void setMarkerMap(Map<StructureMarkers, Block> markerMap) {
		TreasureTemplateRegistry.markerMap = markerMap;
	}

	public static Map<BlockState, BlockState> getReplacementMap() {
		return replacementMap;
	}

	public void setReplacementMap(Map<BlockState, BlockState> replacementMap) {
		TreasureTemplateRegistry.replacementMap = replacementMap;
	}

	public static ICoords getOffset(RandomSource random, GottschTemplate template) {
		ICoords offsetCoords = template.findCoords(random, getMarkerMap().get(StructureMarkers.OFFSET));
		return offsetCoords;
	}

	public static ICoords getOffsetFrom(RandomSource random, GottschTemplate template, StructureMarkers marker) {
		ICoords offsetCoords = template.findCoords(random, getMarkerMap().get(marker));
		return offsetCoords;
	}
}
