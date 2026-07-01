package com.github.exopandora.worldupgrader

import net.minecraft.data.worldgen.features.TreeFeatures
import net.minecraft.data.worldgen.features.VegetationFeatures
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature

val upgradeIndex = mapOf(
    "1.21.5" to VersionUpgrade(
        overworldUpgrades = LevelUpgrade(
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
        netherUpgrades = LevelUpgrade(
            structureUpgrades = setOf(
                netherFossilStructureUpgrade,
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
            endUpgrades.merge(other.endUpgrades),
        )

    fun isEmpty()=
        overworldUpgrades.isEmpty() &&
            netherUpgrades.isEmpty() &&
            endUpgrades.isEmpty()

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

    companion object {
        val EMPTY = LevelUpgrade()
    }
}

sealed interface Upgrade
