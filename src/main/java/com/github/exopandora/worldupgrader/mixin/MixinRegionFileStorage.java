package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(RegionFileStorage.class)
public class MixinRegionFileStorage {
	@ModifyConstant(
		method = "getRegionFile",
		constant = @Constant(intValue = 256)
	)
	private int maxRegionFileCacheSize(int original) {
		return 8;
	}
}
