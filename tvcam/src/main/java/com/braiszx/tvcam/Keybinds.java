package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Teclas del numpad, igual que una mesa de realizacion: 1-9 cortan a cada camara,
 * 0 corta la emision.
 */
public final class Keybinds {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(TVCam.MOD_ID, "tvcam"));

    private static final KeyBinding[] CUT = new KeyBinding[9];
    private static KeyBinding stop;
    private static KeyBinding addCamera;
    private static KeyBinding toggleWindow;

    private Keybinds() {
    }

    public static void register() {
        int[] numpad = {
                GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3,
                GLFW.GLFW_KEY_KP_4, GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_KP_6,
                GLFW.GLFW_KEY_KP_7, GLFW.GLFW_KEY_KP_8, GLFW.GLFW_KEY_KP_9
        };
        for (int i = 0; i < CUT.length; i++) {
            CUT[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.tvcam.cut." + (i + 1), InputUtil.Type.KEYSYM, numpad[i], CATEGORY));
        }
        stop = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tvcam.stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_0, CATEGORY));
        addCamera = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tvcam.add", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_MULTIPLY, CATEGORY));
        toggleWindow = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tvcam.window", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_DECIMAL, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CameraDirector director = CameraDirector.get();
            for (int i = 0; i < CUT.length; i++) {
                while (CUT[i].wasPressed()) {
                    director.cut(i);
                }
            }
            while (stop.wasPressed()) {
                director.cut(-1);
            }
            while (addCamera.wasPressed()) {
                director.addHere(null);
            }
            while (toggleWindow.wasPressed()) {
                director.toggleWindow();
            }
        });
    }
}
