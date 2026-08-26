package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Autoprueba de la segunda ventana, para comprobar en un arranque sin tocar nada
 * que GLFW crea la ventana, que comparte el contexto de OpenGL con el juego y que
 * la textura del frame se pinta en ella.
 *
 * <p>Solo se activa con la variable de entorno {@code TVCAM_SELFTEST=1}.
 */
public final class SelfTest {
    private static int ticks;

    private SelfTest() {
    }

    public static void register() {
        TVCam.LOGGER.info("[selftest] activada");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            if (ticks == 40) {
                CameraDirector.get().window().open();
                TVCam.LOGGER.info("[selftest] ventana abierta: {}",
                        CameraDirector.get().window().isOpen());
                CameraDirector.get().setSelfTestPresenting(true);
            } else if (ticks == 60) {
                CameraDirector.get().window().requestCapture(
                        java.nio.file.Path.of(System.getProperty("tvcam.selftest.capture",
                                "tvcam-selftest.png")));
            } else if (ticks == 70) {
                TVCam.LOGGER.info("[selftest] espejo del frame: {}",
                        CameraDirector.get().selfTestMirror());
            } else if (ticks == 80) {
                TVCam.LOGGER.info("[selftest] frames presentados en la ventana: {}",
                        CameraDirector.get().selfTestPresentedFrames());
                CameraDirector.get().setSelfTestPresenting(false);
                CameraDirector.get().shutdown();
                TVCam.LOGGER.info("[selftest] terminada, cierro el juego");
                MinecraftClient.getInstance().scheduleStop();
            }
        });
    }
}
