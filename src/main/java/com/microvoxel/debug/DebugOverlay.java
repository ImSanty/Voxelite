package com.microvoxel.debug;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;

import java.nio.ByteBuffer;

import org.joml.Matrix4f;
import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.system.MemoryUtil;

import com.microvoxel.render.Mesh;
import com.microvoxel.render.ShaderProgram;

/**
 * Superposicion de texto para las estadisticas de depuracion.
 *
 * <p>
 * El texto se genera con {@code stb_easy_font} (incluido en LWJGL-STB), un
 * tipo de letra vectorial embebido: <b>no requiere ningun archivo de fuente ni
 * textura</b>. STB produce quads de 16 bytes por vertice (posicion + color),
 * que
 * se suben a un VBO dinamico y se dibujan como triangulos con el shader de
 * depuracion y una proyeccion ortografica con el origen en la esquina superior
 * izquierda.
 * </p>
 *
 * <p>
 * Es la unica dependencia adicional (LWJGL-STB), documentada en el README.
 * </p>
 */
public final class DebugOverlay {

    /** Capacidad maxima en caracteres del texto por fotograma. */
    private static final int MAX_CHARS = 900;
    /** Bytes por caracter reservados por STB (margen holgado). */
    private static final int BYTES_PER_CHAR = 270;
    /** Cada quad son 4 vertices de 16 bytes. */
    private static final int QUAD_BYTES = 64;

    private static final float TEXT_SCALE = 1.5f;
    private static final float PADDING = 6.0f;

    private final ByteBuffer vertexBuffer;
    private final ByteBuffer colorBuffer;
    private final int maxQuads;

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f identity = new Matrix4f();

    private ShaderProgram shader;
    private Mesh textMesh;
    private boolean initialized;

    public DebugOverlay() {
        maxQuads = MAX_CHARS * 6;
        vertexBuffer = MemoryUtil.memAlloc(MAX_CHARS * BYTES_PER_CHAR);
        colorBuffer = MemoryUtil.memAlloc(4);
        colorBuffer.put(0, (byte) 0xEC);
        colorBuffer.put(1, (byte) 0xF2);
        colorBuffer.put(2, (byte) 0xF0);
        colorBuffer.put(3, (byte) 0xFF);
    }

    /** Debe llamarse con un contexto de OpenGL activo. */
    public void initialize() {
        shader = ShaderProgram.fromResources("debug", "debug.vert", "debug.frag");
        textMesh = Mesh.createTextMesh(maxQuads);
        initialized = true;
    }

    /** Dibuja el texto dado, alineado a la esquina superior izquierda. */
    public void render(String text, int framebufferWidth, int framebufferHeight) {
        if (!initialized || text == null || text.isEmpty()) {
            return;
        }

        vertexBuffer.clear();
        int quads = STBEasyFont.stb_easy_font_print(PADDING, PADDING, text, colorBuffer, vertexBuffer);
        if (quads <= 0) {
            return;
        }
        if (quads > maxQuads) {
            quads = maxQuads;
        }

        vertexBuffer.position(0);
        vertexBuffer.limit(quads * QUAD_BYTES);
        textMesh.updateTextVertices(vertexBuffer, quads);
        vertexBuffer.limit(vertexBuffer.capacity());

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Proyeccion ortografica con (0,0) en la esquina superior izquierda.
        projection.identity().setOrtho(0.0f, framebufferWidth, framebufferHeight, 0.0f, -1.0f, 1.0f);

        shader.bind();
        shader.setMatrix4("uProjection", projection);
        shader.setMatrix4("uView", identity.identity());
        shader.setFloat("uScale", TEXT_SCALE);
        shader.setVector4("uColor", 0.93f, 0.95f, 0.94f, 1.0f);

        textMesh.draw();

        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        ShaderProgram.unbind();
    }

    public void close() {
        if (textMesh != null) {
            textMesh.close();
            textMesh = null;
        }
        if (shader != null) {
            shader.close();
            shader = null;
        }
        MemoryUtil.memFree(vertexBuffer);
        MemoryUtil.memFree(colorBuffer);
        initialized = false;
    }
}
