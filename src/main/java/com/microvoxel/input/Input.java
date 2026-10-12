package com.microvoxel.input;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

/**
 * Estado de entrada del usuario.
 *
 * <p>Mantiene dos vistas del teclado: la tecla pulsada en este instante (para el
 * movimiento continuo) y las teclas que acaban de pulsarse en el fotograma
 * actual (para alternar modos sin repeticiones). El raton acumula su
 * desplazamiento y se reinicia al final de cada fotograma.</p>
 *
 * <p>Las llamadas de GLFW se reenvian aqui desde {@code Window}. Esta clase no
 * conoce OpenGL.</p>
 */
public final class Input {

    private final boolean[] down = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] pressedThisFrame = new boolean[GLFW_KEY_LAST + 1];

    private double mouseDeltaX;
    private double mouseDeltaY;

    /** Aplica un evento de tecla de GLFW. */
    public void onKey(int key, int action) {
        if (key < 0 || key > GLFW_KEY_LAST) {
            return;
        }
        if (action == GLFW_PRESS) {
            if (!down[key]) {
                pressedThisFrame[key] = true;
            }
            down[key] = true;
        } else if (action == GLFW_RELEASE) {
            down[key] = false;
        } else if (action == GLFW_REPEAT) {
            // La repeticion no genera un nuevo flanco de subida.
            down[key] = true;
        }
    }

    /** Acumula el movimiento del raton mientras no se ignora la entrada. */
    public void onMouseMove(double dx, double dy) {
        mouseDeltaX += dx;
        mouseDeltaY += dy;
    }

    /** {@code true} mientras la tecla permanece pulsada. */
    public boolean isKeyDown(int key) {
        return key >= 0 && key <= GLFW_KEY_LAST && down[key];
    }

    /** {@code true} solo en el fotograma en que la tecla paso de suelta a pulsada. */
    public boolean wasKeyPressed(int key) {
        return key >= 0 && key <= GLFW_KEY_LAST && pressedThisFrame[key];
    }

    public double mouseDeltaX() {
        return mouseDeltaX;
    }

    public double mouseDeltaY() {
        return mouseDeltaY;
    }

    /** Limpia los flancos y el desplazamiento del raton. Se llama al cerrar el fotograma. */
    public void endFrame() {
        java.util.Arrays.fill(pressedThisFrame, false);
        mouseDeltaX = 0;
        mouseDeltaY = 0;
    }
}
