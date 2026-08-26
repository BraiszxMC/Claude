package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraPoint;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/** Un aviso pequeno en tu ventana (no en la emision) de que camara esta al aire. */
public final class Hud {
    private static final int RED = 0xFFFF5555;

    private Hud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            CameraDirector director = CameraDirector.get();
            if (director.isCameraFrame()) {
                return;
            }
            CameraPoint camera = director.activeCamera();
            if (camera == null || !director.window().isOpen()) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            String label = "● AL AIRE  CAM " + (director.activeIndex() + 1) + " · " + camera.name();
            context.drawTextWithShadow(client.textRenderer, label, 6, 6, RED);
        });
    }
}
