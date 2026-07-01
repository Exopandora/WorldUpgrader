package com.github.exopandora.worldupgrader

import com.github.exopandora.worldupgrader.PillarEntry.Companion.optional
import com.github.exopandora.worldupgrader.PillarEntry.Companion.required
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.features.TreeFeatures
import net.minecraft.data.worldgen.features.VegetationFeatures
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration
import net.minecraft.world.level.levelgen.feature.treedecorators.PlaceOnGroundDecorator
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator

val oakDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
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

val fanyOakDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
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

val birchDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
    override val features = setOf(
        TreeFeatures.BIRCH_LEAF_LITTER,
        TreeFeatures.BIRCH_BEES_0002_LEAF_LITTER,
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

val darkOakDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
    override val features = setOf(
        TreeFeatures.DARK_OAK_LEAF_LITTER,
    )
    
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

val cactusDecorationUpgrade = object : PillarMatchingDecorationUpgrade() {
    override val features = setOf(
        VegetationFeatures.CACTUS,
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

interface DecorationUpgrade : Upgrade {
    val features: Set<ResourceKey<ConfiguredFeature<*, *>>>
    fun test(level: Level, pos: BlockPos, blockState: BlockState): BlockPos?
    fun place(level: ServerLevel, random: RandomSource, testPos: BlockPos, resultPos: BlockPos, biome: Holder<Biome>)
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
