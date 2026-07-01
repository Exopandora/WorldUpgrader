package com.github.exopandora.worldupgrader

import net.minecraft.data.worldgen.features.TreeFeatures
import net.minecraft.data.worldgen.features.VegetationFeatures
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature

val upgradeIndex = mapOf(
    "1.21.5" to VersionUpgrade(
        Level.OVERWORLD to LevelUpgrade(
            decorationUpgrades = setOf(
                oakDecorationUpgrade,
                fanyOakDecorationUpgrade,
                birchDecorationUpgrade,
                darkOakDecorationUpgrade,
                cactusDecorationUpgrade,
            ),
            featureUpgrades = setOf(
                VegetationFeatures.DRY_GRASS,
                VegetationFeatures.BUSH,
                VegetationFeatures.FIREFLY_BUSH,
                VegetationFeatures.WILDFLOWER,
                TreeFeatures.FALLEN_OAK_TREE,
                TreeFeatures.FALLEN_JUNGLE_TREE,
                TreeFeatures.FALLEN_SPRUCE_TREE,
                TreeFeatures.FALLEN_BIRCH_TREE,
                TreeFeatures.FALLEN_SUPER_BIRCH_TREE,
            ),
            entityUpgrades = setOf(
                chickenVariantUpgrade,
                cowVariantUpgrade,
                pigVariantUpgrade,
                wolfVariantUpgrade,
            )
        )
    ),
    "1.21.6" to VersionUpgrade(
        Level.NETHER to LevelUpgrade(
            structureUpgrades = setOf(
                netherFossilStructureUpgrade,
            )
        )
    )
)

data class VersionUpgrade(
    private val dimension2upgrades: Map<ResourceKey<Level>, LevelUpgrade>
) {
    constructor(vararg pairs: Pair<ResourceKey<Level>, LevelUpgrade>) : this(mapOf(*pairs))
    
    operator fun get(dimensionKey: ResourceKey<Level>): LevelUpgrade? =
        dimension2upgrades[dimensionKey]
    
    fun merge(other: VersionUpgrade): VersionUpgrade {
        val result = dimension2upgrades.toMutableMap()
        other.dimension2upgrades.forEach { (key, upgrade) ->
            result.compute(key) { _, v ->
                v?.merge(upgrade) ?: upgrade
            }
        }
        return VersionUpgrade(result)
    }
    
    fun isEmpty() =
        dimension2upgrades.isEmpty() || dimension2upgrades.values.all { it.isEmpty() }
    
    companion object {
        val EMPTY = VersionUpgrade()
    }
}

data class LevelUpgrade(
    val decorationUpgrades: Set<DecorationUpgrade> = emptySet(),
    val featureUpgrades: Set<ResourceKey<ConfiguredFeature<*, *>>> = emptySet(),
    val entityUpgrades: Set<EntityUpgrade> = emptySet(),
    val structureUpgrades: Set<StructureUpgrade> = emptySet(),
) {
    fun merge(other: LevelUpgrade) =
        LevelUpgrade(
            decorationUpgrades + other.decorationUpgrades,
            featureUpgrades + other.featureUpgrades,
            entityUpgrades + other.entityUpgrades,
        )
    
    fun isEmpty() =
        decorationUpgrades.isEmpty() &&
            featureUpgrades.isEmpty() &&
            entityUpgrades.isEmpty() &&
            structureUpgrades.isEmpty()
}

sealed interface Upgrade
