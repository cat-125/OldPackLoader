package dev.cat125.oldpackloader.mixin;

import dev.cat125.oldpackloader.PackConverter;
import dev.cat125.oldpackloader.PackVersion;
import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Pack.class)
public abstract class PackMixin {
    @Inject(method = "open", at = @At("RETURN"), cancellable = true)
    private void wrapPackResources(CallbackInfoReturnable<PackResources> callback) {
        var resources = PackConverter.convertResources(callback.getReturnValue());
        callback.setReturnValue(resources);
    }

    @Inject(method = "readMetaAndCreate", at = @At("RETURN"), cancellable = true)
    private static void hidePackWarning(CallbackInfoReturnable<Pack> callback) {
        Pack pack = callback.getReturnValue();
        if (pack == null || pack.getCompatibility() == PackCompatibility.COMPATIBLE) {
            return;
        }

        PackAccessor accessor = (PackAccessor) pack;
        try (PackResources resources = accessor.oldpackloader$resources().openPrimary(pack.location())) {
            PackVersion source = PackVersion.detect(resources);
            if (source.needsMigration() && pack.getCompatibility() == PackCompatibility.TOO_OLD) {
                Pack.Metadata metadata = accessor.oldpackloader$metadata();
                Pack.Metadata compatibleMetadata = new Pack.Metadata(
                    metadata.description(),
                    PackCompatibility.COMPATIBLE,
                    metadata.requestedFeatures(),
                    metadata.overlays()
                );
                callback.setReturnValue(new Pack(pack.location(), accessor.oldpackloader$resources(), compatibleMetadata, pack.selectionConfig()));
            }
        } catch (IOException ignored) {}
    }
}
