package com.microvoxel.camera;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.microvoxel.input.Input;

/**
 * Camara en primera persona.
 *
 * <p>
 * El movimiento depende del tiempo transcurrido ({@code delta} en segundos),
 * nunca de una velocidad por fotograma. No hay colisiones: la camara puede
 * atravesar los bloques para inspeccionar tuneles y cavidades desde dentro.
 * </p>
 *
 * <p>
 * La orientacion se parametriza con {@code yaw} (giro alrededor de Y) y
 * {@code pitch} (inclinacion alrededor de X), ambos en grados. El vector
 * frontal
 * se define como:
 * </p>
 * 
 * <pre>
 *   front = (cos(pitch) sin(yaw), sin(pitch), cos(pitch) cos(yaw))
 * </pre>
 * <p>
 * con {@code yaw = 0} mirando hacia +Z.
 * </p>
 */
public final class Camera {

    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float MAX_PITCH = 89.0f;
    private static final float FOV_DEGREES = 75.0f;
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 1000.0f;

    private final Vector3f position = new Vector3f(0.0f, 0.0f, 0.0f);
    private float yaw = 0.0f;
    private float pitch = 0.0f;

    /** Velocidad de desplazamiento en unidades de mundo por segundo. */
    private float movementSpeed = 8.0f;

    private final Vector3f front = new Vector3f(0, 0, 1);
    private final Vector3f right = new Vector3f(1, 0, 0);
    private final Vector3f up = new Vector3f(0, 1, 0);

    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();

    private final Vector3f tmp = new Vector3f();

    /**
     * Actualiza solo la orientacion a partir del raton.
     *
     * <p>Es la mitad de {@link #update} que sigue siendo responsabilidad de la
     * camara cuando el movimiento lo resuelve el {@code Player}.</p>
     *
     * @param input estado actual del raton
     */
    public void updateLook(Input input) {
        if (input.mouseDeltaX() != 0.0 || input.mouseDeltaY() != 0.0) {
            // Con la base derecha empleada (front = (cosP sinY, sinP, cosP cosY)),
            // el eje "derecha en pantalla" es front x arriba = (-X) cuando yaw = 0.
            // Por eso mover el raton a la derecha debe DISMINUIR el yaw para que la
            // vista gire hacia la derecha en pantalla. El signo vertical es el
            // correcto porque la coordenada Y del cursor crece hacia abajo.
            yaw -= (float) input.mouseDeltaX() * MOUSE_SENSITIVITY;
            pitch -= (float) input.mouseDeltaY() * MOUSE_SENSITIVITY;
            pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
            yaw = (float) (yaw % 360.0);
        }

        updateBasis();
    }

    /**
     * Camara libre: orientacion con el raton y desplazamiento en el espacio.
     *
     * <p>Es el modo sin colision: no consulta el mundo, de modo que sirve para
     * inspeccionar el terreno desde cualquier angulo. Con la colision activa el
     * movimiento lo resuelve {@code Player} y solo se llama a
     * {@link #updateLook}.</p>
     *
     * @param delta tiempo transcurrido en segundos
     * @param input estado actual del teclado y el raton
     */
    public void update(float delta, Input input) {
        updateLook(input);

        // --- Desplazamiento dependiente del tiempo ---
        float velocity = movementSpeed * delta;
        if (input.isKeyDown(GLFW_KEY_W)) {
            position.fma(velocity, front);
        }
        if (input.isKeyDown(GLFW_KEY_S)) {
            position.fma(-velocity, front);
        }
        if (input.isKeyDown(GLFW_KEY_D)) {
            position.fma(velocity, right);
        }
        if (input.isKeyDown(GLFW_KEY_A)) {
            position.fma(-velocity, right);
        }
        if (input.isKeyDown(GLFW_KEY_SPACE)) {
            position.y += velocity;
        }
        if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT)) {
            position.y -= velocity;
        }
    }

    private void updateBasis() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosPitch = (float) Math.cos(pitchRad);

        front.set(
                cosPitch * (float) Math.sin(yawRad),
                (float) Math.sin(pitchRad),
                cosPitch * (float) Math.cos(yawRad)).normalize();

        // right = front x mundoArriba
        front.cross(0.0f, 1.0f, 0.0f, right).normalize();
        // up = right x front (garantiza una base ortonormal)
        right.cross(front, up).normalize();
    }

    /** Coloca la camara mirando hacia un punto, recalculando yaw y pitch. */
    public void lookAt(Vector3f target) {
        tmp.set(target).sub(position);
        if (tmp.lengthSquared() < 1e-8f) {
            return;
        }
        tmp.normalize();
        pitch = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, tmp.y))));
        // Se limita igual que en update() para no volcar la camara cuando el
        // objetivo queda practicamente en la vertical.
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
        yaw = (float) Math.toDegrees(Math.atan2(tmp.x, tmp.z));
        updateBasis();
    }

    /** Matriz de vista derivada de la posicion y la orientacion actuales. */
    public Matrix4f viewMatrix() {
        updateBasis();
        return view.identity().lookAt(position, tmp.set(position).add(front), up);
    }

    /** Matriz de proyeccion en perspectiva para la relacion de aspecto dada. */
    public Matrix4f projectionMatrix(float aspect) {
        return projection.identity().perspective(
                (float) Math.toRadians(FOV_DEGREES), aspect, NEAR_PLANE, FAR_PLANE);
    }

    public Vector3f position() {
        return position;
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    /** Coloca la camara en un punto (por ejemplo, la posicion interpolada del jugador). */
    public void setPosition(Vector3f target) {
        position.set(target);
    }

    public Vector3f front() {
        updateBasis();
        return new Vector3f(front);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void setMovementSpeed(float speed) {
        this.movementSpeed = speed;
    }
}
