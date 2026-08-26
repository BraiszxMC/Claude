package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TVCam implements ClientModInitializer {
    public static final String MOD_ID = "tvcam";
    public static final Logger LOGGER = LoggerFactory.getLogger("TVCam");

    @Override
    public void onInitializeClient() {
        Keybinds.register();
        Commands.register();
        Hud.register();
        if ("1".equals(System.getenv("TVCAM_SELFTEST"))) {
            SelfTest.register();
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CameraDirector.get().shutdown());
        LOGGER.info("TVCam listo");
    }
}
