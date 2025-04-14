package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface AccessorMinecraftServer
{
	@Accessor
	LevelStorageSource.LevelStorageAccess getStorageSource();
	
	@Accessor
	void setWaitingForNextTick(boolean waiting);
}
