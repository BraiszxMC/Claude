package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marca donde empieza y acaba cada frame, para saber a quien pertenece. */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "render(Z)V", at = @At("HEAD"))
    private void tvcam$beginFrame(boolean tick, CallbackInfo ci) {
        CameraDirector.get().beginFrame();
    }

    @Inject(method = "render(Z)V", at = @At("RETURN"))
    private void tvcam$endFrame(boolean tick, CallbackInfo ci) {
        CameraDirector.get().endFrame();
    }
}
