package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * El oido se queda con el jugador: sin esto el sonido saltaria entre tu posicion
 * y la de la camara en frames alternos.
 */
@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    @Inject(method = "updateListenerPosition", at = @At("HEAD"), cancellable = true)
    private void tvcam$keepEarsOnPlayer(Camera camera, CallbackInfo ci) {
        if (CameraDirector.get().isCameraFrame()) {
            ci.cancel();
        }
    }
}
