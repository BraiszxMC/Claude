package com.braiszx.tvcam.camera;

import com.braiszx.tvcam.TVCam;
import com.braiszx.tvcam.render.CameraWindow;
import com.braiszx.tvcam.render.FrameMirror;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * El realizador: sabe que camaras hay, cual esta al aire y decide que frames se
 * dibujan desde la camara en vez de desde los ojos del jugador.
 *
 * <p>El truco de esta beta es no renderizar el mundo dos veces por frame (que es
 * lo que cuesta la mitad de los FPS), sino alternar: un frame para tu ventana y
 * el siguiente para la ventana de camara. Cada ventana va a la mitad de FPS pero
 * el coste total es practicamente el de jugar normal.
 */
public final class CameraDirector {
    private static final CameraDirector INSTANCE = new CameraDirector();

    public static CameraDirector get() {
        return INSTANCE;
    }

    private final CameraStore store = new CameraStore();
    private final CameraWindow window = new CameraWindow();
    private final FrameMirror mirror = new FrameMirror();

    /** Indice de la camara al aire, o -1 si no hay ninguna. */
    private int active = -1;
    /** true mientras se dibuja un frame que va a la ventana de camara. */
    private boolean cameraFrame;
    private boolean hideGuiBackup;
    private long frameCounter;

    private CameraDirector() {
    }

    // ---------------------------------------------------------------- camaras

    private String worldKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        String place;
        if (client.getCurrentServerEntry() != null) {
            place = client.getCurrentServerEntry().address;
        } else if (client.getServer() != null) {
            place = "singleplayer/" + client.getServer().getSaveProperties().getLevelName();
        } else {
            place = "desconocido";
        }
        String dimension = client.world != null ? client.world.getRegistryKey().getValue().toString() : "?";
        return place + "#" + dimension;
    }

    public List<CameraPoint> cameras() {
        return store.get(worldKey());
    }

    /** Crea una camara donde esta el jugador ahora mismo, mirando a donde mira. */
    public CameraPoint addHere(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        List<CameraPoint> list = cameras();
        String finalName = (name == null || name.isBlank()) ? "Camara " + (list.size() + 1) : name;
        CameraPoint point = new CameraPoint(
                finalName,
                client.player.getX(),
                client.player.getEyeY(),
                client.player.getZ(),
                client.player.getYaw(),
                client.player.getPitch());
        list.add(point);
        store.save();
        return point;
    }

    public boolean remove(int index) {
        List<CameraPoint> list = cameras();
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        if (active >= list.size()) {
            active = list.isEmpty() ? -1 : list.size() - 1;
        }
        store.save();
        return true;
    }

    public void clear() {
        cameras().clear();
        active = -1;
        store.save();
    }

    public CameraPoint activeCamera() {
        List<CameraPoint> list = cameras();
        return (active >= 0 && active < list.size()) ? list.get(active) : null;
    }

    public int activeIndex() {
        return active;
    }

    /** Corta a la camara indicada (0-based). -1 apaga la emision. */
    public void cut(int index) {
        List<CameraPoint> list = cameras();
        if (index < 0) {
            active = -1;
            feedback(Text.literal("TVCam: emision parada").formatted(Formatting.GRAY));
            return;
        }
        if (index >= list.size()) {
            feedback(Text.literal("TVCam: no hay camara " + (index + 1)).formatted(Formatting.RED));
            return;
        }
        active = index;
        CameraPoint point = list.get(index);
        feedback(Text.literal("TVCam: al aire " + (index + 1) + " - " + point.name()).formatted(Formatting.AQUA));
        if (!window.isOpen()) {
            window.open();
        }
    }

    private void feedback(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(text, true);
        }
    }

    // ---------------------------------------------------------------- ventana

    public CameraWindow window() {
        return window;
    }

    public void toggleWindow() {
        if (window.isOpen()) {
            window.close();
        } else {
            window.open();
        }
    }

    // ----------------------------------------------------------------- frames

    /** true si el frame que se esta dibujando ahora va a la ventana de camara. */
    public boolean isCameraFrame() {
        return cameraFrame;
    }

    private boolean canBroadcast() {
        MinecraftClient client = MinecraftClient.getInstance();
        return window.isOpen()
                && activeCamera() != null
                && client.world != null
                && client.player != null
                && client.currentScreen == null;
    }

    /** Llamado al principio de cada frame del juego. */
    public void beginFrame() {
        frameCounter++;
        cameraFrame = canBroadcast() && mirror.hasFrame() && (frameCounter % 2L == 0L);
        if (cameraFrame) {
            MinecraftClient client = MinecraftClient.getInstance();
            hideGuiBackup = client.options.hudHidden;
            client.options.hudHidden = true;
        }
    }

    /** Llamado al final de cada frame del juego. */
    public void endFrame() {
        if (cameraFrame) {
            MinecraftClient.getInstance().options.hudHidden = hideGuiBackup;
            cameraFrame = false;
        }
    }

    /**
     * Llamado justo antes de que el juego presente el frame en tu ventana.
     *
     * <p>Si el frame era de camara lo mandamos a la ventana de camara y devolvemos
     * a tu ventana tu ultimo frame, para que no parpadee entre las dos vistas. Si
     * el frame era tuyo, lo guardamos para poder repetirlo en el siguiente.
     */
    public void beforePresent() {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) {
            return;
        }
        try {
            if (selfTestPresenting) {
                if (window.present(framebuffer)) {
                    selfTestFrames++;
                }
                return;
            }
            if (cameraFrame) {
                window.present(framebuffer);
                mirror.present();
            } else {
                mirror.capture(framebuffer);
            }
        } catch (RuntimeException e) {
            TVCam.LOGGER.error("Fallo presentando el frame de camara, cierro la ventana", e);
            window.close();
            mirror.close();
            active = -1;
        }
    }

    // ------------------------------------------------------------- autoprueba

    private boolean selfTestPresenting;
    private int selfTestFrames;

    public void setSelfTestPresenting(boolean value) {
        selfTestPresenting = value;
    }

    public int selfTestPresentedFrames() {
        return selfTestFrames;
    }

    /** Comprueba que la copia del frame (el espejo) funciona contra esta version. */
    public String selfTestMirror() {
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (framebuffer == null) {
            return "sin framebuffer";
        }
        try {
            mirror.capture(framebuffer);
            boolean presented = mirror.present();
            return "capturado=" + mirror.hasFrame() + " presentado=" + presented;
        } catch (RuntimeException e) {
            TVCam.LOGGER.error("[selftest] el espejo fallo", e);
            return "FALLO: " + e;
        }
    }

    public void shutdown() {
        window.close();
        mirror.close();
    }
}
