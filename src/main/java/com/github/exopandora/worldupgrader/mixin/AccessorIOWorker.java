package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IOWorker.class)
public interface AccessorIOWorker
{
	@Accessor
	RegionFileStorage getStorage();
}
