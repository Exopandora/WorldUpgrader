package com.github.exopandora.worldupgrader

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus

fun ChunkPos.atRegionOffset(regionOffset: ChunkPos): ChunkPos =
    ChunkPos(x + (regionOffset.x shl 5), z + (regionOffset.z shl 5))

fun ServerLevel.chunkAt(
    chunkPos: ChunkPos,
    chunkStatus: ChunkStatus = ChunkStatus.FULL
): LevelChunk =
    getChunk(chunkPos.x, chunkPos.z, chunkStatus, true) as LevelChunk
