package com.microvoxel.window;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_SAMPLES;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import com.microvoxel.input.Input;

import java.nio.IntBuffer;

/**
 * Ventana GLFW y contexto de OpenGL 3.3 Core.
 *
 * <p>Responsabilidades: inicializar GLFW, crear la ventana con un contexto de
 * OpenGL 3.3 Core, instalar las llamadas de entrada y ofrecer las operaciones
 * basicas del bucle (intercambio de buffers, sondeo de eventos, sondeo del
 * tamano del framebuffer) junto a una limpieza ordenada de los recursos
 * nativos.</p>
 *
 * <p>El cursor se captura para el control en primera persona; {@code Tab} lo
 * libera o vuelve a capturar. Al liberarse, el raton no se ignora, de modo que
 * la camara solo gira cuando el cursor esta capturado.</p>
 *
 * <p>La sincronizacion vertical esta desactivada ({@link #SWAP_INTERVAL}), de modo
 * que el bucle no tiene tope artificial de FPS. El gasto de CPU o GPU que ello
 * implica es deliberado: la simulacion avanza a 20 Hz y se dibuja interpolada, no
 * ligada a los fotogramas.</p>
 */
public final class Window {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    /**
     * Intervalo de intercambio de buffers: {@code 0} desactiva la sincronizacion
     * vertical (sin tope de FPS) y {@code 1} la activa (limitado a la frecuencia
     * del monitor).
     *
     * <p>Sin sincronizacion el bucle dibuja tan rapido como pueda: mas FPS y menos
     * latencia de entrada, pero tambien mas gasto de CPU/GPU y posible desgarro de
     * imagen. La simulacion no depende de esto: sigue avanzando a 20 Hz con dibujo
     * interpolado, asi que el movimiento se ve igual de fluido a 60 o a 600 FPS.</p>
     */
    private static final int SWAP_INTERVAL = 0;

    private final String title;
    private long handle;
    private boolean initialized;

    /** Indica si el raton esta capturado (control de camara activo). */
    private boolean cursorCaptured = true;

    /** Ultima posicion del cursor, para calcular el delta. */
    private double lastCursorX;
    private double lastCursorY;
    private boolean hasLastCursor;

    private final Input input;

    private int framebufferWidth = DEFAULT_WIDTH;
    private int framebufferHeight = DEFAULT_HEIGHT;

    public Window(String title, Input input) {
        this.title = title;
        this.input = input;
    }

    public void create() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("No se pudo inicializar GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        // Necesario en macOS para obtener el perfil Core de 3.3.
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);

        handle = glfwCreateWindow(DEFAULT_WIDTH, DEFAULT_HEIGHT, title, 0L, 0L);
        if (handle == 0L) {
            glfwTerminate();
            throw new IllegalStateException(
                    "No se pudo crear la ventana de OpenGL 3.3 Core. Comprueba que la GPU "
                            + "y los controladores soporten OpenGL 3.3 o superior.");
        }
        initialized = true;

        installInputCallbacks();

        // Centra la ventana en el monitor principal.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetWindowSize(handle, w, h);
            GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (mode != null) {
                glfwSetWindowPos(handle,
                        (mode.width() - w.get(0)) / 2,
                        (mode.height() - h.get(0)) / 2);
            }
        }

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(SWAP_INTERVAL);
        glfwShowWindow(handle);

        // Carga las funciones de OpenGL de la version que expone el contexto.
        GL.createCapabilities();

        setCursorCaptured(true);
        refreshFramebufferSize();
    }

    private void installInputCallbacks() {
        glfwSetKeyCallback(handle, (window, key, scancode, action, mods) ->
                input.onKey(key, action));

        glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
            if (!cursorCaptured) {
                hasLastCursor = false;
                return;
            }
            if (hasLastCursor) {
                input.onMouseMove(xpos - lastCursorX, ypos - lastCursorY);
            }
            lastCursorX = xpos;
            lastCursorY = ypos;
            hasLastCursor = true;
        });
    }

    /** Activa o desactiva la captura del cursor. */
    public void setCursorCaptured(boolean captured) {
        this.cursorCaptured = captured;
        this.hasLastCursor = false;
        glfwSetInputMode(handle, GLFW_CURSOR, captured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }

    public boolean isCursorCaptured() {
        return cursorCaptured;
    }

    public void toggleCursorCapture() {
        setCursorCaptured(!cursorCaptured);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void requestClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    public void refreshFramebufferSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, w, h);
            if (w.get(0) > 0 && h.get(0) > 0) {
                framebufferWidth = w.get(0);
                framebufferHeight = h.get(0);
            }
        }
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }

    public int width() {
        return framebufferWidth;
    }

    public int height() {
        return framebufferHeight;
    }

    public long handle() {
        return handle;
    }

    public void setTitle(String newTitle) {
        glfwSetWindowTitle(handle, newTitle);
    }

    /** Libera los recursos nativos de GLFW. */
    public void destroy() {
        if (handle != 0L) {
            glfwFreeCallbacks(handle);
            glfwDestroyWindow(handle);
            handle = 0L;
        }
        glfwSetErrorCallback(null);
        if (initialized) {
            glfwTerminate();
            initialized = false;
        }
    }
}
