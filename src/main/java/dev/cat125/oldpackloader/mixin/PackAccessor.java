package dev.cat125.oldpackloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(Pack.class)
public interface PackAccessor {
    @Accessor("resources")
    Pack.ResourcesSupplier oldpackloader$resources();

    @Accessor("metadata")
    Pack.Metadata oldpackloader$metadata();
}
