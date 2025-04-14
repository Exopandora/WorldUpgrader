package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockableEventLoop.class)
public interface AccessorBlockableEventLoop
{
	@Invoker
	void invokeRunAllTasks();
}
