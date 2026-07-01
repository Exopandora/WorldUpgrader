package com.github.exopandora.worldupgrader

import com.github.exopandora.worldupgrader.mixin.ChunkGeneratorAccessor
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.structure.BuiltinStructures
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilPieces
import net.minecraft.world.phys.AABB

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
                val writeableArea = ChunkGeneratorAccessor.invokeGetWritableArea(chunk)
                @Suppress("DEPRECATION")
                writeableArea.encapsulate(boundingBox)
                if (level.getBlockState(blockPos).isAir && writeableArea.isInside(blockPos)) {
                    level.setBlock(blockPos, Blocks.DRIED_GHAST.defaultBlockState().rotate(Rotation.getRandom(random)), Block.UPDATE_CLIENTS)
                }
            }
        }
    }
}

interface StructureUpgrade {
    val structures: Set<ResourceKey<Structure>>
    fun upgrade(chunk: LevelChunk, level: ServerLevel, structureStart: StructureStart)
}
