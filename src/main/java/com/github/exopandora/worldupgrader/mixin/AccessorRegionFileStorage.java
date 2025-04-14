package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;

@Mixin(RegionFileStorage.class)
public interface AccessorRegionFileStorage
{
	@Invoker
	RegionFile invokeGetRegionFile(ChunkPos chunkPos) throws IOException;
}
