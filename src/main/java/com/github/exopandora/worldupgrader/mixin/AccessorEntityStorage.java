package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityStorage.class)
public interface AccessorEntityStorage
{
	@Accessor
	SimpleRegionStorage getSimpleRegionStorage();
}
