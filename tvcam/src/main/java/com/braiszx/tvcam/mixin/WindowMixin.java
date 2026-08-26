package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.tracy.TracyFrameCapturer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Justo antes de que el juego presente el frame en tu ventana: si era un frame de
 * camara lo mandamos a la otra ventana y devolvemos el tuyo a la tuya.
 *
 * <p>No cancelamos el metodo: ademas de presentar, se encarga del sondeo de
 * eventos del sistema, asi que saltarselo dejaria el juego sin responder a nada.
 */
@Mixin(Window.class)
public class WindowMixin {
    @Inject(method = "swapBuffers", at = @At("HEAD"))
    private void tvcam$divert(TracyFrameCapturer capturer, CallbackInfo ci) {
        CameraDirector.get().beforePresent();
    }
}
