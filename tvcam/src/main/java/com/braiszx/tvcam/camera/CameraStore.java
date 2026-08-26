package com.braiszx.tvcam.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.braiszx.tvcam.TVCam;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda las camaras en config/tvcam.json, agrupadas por mundo/servidor, para que
 * al volver a entrar sigan donde las dejaste.
 */
public final class CameraStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, List<CameraPoint>>>() {}.getType();

    private final Path file;
    private Map<String, List<CameraPoint>> byWorld = new HashMap<>();

    public CameraStore() {
        this.file = FabricLoader.getInstance().getConfigDir().resolve("tvcam.json");
        load();
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, List<CameraPoint>> loaded = GSON.fromJson(reader, TYPE);
            if (loaded != null) {
                byWorld = loaded;
            }
        } catch (IOException | RuntimeException e) {
            TVCam.LOGGER.warn("No se pudo leer tvcam.json, empiezo de cero", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(byWorld, TYPE, writer);
            }
        } catch (IOException e) {
            TVCam.LOGGER.error("No se pudieron guardar las camaras", e);
        }
    }

    public List<CameraPoint> get(String worldKey) {
        return byWorld.computeIfAbsent(worldKey, k -> new ArrayList<>());
    }
}
