package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleRegionStorage.class)
public interface AccessorSimpleRegionStorage
{
	@Accessor
	IOWorker getWorker();
}
