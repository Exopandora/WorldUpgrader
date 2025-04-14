package com.github.exopandora.worldupgrader.mixin;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentEntitySectionManager.class)
public interface AccessorPersistentEntitySectionManager<T extends EntityAccess>
{
	@Accessor
	EntityPersistentStorage<T> getPermanentStorage();
}
