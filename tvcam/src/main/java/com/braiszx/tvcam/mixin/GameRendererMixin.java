package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La mano y el objeto que llevas no salen en la emision. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void tvcam$noHand(float tickDelta, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
        if (CameraDirector.get().isCameraFrame()) {
            ci.cancel();
        }
    }
}
