package com.braiszx.tvcam.camera;

/**
 * Una camara fija: una posicion y una orientacion guardadas en el mundo.
 */
public record CameraPoint(String name, double x, double y, double z, float yaw, float pitch) {
    public CameraPoint withName(String newName) {
        return new CameraPoint(newName, x, y, z, yaw, pitch);
    }
}
