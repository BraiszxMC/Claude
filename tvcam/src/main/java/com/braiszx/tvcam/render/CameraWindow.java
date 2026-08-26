package com.braiszx.tvcam.render;

import com.braiszx.tvcam.TVCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * La segunda ventana: una ventana GLFW propia que comparte el contexto de OpenGL
 * con la del juego (asi puede leer directamente la textura donde Minecraft acaba
 * de dibujar el frame) y se limita a pintar esa textura a pantalla completa.
 *
 * <p>Compartir el contexto es lo que hace que esto sea barato: no copiamos pixeles
 * por la CPU ni renderizamos el mundo otra vez, solo presentamos en otra ventana
 * una textura que ya existe en la GPU.
 */
public final class CameraWindow {
    private static final String VERTEX_SHADER = """
            #version 150 core
            uniform vec2 uScale;
            out vec2 vUv;
            void main() {
                // Triangulo que cubre toda la pantalla, sin buffers de vertices.
                vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                vUv = corner;
                gl_Position = vec4((corner * 2.0 - 1.0) * uScale, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150 core
            uniform sampler2D uTexture;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(texture(uTexture, vUv).rgb, 1.0);
            }
            """;

    private long handle;
    private GLCapabilities capabilities;
    private int program;
    private int vao;
    private int uScaleLocation;
    private int uTextureLocation;
    private Path pendingCapture;

    public boolean isOpen() {
        return handle != 0L;
    }

    public long handle() {
        return handle;
    }

    // ------------------------------------------------------------------ ciclo

    public void open() {
        if (isOpen()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        long mainWindow = client.getWindow().getHandle();

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        // Que aparecer no le robe el foco al juego.
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);

        handle = GLFW.glfwCreateWindow(1280, 720, "TVCam", 0L, mainWindow);
        if (handle == 0L) {
            TVCam.LOGGER.error("GLFW no pudo crear la ventana de camara");
            return;
        }

        // Si el usuario cierra la ventana a mano, la soltamos nosotros.
        GLFW.glfwSetWindowCloseCallback(handle, w -> close());

        GLCapabilities mainCapabilities = GL.getCapabilities();
        GLFW.glfwMakeContextCurrent(handle);
        try {
            capabilities = GL.createCapabilities();
            GLFW.glfwSwapInterval(0);
            buildPipeline();
        } catch (RuntimeException e) {
            TVCam.LOGGER.error("No se pudo preparar OpenGL en la ventana de camara", e);
            GLFW.glfwMakeContextCurrent(mainWindow);
            GL.setCapabilities(mainCapabilities);
            close();
            return;
        }
        GLFW.glfwMakeContextCurrent(mainWindow);
        GL.setCapabilities(mainCapabilities);

        TVCam.LOGGER.info("Ventana de camara abierta");
    }

    public void close() {
        if (!isOpen()) {
            return;
        }
        long window = handle;
        handle = 0L;
        MinecraftClient client = MinecraftClient.getInstance();
        long mainWindow = client.getWindow().getHandle();
        GLCapabilities mainCapabilities = GL.getCapabilities();
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.setCapabilities(capabilities);
            if (program != 0) {
                GL20C.glDeleteProgram(program);
                program = 0;
            }
            if (vao != 0) {
                GL30C.glDeleteVertexArrays(vao);
                vao = 0;
            }
        } catch (RuntimeException e) {
            TVCam.LOGGER.warn("Limpiando la ventana de camara", e);
        } finally {
            GLFW.glfwMakeContextCurrent(mainWindow);
            GL.setCapabilities(mainCapabilities);
            capabilities = null;
            GLFW.glfwDestroyWindow(window);
        }
        TVCam.LOGGER.info("Ventana de camara cerrada");
    }

    // --------------------------------------------------------------- pipeline

    private void buildPipeline() {
        int vertex = compile(GL20C.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragment = compile(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vertex);
        GL20C.glAttachShader(program, fragment);
        GL20C.glLinkProgram(program);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetProgramInfoLog(program);
            throw new IllegalStateException("No se pudo enlazar el shader de la ventana: " + log);
        }
        GL20C.glDetachShader(program, vertex);
        GL20C.glDetachShader(program, fragment);
        GL20C.glDeleteShader(vertex);
        GL20C.glDeleteShader(fragment);

        uScaleLocation = GL20C.glGetUniformLocation(program, "uScale");
        uTextureLocation = GL20C.glGetUniformLocation(program, "uTexture");

        // En core profile hace falta un VAO aunque no usemos atributos, y los VAO
        // no se comparten entre contextos: este es solo de esta ventana.
        vao = GL30C.glGenVertexArrays();
    }

    private int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("No se pudo compilar el shader de la ventana: " + log);
        }
        return shader;
    }

    // ------------------------------------------------------------ presentacion

    /**
     * Coge la textura donde Minecraft acaba de dibujar el frame y la pinta en la
     * ventana de camara.
     *
     * @return true si la imagen se presento en la otra ventana.
     */
    /**
     * Pide guardar en un PNG lo siguiente que se pinte en la ventana de camara.
     * Se usa en la autoprueba para comprobar que la imagen llega bien.
     */
    public void requestCapture(Path path) {
        pendingCapture = path;
    }

    private void writeCapture(int width, int height) {
        Path path = pendingCapture;
        pendingCapture = null;
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            GL11C.glReadPixels(0, 0, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            STBImageWrite.stbi_flip_vertically_on_write(true);
            boolean ok = STBImageWrite.stbi_write_png(path.toString(), width, height, 4, pixels, width * 4);
            TVCam.LOGGER.info("[selftest] captura de la ventana en {}: {}", path, ok);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    public boolean present(Framebuffer source) {
        if (!isOpen()) {
            return false;
        }
        if (!(source.getColorAttachment() instanceof GlTexture texture)) {
            return false;
        }
        if (GLFW.glfwWindowShouldClose(handle)) {
            close();
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        long mainWindow = client.getWindow().getHandle();

        // Obligatorio antes de leer desde el otro contexto una textura que acaba
        // de escribir este: sin esto el otro contexto puede ver basura.
        GL11C.glFlush();

        GLCapabilities mainCapabilities = GL.getCapabilities();
        GLFW.glfwMakeContextCurrent(handle);
        GL.setCapabilities(capabilities);
        try {
            int[] width = new int[1];
            int[] height = new int[1];
            GLFW.glfwGetFramebufferSize(handle, width, height);
            if (width[0] <= 0 || height[0] <= 0) {
                return false;
            }

            GL11C.glViewport(0, 0, width[0], height[0]);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);

            // Barras negras en vez de deformar la imagen si las proporciones no cuadran.
            float sourceAspect = (float) source.textureWidth / (float) source.textureHeight;
            float targetAspect = (float) width[0] / (float) height[0];
            float scaleX = 1.0f;
            float scaleY = 1.0f;
            if (sourceAspect > targetAspect) {
                scaleY = targetAspect / sourceAspect;
            } else {
                scaleX = sourceAspect / targetAspect;
            }

            GL20C.glUseProgram(program);
            GL20C.glUniform2f(uScaleLocation, scaleX, scaleY);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture.getGlId());
            GL20C.glUniform1i(uTextureLocation, 0);

            GL30C.glBindVertexArray(vao);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            GL30C.glBindVertexArray(0);
            GL20C.glUseProgram(0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);

            if (pendingCapture != null) {
                writeCapture(width[0], height[0]);
            }

            GLFW.glfwSwapBuffers(handle);
            return true;
        } finally {
            GLFW.glfwMakeContextCurrent(mainWindow);
            GL.setCapabilities(mainCapabilities);
        }
    }
}
