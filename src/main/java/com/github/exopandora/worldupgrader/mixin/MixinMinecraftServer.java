package com.github.exopandora.worldupgrader.mixin;

import com.github.exopandora.worldupgrader.DecorationUpgradeKt;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {
	@Inject(
		at = @At(
			value = "INVOKE",
			target = "net/minecraft/server/MinecraftServer.initServer()Z",
			shift = Shift.AFTER
		),
		method = "runServer"
	)
	private void runServer(CallbackInfo ci) {
		// noinspection DataFlowIssue
		DecorationUpgradeKt.upgrade((MinecraftServer) (Object) this);
	}
	
//	@Inject(
//		at = @At(
//			value = "INVOKE",
//			target = "net/minecraft/server/MinecraftServer.waitUntilNextTick()V",
//			shift = Shift.AFTER,
//			ordinal = 1
//		),
//		method = "prepareLevels"
//	)
//	private void prepareLevels(CallbackInfo ci) {
//		DecorationUpgradeKt.upgrade((MinecraftServer) (Object) this);
//	}
}
