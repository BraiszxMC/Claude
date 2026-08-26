package com.braiszx.tvcam.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

/**
 * Copia de seguridad del ultimo frame que se dibujo desde tus ojos.
 *
 * <p>En los frames de camara el juego dibuja la vista de la camara en su unico
 * framebuffer, asi que si no hicieramos nada tu ventana parpadearia entre las dos
 * vistas. Guardamos aqui tu ultimo frame y lo volvemos a pintar en tu ventana
 * justo antes de que el juego la presente.
 */
public final class FrameMirror {
    private SimpleFramebuffer buffer;
    private int width;
    private int height;

    public boolean hasFrame() {
        return buffer != null;
    }

    /** Guarda el frame actual (el tuyo) para poder repetirlo en el siguiente. */
    public void capture(Framebuffer source) {
        GpuTexture texture = source.getColorAttachment();
        if (texture == null) {
            return;
        }
        ensureSize(source.textureWidth, source.textureHeight);
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(texture, buffer.getColorAttachment(), 0, 0, 0, 0, 0, width, height);
    }

    /** Vuelve a pintar tu ultimo frame en tu ventana. */
    public boolean present() {
        if (buffer == null || buffer.getColorAttachment() == null) {
            return false;
        }
        buffer.blitToScreen();
        return true;
    }

    private void ensureSize(int newWidth, int newHeight) {
        if (buffer != null && width == newWidth && height == newHeight) {
            return;
        }
        close();
        width = newWidth;
        height = newHeight;
        buffer = new SimpleFramebuffer("TVCam mirror", newWidth, newHeight, false);
    }

    public void close() {
        if (buffer != null) {
            buffer.delete();
            buffer = null;
        }
    }
}
