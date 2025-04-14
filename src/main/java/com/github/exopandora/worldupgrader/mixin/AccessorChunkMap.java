package com.github.exopandora.worldupgrader.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface AccessorChunkMap
{
	@Accessor
	WorldGenContext getWorldGenContext();
	
	@Accessor
	Long2ObjectLinkedOpenHashMap<ChunkHolder> getUpdatingChunkMap();
}
