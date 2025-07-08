package com.github.exopandora.worldupgrader

import com.github.exopandora.worldupgrader.PillarEntry.Companion.optional
import com.github.exopandora.worldupgrader.PillarEntry.Companion.required
import com.github.exopandora.worldupgrader.mixin.AccessorBlockableEventLoop
import com.github.exopandora.worldupgrader.mixin.AccessorChunkGenerator
import com.github.exopandora.worldupgrader.mixin.AccessorChunkMap
import com.github.exopandora.worldupgrader.mixin.AccessorEntityStorage
import com.github.exopandora.worldupgrader.mixin.AccessorIOWorker
import com.github.exopandora.worldupgrader.mixin.AccessorMinecraftServer
import com.github.exopandora.worldupgrader.mixin.AccessorPersistentEntitySectionManager
import com.github.exopandora.worldupgrader.mixin.AccessorRegionFileStorage
import com.github.exopandora.worldupgrader.mixin.AccessorServerLevel
import com.github.exopandora.worldupgrader.mixin.AccessorSimpleRegionStorage
import com.google.common.base.Suppliers
import it.unimi.dsi.fastutil.ints.IntArraySet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.io.path.name
import net.minecraft.CrashReport
import net.minecraft.ReportedException
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.features.TreeFeatures
import net.minecraft.data.worldgen.features.VegetationFeatures
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.TemperatureVariants
import net.minecraft.world.entity.animal.wolf.WolfVariants
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeGenerationSettings
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.biome.FeatureSorter
import net.minecraft.world.level.biome.FeatureSorter.StepFeatureData
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.chunk.storage.RegionFile
import net.minecraft.world.level.chunk.storage.RegionFileStorage
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomSupport
import net.minecraft.world.level.levelgen.WorldgenRandom
import net.minecraft.world.level.levelgen.XoroshiroRandomSource
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration
import net.minecraft.world.level.levelgen.feature.treedecorators.PlaceOnGroundDecorator
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator
import net.minecraft.world.level.levelgen.structure.BuiltinStructures
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilPieces
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory

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
    versions.sorted().mapNotNull { upgradeDefinitions[it] }.reduceOrNull(VersionUpgrade::merge) ?: VersionUpgrade.EMPTY

private fun createBiomeFeatureMap(
    registryAccess: RegistryAccess
): Map<Biome, Set<ResourceKey<ConfiguredFeature<*, *>>>> {
    val configuredFeatureRegistry = registryAccess.lookupOrThrow(Registries.CONFIGURED_FEATURE)
    return registryAccess.lookupOrThrow(Registries.BIOME).associateWith { biome ->
        biome.generationSettings.features().stream()
            .flatMap { it.stream() }
            .map { it.value() }
            .flatMap { it.features }
            .map { configuredFeatureRegistry.getResourceKey(it) }
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
    logger.info("${level.dimension().location()} biomes to upgrades:")
    biome2upgrades.forEach { (biome, features) ->
        logger.info("${biomeRegistry.getKey(biome)}=${features}")
    }
    val chunkCache = level.chunkSource
    val chunkMap = chunkCache.chunkMap
    val regionFileStorage = (chunkMap.chunkScanner() as AccessorIOWorker).storage
    val storageSource = (server as AccessorMinecraftServer).storageSource
    val dimensionPath = storageSource.getDimensionPath(level.dimension())
    
    if (levelUpgrade.entityUpgrades.isNotEmpty()) {
        val entityUpgrades = levelUpgrade.entityUpgrades.associateBy { it.entityId }
        val entityRegionFileStorage = (((((level as AccessorServerLevel).entityManager as AccessorPersistentEntitySectionManager<*>).permanentStorage as AccessorEntityStorage).simpleRegionStorage as AccessorSimpleRegionStorage).worker as AccessorIOWorker).storage
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
    entityUpgrades: Map<ResourceLocation, EntityUpgrade>
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

private fun ServerLevel.chunkAt(
    chunkPos: ChunkPos,
    chunkStatus: ChunkStatus = ChunkStatus.FULL
): LevelChunk =
    getChunk(chunkPos.x, chunkPos.z, chunkStatus, true) as LevelChunk

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
            val regionFile = (regionFileStorage as AccessorRegionFileStorage).invokeGetRegionFile(fromChunkPos)
            val positions = ChunkPos.rangeClosed(fromChunkPos, toChunkPos)
                .filter { chunkPos -> regionFile.hasChunk(chunkPos) }
                .collect(Collectors.toList())
            positions.forEach { chunkPos -> consumer(chunkPos, regionFile) }
            regionFileStorage.flush()
            while ((chunkSource.chunkMap as AccessorChunkMap).updatingChunkMap.size > 3000) {
                chunkSource.tick({ true }, true)
                (server as AccessorBlockableEventLoop).invokeRunAllTasks()
                with(server as AccessorMinecraftServer) {
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

private fun ChunkPos.atRegionOffset(regionOffset: ChunkPos): ChunkPos =
    ChunkPos(x + (regionOffset.x shl 5), z + (regionOffset.z shl 5))

interface PillarEntry {
    val count: Int
    val required: Boolean
    
    fun test(blockState: BlockState): Boolean
    
    data class BlockPillarEntry(
        private val block: Block,
        override val count: Int,
        override val required: Boolean
    ) : PillarEntry {
        override fun test(blockState: BlockState): Boolean =
            blockState.`is`(block)
    }
    
    data class BlockTagPillarEntry(
        private val tag: TagKey<Block>,
        override val count: Int,
        override val required: Boolean
    ) : PillarEntry {
        override fun test(blockState: BlockState): Boolean =
            blockState.`is`(tag)
    }
    
    data class BlockStatePillarEntry(
        private val blockState: BlockState,
        override val count: Int,
        override val required: Boolean
    ) : PillarEntry {
        override fun test(blockState: BlockState): Boolean =
            blockState == this.blockState
    }
    
    @Suppress("unused")
    companion object {
        fun required(block: Block, count: Int): PillarEntry =
            BlockPillarEntry(block, count, true)
        
        fun required(tag: TagKey<Block>, count: Int): PillarEntry =
            BlockTagPillarEntry(tag, count, true)
        
        fun required(blockState: BlockState, count: Int): PillarEntry =
            BlockStatePillarEntry(blockState, count, true)
        
        fun optional(block: Block, count: Int): PillarEntry =
            BlockPillarEntry(block, count, false)
        
        fun optional(tag: TagKey<Block>, count: Int): PillarEntry =
            BlockTagPillarEntry(tag, count, false)
        
        fun optional(blockState: BlockState, count: Int): PillarEntry =
            BlockStatePillarEntry(blockState, count, false)
    }
}

private val oakDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
    override val features = setOf(
        TreeFeatures.OAK_LEAF_LITTER,
        TreeFeatures.OAK_BEES_0002_LEAF_LITTER,
    )
    
    override val pillarDefinition = listOf(
        required(Blocks.OAK_LEAVES, 1),
        required(Blocks.OAK_LOG, 4),
        optional(Blocks.OAK_LOG, 2),
        required(Blocks.DIRT, 1),
    )
    
    override fun place(level: ServerLevel, random: RandomSource, testPos: BlockPos, resultPos: BlockPos, biome: Holder<Biome>) {
        placeLeafLitter(TreeFeatures.OAK_LEAF_LITTER, level, random, resultPos)
    }
}

private val fanyOakDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
    override val features = setOf(
        TreeFeatures.FANCY_OAK_LEAF_LITTER,
        TreeFeatures.FANCY_OAK_BEES_0002_LEAF_LITTER,
    )
    
    override val pillarDefinition = listOf(
        required(Blocks.OAK_LEAVES, 4),
        required(Blocks.OAK_LOG, 8),
        optional(Blocks.OAK_LOG, 4),
        required(Blocks.DIRT, 1),
    )
    
    override fun place(level: ServerLevel, random: RandomSource, testPos: BlockPos, resultPos: BlockPos, biome: Holder<Biome>) {
        placeLeafLitter(TreeFeatures.FANCY_OAK_LEAF_LITTER, level, random, resultPos)
    }
}

private val birchDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
    override val features = setOf(
        TreeFeatures.BIRCH_LEAF_LITTER,
        TreeFeatures.BIRCH_BEES_0002_LEAF_LITTER
    )
    
    override val pillarDefinition = listOf(
        required(Blocks.BIRCH_LEAVES, 1),
        required(Blocks.BIRCH_LOG, 5),
        optional(Blocks.BIRCH_LOG, 2),
        required(Blocks.DIRT, 1),
    )
    
    override fun place(level: ServerLevel, random: RandomSource, testPos: BlockPos, resultPos: BlockPos, biome: Holder<Biome>) {
        val feature = when {
            biome.`is`(Biomes.BIRCH_FOREST) || biome.`is`(Biomes.OLD_GROWTH_BIRCH_FOREST) -> TreeFeatures.BIRCH_LEAF_LITTER
            else -> TreeFeatures.OAK_LEAF_LITTER
        }
        placeLeafLitter(feature, level, random, resultPos)
    }
}

private val darkOakDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
    override val features = setOf(TreeFeatures.DARK_OAK_LEAF_LITTER)
    
    override val pillarDefinition: List<PillarEntry> = listOf(
        required(Blocks.DARK_OAK_LEAVES, 1),
        optional(Blocks.DARK_OAK_LEAVES, 2),
        optional(Blocks.AIR, 1),
        required(Blocks.DARK_OAK_LOG, 6),
        optional(Blocks.DARK_OAK_LOG, 3),
        required(Blocks.DIRT, 1),
    )
    
    override fun test(level: Level, pos: BlockPos, blockState: BlockState): BlockPos? {
        val nw = super.test(level, pos, blockState) ?: return null
        val ne = super.test(level, pos.east(), blockState) ?: return null
        if (nw.y != ne.y) return null
        val sw = super.test(level, pos.south(), blockState) ?: return null
        if (nw.y != sw.y) return null
        val se = super.test(level, pos.offset(1, 0, 1), blockState) ?: return null
        if (nw.y != se.y) return null
        return nw
    }
    
    override fun place(level: ServerLevel, random: RandomSource, testPos: BlockPos, resultPos: BlockPos, biome: Holder<Biome>) {
        placeLeafLitter(TreeFeatures.DARK_OAK_LEAF_LITTER, level, random, resultPos)
    }
}

private val cactusDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
    override val features = setOf(
        VegetationFeatures.PATCH_CACTUS
    )
    
    override val pillarDefinition = listOf(
        required(Blocks.CACTUS, 1),
        optional(Blocks.CACTUS, 3),
        required(BlockTags.SAND, 1),
    )
    
    override fun place(level: ServerLevel, random: RandomSource, testPos: BlockPos, resultPos: BlockPos, biome: Holder<Biome>) {
        if (random.nextFloat() < 0.25F) {
            val flowerPos = testPos.above()
            if (
                level.getBlockState(flowerPos.north()).isAir &&
                level.getBlockState(flowerPos.east()).isAir &&
                level.getBlockState(flowerPos.south()).isAir &&
                level.getBlockState(flowerPos.west()).isAir
            ) {
                level.setBlock(flowerPos, Blocks.CACTUS_FLOWER.defaultBlockState(), WORLD_GEN_BLOCK_UPDATE_FLAGS)
            }
        }
    }
}

private val chickenVariantUpgrade = TemperatureVariantEntityUpgrade(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.CHICKEN))
private val cowVariantUpgrade = TemperatureVariantEntityUpgrade(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COW))
private val pigVariantUpgrade = TemperatureVariantEntityUpgrade(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG))

private val wolfVariantUpgrade = object : VariantEntityUpgrade(
    entityId = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.WOLF),
    defaultVariant = WolfVariants.PALE.location()
) {
    override fun variant(pos: BlockPos, level: ServerLevel, biome: Holder<Biome>): ResourceLocation =
        when {
            biome.`is`(BiomeTags.IS_SAVANNA) -> WolfVariants.SPOTTED.location()
            biome.`is`(Biomes.GROVE) -> WolfVariants.SNOWY.location()
            biome.`is`(Biomes.OLD_GROWTH_PINE_TAIGA) -> WolfVariants.BLACK.location()
            biome.`is`(Biomes.SNOWY_TAIGA) -> WolfVariants.ASHEN.location()
            biome.`is`(BiomeTags.IS_JUNGLE) -> WolfVariants.RUSTY.location()
            biome.`is`(Biomes.FOREST) -> WolfVariants.WOODS.location()
            biome.`is`(Biomes.OLD_GROWTH_SPRUCE_TAIGA) -> WolfVariants.CHESTNUT.location()
            biome.`is`(BiomeTags.IS_BADLANDS) -> WolfVariants.STRIPED.location()
            else -> WolfVariants.PALE.location()
        }
}

val netherFossilStructureUpgrade = object : StructureUpgrade {
    override val structures = setOf(BuiltinStructures.NETHER_FOSSIL)
    
    override fun upgrade(chunk: LevelChunk, level: ServerLevel, structureStart: StructureStart) {
        structureStart.pieces.forEach { piece ->
            val templatePosition = (piece as NetherFossilPieces.NetherFossilPiece).templatePosition()
            if (!level.getBiome(templatePosition).`is`(Biomes.SOUL_SAND_VALLEY)) {
                return@forEach
            }
            val boundingBox = piece.boundingBox
            val boneBlockCount = BlockPos.betweenClosed(AABB.of(boundingBox))
                .count { chunk.getBlockState(it).block == Blocks.BONE_BLOCK }
            if (boneBlockCount < 3) {
                return@forEach
            }
            val random = RandomSource.create(level.seed).forkPositional().at(boundingBox.center)
            if (random.nextFloat() < 0.5F) {
                val x = boundingBox.minX() + random.nextInt(boundingBox.xSpan)
                val y = boundingBox.minY()
                val z = boundingBox.minZ() + random.nextInt(boundingBox.zSpan)
                val blockPos = BlockPos(x, y, z)
                val writeableArea = AccessorChunkGenerator.invokeGetWritableArea(chunk)
                @Suppress("DEPRECATION")
                writeableArea.encapsulate(boundingBox)
                if (level.getBlockState(blockPos).isAir && writeableArea.isInside(blockPos)) {
                    level.setBlock(blockPos, Blocks.DRIED_GHAST.defaultBlockState().rotate(Rotation.getRandom(random)), Block.UPDATE_CLIENTS)
                }
            }
        }
    }
}

val upgradeDefinitions = mapOf(
    "1.21.5" to VersionUpgrade(
        overworldUpgrades = LevelUpgrade(
            decorationUpgrades = setOf(
                oakDecorationUpgrade,
                fanyOakDecorationUpgrade,
                birchDecorationUpgrade,
                darkOakDecorationUpgrade,
                cactusDecorationUpgrade
            ),
            featureUpgrades = setOf(
                VegetationFeatures.PATCH_DRY_GRASS,
                VegetationFeatures.PATCH_BUSH,
                VegetationFeatures.PATCH_FIREFLY_BUSH,
                VegetationFeatures.WILDFLOWERS_BIRCH_FOREST,
                VegetationFeatures.WILDFLOWERS_MEADOW,
                TreeFeatures.FALLEN_OAK_TREE,
                TreeFeatures.FALLEN_JUNGLE_TREE,
                TreeFeatures.FALLEN_SPRUCE_TREE,
                TreeFeatures.FALLEN_BIRCH_TREE,
                TreeFeatures.FALLEN_SUPER_BIRCH_TREE
            ),
            entityUpgrades = setOf(
                chickenVariantUpgrade,
                cowVariantUpgrade,
                pigVariantUpgrade,
                wolfVariantUpgrade
            )
        )
    ),
    "1.21.6" to VersionUpgrade(
        netherUpgrades = LevelUpgrade(
            structureUpgrades = setOf(
                netherFossilStructureUpgrade
            )
        )
    )
)

data class VersionUpgrade(
    val overworldUpgrades: LevelUpgrade = LevelUpgrade.EMPTY,
    val netherUpgrades: LevelUpgrade = LevelUpgrade.EMPTY,
    val endUpgrades: LevelUpgrade = LevelUpgrade.EMPTY,
) {
    fun merge(other: VersionUpgrade) =
        VersionUpgrade(
            overworldUpgrades.merge(other.overworldUpgrades),
            netherUpgrades.merge(other.netherUpgrades),
            endUpgrades.merge(other.endUpgrades)
        )
    
    fun isEmpty()=
        overworldUpgrades.isEmpty() &&
        netherUpgrades.isEmpty() &&
        endUpgrades.isEmpty()
    
    companion object {
        val EMPTY = VersionUpgrade()
    }
}

interface StructureUpgrade {
    val structures: Set<ResourceKey<Structure>>
    
    fun upgrade(chunk: LevelChunk, level: ServerLevel, structureStart: StructureStart)
}

data class LevelUpgrade(
    val decorationUpgrades: Set<DecorationUpgrade> = emptySet(),
    val featureUpgrades: Set<ResourceKey<ConfiguredFeature<*, *>>> = emptySet(),
    val entityUpgrades: Set<VariantEntityUpgrade> = emptySet(),
    val structureUpgrades: Set<StructureUpgrade> = emptySet(),
) {
    fun merge(other: LevelUpgrade) =
        LevelUpgrade(
            decorationUpgrades + other.decorationUpgrades,
            featureUpgrades + other.featureUpgrades,
            entityUpgrades + other.entityUpgrades
        )
    
    fun isEmpty() =
        decorationUpgrades.isEmpty() &&
        featureUpgrades.isEmpty() &&
        entityUpgrades.isEmpty() &&
        structureUpgrades.isEmpty()
    
    companion object {
        val EMPTY = LevelUpgrade()
    }
}

private fun upgradeChunk(
    level: ServerLevel,
    chunk: LevelChunk,
    generatorConfig: GeneratorConfig,
    biome2upgrades: Map<Biome, UpgradeSet>,
    structure2upgrades: Map<Structure, List<StructureUpgrade>>,
) {
    logger.info("Upgrading chunk ${level.dimension().location()} ${chunk.pos}")
    
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

private fun upgradeEntities(
    level: ServerLevel,
    chunk: Lazy<LevelChunk>,
    entities: List<CompoundTag>,
    entityUpgrades: Map<ResourceLocation, EntityUpgrade>
) {
    entities.forEach { entity ->
        entity.getString("id")
            .map { entityId -> entityUpgrades[ResourceLocation.parse(entityId)] }
            .ifPresent { upgrade -> upgrade.upgrade(chunk.value, entity, level) }
    }
}

data class UpgradeSet(
    val decorationUpgrades: Set<DecorationUpgrade>,
    val featureUpgrades: Set<ResourceKey<ConfiguredFeature<*, *>>>
)

sealed interface Upgrade

interface EntityUpgrade : Upgrade {
    val entityId: ResourceLocation
    fun upgrade(chunk: LevelChunk, entity: CompoundTag, level: ServerLevel)
}

abstract class VariantEntityUpgrade(
    override val entityId: ResourceLocation,
    protected val defaultVariant: ResourceLocation?
) : EntityUpgrade {
    abstract fun variant(pos: BlockPos, level: ServerLevel, biome: Holder<Biome>): ResourceLocation
    
    override fun upgrade(chunk: LevelChunk, entity: CompoundTag, level: ServerLevel) {
        val pos = entity.read("Pos", Vec3.CODEC)
            .map { BlockPos(it.x.toInt(), it.y.toInt(), it.z.toInt()) }
            .orElse(BlockPos.ZERO)
        val currentVariant = entity.read("variant", ResourceLocation.CODEC)
        if (currentVariant.isEmpty || currentVariant.get() == defaultVariant) {
            val updatedVariant = variant(pos, level, chunk.getNoiseBiome(pos.x, pos.y, pos.z))
            entity.store("variant", ResourceLocation.CODEC, updatedVariant)
        }
    }
}

class TemperatureVariantEntityUpgrade(
    entityId: ResourceLocation
) : VariantEntityUpgrade(entityId, TemperatureVariants.TEMPERATE) {
    override fun variant(pos: BlockPos, level: ServerLevel, biome: Holder<Biome>): ResourceLocation =
        when {
            biome.`is`(BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS) -> TemperatureVariants.COLD
            biome.`is`(BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS) -> TemperatureVariants.WARM
            else -> TemperatureVariants.TEMPERATE
        }
}

interface DecorationUpgrade : Upgrade {
    val features: Set<ResourceKey<ConfiguredFeature<*, *>>>
    fun test(level: Level, pos: BlockPos, blockState: BlockState): BlockPos?
    fun place(level: ServerLevel, random: RandomSource, testPos: BlockPos, resultPos: BlockPos, biome: Holder<Biome>)
}

const val WORLD_GEN_BLOCK_UPDATE_FLAGS = Block.UPDATE_NEIGHBORS or Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE

abstract class AbstractDecorationUpgrade : DecorationUpgrade {
    fun placeLeafLitter(
        configuredFeatureKey: ResourceKey<ConfiguredFeature<*, *>>,
        level: ServerLevel,
        random: RandomSource,
        pos: BlockPos
    ) {
        val configuredFeatureRegistry = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
        configuredFeatureRegistry.get(configuredFeatureKey).ifPresent { configuredFeatureHolder ->
            val treeConfiguration = configuredFeatureHolder.value().config() as TreeConfiguration
            val context = TreeDecorator.Context(
                level,
                { targetPos, targetState -> level.setBlock(targetPos, targetState, WORLD_GEN_BLOCK_UPDATE_FLAGS) },
                random,
                mutableSetOf(pos),
                mutableSetOf(),
                mutableSetOf()
            )
            treeConfiguration.decorators.forEach { decorator ->
                if (decorator is PlaceOnGroundDecorator) {
                    decorator.place(context)
                }
            }
        }
    }
}

abstract class PillarMatchingDecorationUpgrade : AbstractDecorationUpgrade() {
    protected abstract val pillarDefinition: List<PillarEntry>
    
    override fun test(level: Level, pos: BlockPos, blockState: BlockState): BlockPos? {
        var offset = 0
        pillarDefinition.forEach { definition ->
            repeat(definition.count) {
                val blockStateAtOffset = when (offset) {
                    0 -> blockState
                    else -> level.getBlockState(pos.below(offset))
                }
                if (definition.test(blockStateAtOffset)) {
                    offset++
                } else if (definition.required) {
                    return null
                }
            }
        }
        return pos.below(offset - 1)
    }
}

data class GeneratorConfig(
    val biomeSource: BiomeSource,
    val generationSettingsGetter: (Holder<Biome>) -> BiomeGenerationSettings,
    val featuresPerStep: Supplier<List<StepFeatureData>>
) {
    companion object {
        fun of(level: ServerLevel): GeneratorConfig {
            val biomeSource = (level.chunkSource.chunkMap as AccessorChunkMap).worldGenContext.generator.biomeSource
            val generationSettingsGetter = { holder: Holder<Biome> -> holder.value().generationSettings }
            val featuresPerStep = Suppliers.memoize<List<StepFeatureData>> {
                FeatureSorter.buildFeaturesPerStep(
                    biomeSource.possibleBiomes().toList(),
                    { holder: Holder<Biome> -> (generationSettingsGetter(holder) as BiomeGenerationSettings).features() },
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
