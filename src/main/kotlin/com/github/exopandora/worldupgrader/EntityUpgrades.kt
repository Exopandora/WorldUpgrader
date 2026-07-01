package com.github.exopandora.worldupgrader

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.animal.TemperatureVariants
import net.minecraft.world.entity.animal.wolf.WolfVariants
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3

val chickenVariantUpgrade = TemperatureVariantEntityUpgrade(BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.CHICKEN))
val cowVariantUpgrade = TemperatureVariantEntityUpgrade(BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.COW))
val pigVariantUpgrade = TemperatureVariantEntityUpgrade(BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.PIG))

val wolfVariantUpgrade = object : VariantEntityUpgrade(
    entityId = BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.WOLF),
    defaultVariant = WolfVariants.PALE.identifier()
) {
    override fun variant(pos: BlockPos, level: ServerLevel, biome: Holder<Biome>): Identifier =
        when {
            biome.`is`(BiomeTags.IS_SAVANNA) -> WolfVariants.SPOTTED.identifier()
            biome.`is`(Biomes.GROVE) -> WolfVariants.SNOWY.identifier()
            biome.`is`(Biomes.OLD_GROWTH_PINE_TAIGA) -> WolfVariants.BLACK.identifier()
            biome.`is`(Biomes.SNOWY_TAIGA) -> WolfVariants.ASHEN.identifier()
            biome.`is`(BiomeTags.IS_JUNGLE) -> WolfVariants.RUSTY.identifier()
            biome.`is`(Biomes.FOREST) -> WolfVariants.WOODS.identifier()
            biome.`is`(Biomes.OLD_GROWTH_SPRUCE_TAIGA) -> WolfVariants.CHESTNUT.identifier()
            biome.`is`(BiomeTags.IS_BADLANDS) -> WolfVariants.STRIPED.identifier()
            else -> WolfVariants.PALE.identifier()
        }
}

interface EntityUpgrade : Upgrade {
    val entityId: Identifier
    fun upgrade(chunk: LevelChunk, entity: CompoundTag, level: ServerLevel)
}

abstract class VariantEntityUpgrade(
    override val entityId: Identifier,
    protected val defaultVariant: Identifier?
) : EntityUpgrade {
    abstract fun variant(pos: BlockPos, level: ServerLevel, biome: Holder<Biome>): Identifier
    
    override fun upgrade(chunk: LevelChunk, entity: CompoundTag, level: ServerLevel) {
        val pos = entity.read("Pos", Vec3.CODEC)
            .map { BlockPos(it.x.toInt(), it.y.toInt(), it.z.toInt()) }
            .orElse(BlockPos.ZERO)!!
        val currentVariant = entity.read("variant", Identifier.CODEC)
        if (currentVariant.isEmpty || currentVariant.get() == defaultVariant) {
            val updatedVariant = variant(pos, level, chunk.getNoiseBiome(pos.x, pos.y, pos.z))
            entity.store("variant", Identifier.CODEC, updatedVariant)
        }
    }
}

class TemperatureVariantEntityUpgrade(
    entityId: Identifier
) : VariantEntityUpgrade(entityId, TemperatureVariants.TEMPERATE) {
    override fun variant(pos: BlockPos, level: ServerLevel, biome: Holder<Biome>): Identifier =
        when {
            biome.`is`(BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS) -> TemperatureVariants.COLD
            biome.`is`(BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS) -> TemperatureVariants.WARM
            else -> TemperatureVariants.TEMPERATE
        }
}
