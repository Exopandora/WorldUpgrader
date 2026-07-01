package com.github.exopandora.worldupgrader

import com.github.exopandora.worldupgrader.mixin.*
import com.google.common.base.Suppliers
import it.unimi.dsi.fastutil.ints.IntArraySet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import net.minecraft.CrashReport
import net.minecraft.ReportedException
import net.minecraft.core.*
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.*
import net.minecraft.world.level.biome.FeatureSorter.StepFeatureData
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.storage.RegionFile
import net.minecraft.world.level.chunk.storage.RegionFileStorage
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomSupport
import net.minecraft.world.level.levelgen.WorldgenRandom
import net.minecraft.world.level.levelgen.XoroshiroRandomSource
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.minecraft.world.level.levelgen.structure.Structure
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.io.path.name

private val logger = LoggerFactory.getLogger(DecorationUpgrade::class.java.simpleName)

fun upgrade(server: MinecraftServer) {
    val versionsToUpgrade = System.getProperty("worldupgrader.versions", "").split(",").toSet()
    val versionUpgrades = compileUpgrades(versionsToUpgrade)
    if (versionUpgrades.isEmpty()) {
        logger.info("Skipping world upgrades because no valid versions were specified")
        return
    }
    logger.info("Upgrading worlds with feature set {}", versionsToUpgrade.joinToString())
    val registryAccess = server.registryAccess()
    val biome2features = createBiomeFeatureMap(registryAccess)
    val biomeRegistry = registryAccess.lookupOrThrow(Registries.BIOME)
    biome2features.forEach { (biome, features) ->
        logger.info("${biomeRegistry.getKey(biome)}=${features.joinToString(",")}")
    }
    upgradeLevel(server, Level.OVERWORLD, versionUpgrades.overworldUpgrades, biomeRegistry, biome2features)
    upgradeLevel(server, Level.NETHER, versionUpgrades.netherUpgrades, biomeRegistry, biome2features)
    upgradeLevel(server, Level.END, versionUpgrades.endUpgrades, biomeRegistry, biome2features)
    logger.info("Done upgrading worlds")
}

private fun compileUpgrades(versions: Set<String>): VersionUpgrade =
    versions.sorted()
        .mapNotNull { upgradeIndex[it] }
        .reduceOrNull(VersionUpgrade::merge)
        ?: VersionUpgrade.EMPTY

private fun createBiomeFeatureMap(
    registryAccess: RegistryAccess
): Map<Biome, Set<ResourceKey<ConfiguredFeature<*, *>>>> {
    val configuredFeatureRegistry = registryAccess.lookupOrThrow(Registries.CONFIGURED_FEATURE)
    return registryAccess.lookupOrThrow(Registries.BIOME).associateWith { biome ->
        biome.generationSettings.features().stream()
            .flatMap { it.stream() }
            .map { it.value() }
            .flatMap { it.features }
            .map { configuredFeatureRegistry.getResourceKey(it.value()) }
            .filter { it.isPresent }
            .map { it.get() }
            .collect(Collectors.toSet())
    }
}

private fun createBiome2upgrades(
    levelUpgrade: LevelUpgrade,
    biome2features: Map<Biome, Set<ResourceKey<ConfiguredFeature<*, *>>>>
) = biome2features.mapValues { (_, features) ->
        UpgradeSet(
            levelUpgrade.decorationUpgrades.filter { upgrade -> upgrade.features.intersect(features).isNotEmpty() }.toSet(),
            features.intersect(levelUpgrade.featureUpgrades)
        )
    }
    .filterValues { it.decorationUpgrades.isNotEmpty() || it.featureUpgrades.isNotEmpty() }

private fun upgradeLevel(
    server: MinecraftServer,
    dimension: ResourceKey<Level>,
    levelUpgrade: LevelUpgrade,
    biomeRegistry: Registry<Biome>,
    biome2features: Map<Biome, Set<ResourceKey<ConfiguredFeature<*, *>>>>
) {
    if (levelUpgrade.isEmpty()) return
    val level = server.getLevel(dimension)!!
    val biome2upgrades = createBiome2upgrades(levelUpgrade, biome2features)
    logger.info("${level.dimension().identifier()} biomes to upgrades:")
    biome2upgrades.forEach { (biome, features) ->
        logger.info("${biomeRegistry.getKey(biome)}=${features}")
    }
    val chunkCache = level.chunkSource
    val chunkMap = chunkCache.chunkMap
    val regionFileStorage = (chunkMap.chunkScanner() as IOWorkerAccessor).storage
    val storageSource = (server as MinecraftServerAccessor).storageSource
    val dimensionPath = storageSource.getDimensionPath(level.dimension())
    
    if (levelUpgrade.entityUpgrades.isNotEmpty()) {
        val entityUpgrades = levelUpgrade.entityUpgrades.associateBy { it.entityId }
        val entityRegionFileStorage = (((((level as ServerLevelAccessor).entityManager as PersistentEntitySectionManagerAccessor<*>).permanentStorage as EntityStorageAccessor).simpleRegionStorage as SimpleRegionStorageAccessor).worker as IOWorkerAccessor).storage
        forEachChunk(server, chunkCache, dimensionPath.resolve("entities"), entityRegionFileStorage) { chunkPos, regionFile ->
            upgradeEntities(chunkPos, regionFile, level, entityUpgrades)
        }
    }
    if (biome2upgrades.isNotEmpty() || levelUpgrade.structureUpgrades.isNotEmpty()) {
        val generatorConfig = GeneratorConfig.of(level)
        val structureRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
        val structureUpgrades = levelUpgrade.structureUpgrades.flatMap { upgrade -> upgrade.structures.map { it to upgrade } }
            .groupBy({ it.first }, { it.second })
            .mapKeys { (key, _) -> structureRegistry.getValueOrThrow(key) }
        forEachChunk(server, chunkCache, dimensionPath.resolve("region"), regionFileStorage) { chunkPos, _ ->
            upgradeChunk(level, level.chunkAt(chunkPos), generatorConfig, biome2upgrades, structureUpgrades)
        }
    }
}

private fun upgradeEntities(
    chunkPos: ChunkPos,
    regionFile: RegionFile,
    level: ServerLevel,
    entityUpgrades: Map<Identifier, EntityUpgrade>
) {
    logger.info("Upgrading entities in chunk $chunkPos")
    val chunkTag = regionFile.getChunkDataInputStream(chunkPos)
        ?.use(NbtIo::read)
        ?: return
    val entities = chunkTag.getListOrEmpty("Entities")
        .takeIf { it.isNotEmpty() }
        ?.map { it as CompoundTag }
        ?: return
    val chunk = lazy { level.chunkAt(chunkPos) }
    upgradeEntities(level, chunk, entities, entityUpgrades)
    chunkTag.put("Entities", ListTag(entities))
    regionFile.getChunkDataOutputStream(ChunkPos(chunkPos.x % 32, chunkPos.z % 32))
        .use { dataOutput -> NbtIo.write(chunkTag, dataOutput) }
}

private fun upgradeEntities(
    level: ServerLevel,
    chunk: Lazy<LevelChunk>,
    entities: List<CompoundTag>,
    entityUpgrades: Map<Identifier, EntityUpgrade>
) {
    entities.forEach { entity ->
        entity.getString("id")
            .map { entityId -> entityUpgrades[Identifier.parse(entityId)] }
            .ifPresent { upgrade -> upgrade.upgrade(chunk.value, entity, level) }
    }
}

private val mcaPattern = Regex("""r\.(-?\d+).(-?\d+)\.mca""").toPattern()

private fun forEachChunk(
    server: MinecraftServer,
    chunkSource: ServerChunkCache,
    dimensionPath: Path,
    regionFileStorage: RegionFileStorage,
    consumer: (ChunkPos, RegionFile) -> Unit
) {
    if (!Files.exists(dimensionPath)) return
    Files.list(dimensionPath).forEach { mca ->
        val matcher = mcaPattern.matcher(mca.name)
        if (!matcher.matches()) {
            return@forEach
        }
        logger.info("Upgrading mca ${mca.name}")
        try {
            val regionOffsetX = matcher.group(1).toInt()
            val regionOffsetZ = matcher.group(2).toInt()
            val regionOffset = ChunkPos(regionOffsetX, regionOffsetZ)
            val fromChunkPos = ChunkPos.ZERO.atRegionOffset(regionOffset)
            val toChunkPos = ChunkPos(31, 31).atRegionOffset(regionOffset)
            @Suppress("CAST_NEVER_SUCCEEDS")
            val regionFile = (regionFileStorage as RegionFileStorageAccessor).invokeGetRegionFile(fromChunkPos)
            val positions = ChunkPos.rangeClosed(fromChunkPos, toChunkPos)
                .filter { chunkPos -> regionFile.hasChunk(chunkPos) }
                .collect(Collectors.toList())
            positions.forEach { chunkPos -> consumer(chunkPos, regionFile) }
            regionFileStorage.flush()
            while ((chunkSource.chunkMap as ChunkMapAccessor).updatingChunkMap.size > 3000) {
                chunkSource.tick({ true }, true)
                (server as BlockableEventLoopAccessor).invokeRunAllTasks()
                with(server as MinecraftServerAccessor) {
                    setWaitingForNextTick(true)
                    try {
                        server.managedBlock { server.pendingTasksCount == 0 }
                    } finally {
                        setWaitingForNextTick(false)
                    }
                }
            }
            server.saveAllChunks(true, true, true)
        } catch (e: Throwable) {
            logger.error("Error", e)
        }
    }
}

private fun upgradeChunk(
    level: ServerLevel,
    chunk: LevelChunk,
    generatorConfig: GeneratorConfig,
    biome2upgrades: Map<Biome, UpgradeSet>,
    structure2upgrades: Map<Structure, List<StructureUpgrade>>,
) {
    logger.info("Upgrading chunk ${level.dimension().identifier()} ${chunk.pos}")
    
    if (structure2upgrades.isNotEmpty() && chunk.hasAnyStructureReferences()) {
        structure2upgrades.forEach { (structure, structureUpgrades) ->
            chunk.getStartForStructure(structure)?.let { structureStart ->
                structureUpgrades.forEach { it.upgrade(chunk, level, structureStart) }
            }
        }
    }
    
    if (biome2upgrades.isEmpty()) return
    
    val worldgenRandom = WorldgenRandom(XoroshiroRandomSource(RandomSupport.generateUniqueSeed()))
    val biomes: MutableSet<Holder<Biome>> = ObjectArraySet()
    ChunkPos.rangeClosed(chunk.pos, 1).forEach { chunkPos ->
        for (levelChunkSection in level.chunkAt(chunkPos).sections) {
            levelChunkSection.biomes.getAll(biomes::add)
        }
    }
    biomes.retainAll(generatorConfig.biomeSource.possibleBiomes())
    
    val featureUpgrades = biomes.asSequence()
        .mapNotNull { biome2upgrades[it.value()] }
        .flatMap { it.featureUpgrades }
        .toSet()
    
    if (featureUpgrades.isNotEmpty()) {
        placeFeatures(featureUpgrades, chunk, level, biomes, generatorConfig, worldgenRandom)
    }
    
    ChunkPos.rangeClosed(ChunkPos.ZERO, ChunkPos(3, 3)).forEach { subSectionInChunkPos ->
        val subSectionWorldOriginX = chunk.pos.getBlockX(subSectionInChunkPos.x shl 2)
        val subSectionWorldOriginZ = chunk.pos.getBlockZ(subSectionInChunkPos.z shl 2)
        val subSectionWorldSurfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, subSectionWorldOriginX, subSectionWorldOriginZ)
        val subSectionBlockPos = BlockPos(subSectionWorldOriginX, subSectionWorldSurfaceY, subSectionWorldOriginZ).mutable()
        val biome = level.getBiome(subSectionBlockPos)
        val upgrades = biome2upgrades[biome.value()]?.decorationUpgrades ?: return@forEach
        val from = ChunkPos(subSectionWorldOriginX, subSectionWorldOriginZ)
        val to = ChunkPos(subSectionWorldOriginX + 3, subSectionWorldOriginZ + 3)
        ChunkPos.rangeClosed(from, to).forEach { posInSubSection ->
            val height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, posInSubSection.x, posInSubSection.z)
            val testPos = BlockPos(posInSubSection.x, height, posInSubSection.z)
            for (upgrade in upgrades) {
                val state = chunk.getBlockState(testPos)
                val resultPos = upgrade.test(level, testPos, state)
                if (resultPos != null) {
                    upgrade.place(level, worldgenRandom, testPos, resultPos, biome)
                    break
                }
            }
        }
    }
}

data class UpgradeSet(
    val decorationUpgrades: Set<DecorationUpgrade>,
    val featureUpgrades: Set<ResourceKey<ConfiguredFeature<*, *>>>
)

data class GeneratorConfig(
    val biomeSource: BiomeSource,
    val generationSettingsGetter: (Holder<Biome>) -> BiomeGenerationSettings,
    val featuresPerStep: Supplier<List<StepFeatureData>>
) {
    companion object {
        fun of(level: ServerLevel): GeneratorConfig {
            val biomeSource = (level.chunkSource.chunkMap as ChunkMapAccessor).worldGenContext.generator.biomeSource
            val generationSettingsGetter = { holder: Holder<Biome> -> holder.value().generationSettings }
            val featuresPerStep = Suppliers.memoize {
                FeatureSorter.buildFeaturesPerStep(
                    biomeSource.possibleBiomes().toList(),
                    { holder: Holder<Biome> -> generationSettingsGetter(holder).features() },
                    true
                )
            }
            return GeneratorConfig(biomeSource, generationSettingsGetter, featuresPerStep)
        }
    }
}

private fun placeFeatures(
    featuresToPlace: Set<ResourceKey<ConfiguredFeature<*, *>>>,
    chunk: LevelChunk,
    level: ServerLevel,
    biomes: Set<Holder<Biome>>,
    generatorConfig: GeneratorConfig,
    worldgenRandom: WorldgenRandom
) {
    val sectionPos = SectionPos.of(chunk.pos, level.minSectionY)
    val blockPos = sectionPos.origin()
    val placedFeatureRegistry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
    val featuresPerStep = generatorConfig.featuresPerStep.get()
    val featuresPerStepCount = featuresPerStep.size
    val decorationSeed = worldgenRandom.setDecorationSeed(level.seed, blockPos.x, blockPos.z)
    
    for (x in 0..< featuresPerStepCount) {
        val intSet: IntSet = IntArraySet()
        
        for (biomeHolder in biomes) {
            val features = generatorConfig.generationSettingsGetter(biomeHolder).features()
            if (x < features.size) {
                val stepFeatureData = featuresPerStep[x]
                features[x].stream()
                    .map { it.value() }
                    .forEach { intSet.add(stepFeatureData.indexMapping().applyAsInt(it)) }
            }
        }
        
        val n = intSet.size
        val placedFeatureLookup = intSet.toIntArray()
        Arrays.sort(placedFeatureLookup)
        val stepFeatureData2 = featuresPerStep[x]
        
        for (y in 0..< n) {
            val placedFeatureIndex = placedFeatureLookup[y]
            val placedFeature = stepFeatureData2.features()[placedFeatureIndex]
            if (featuresToPlace.none(placedFeature.feature::`is`)) {
                continue
            }
            val featureToGenerate = {
                placedFeatureRegistry.getResourceKey(placedFeature)
                    .map { it.toString() }
                    .orElseGet { placedFeature.toString() }
            }
            worldgenRandom.setFeatureSeed(decorationSeed, placedFeatureIndex, x)
            
            try {
                level.setCurrentlyGenerating(featureToGenerate)
                placedFeature.placeWithBiomeCheck(level, level.chunkSource.generator, worldgenRandom, blockPos)
            } catch (e: Exception) {
                val crashReport = CrashReport.forThrowable(e, "Feature placement")
                crashReport.addCategory("Feature").setDetail("Description", featureToGenerate)
                throw ReportedException(crashReport)
            }
        }
    }
    
    level.setCurrentlyGenerating(null)
}
