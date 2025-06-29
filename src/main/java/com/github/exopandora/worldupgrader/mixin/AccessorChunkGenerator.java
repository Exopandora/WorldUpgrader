package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkGenerator.class)
public interface AccessorChunkGenerator
{
	@Invoker
	static BoundingBox invokeGetWritableArea(ChunkAccess chunkAccess)
	{
		throw new AssertionError();
	}
}
