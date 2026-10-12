package com.microvoxel.phys;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import java.util.List;

import org.joml.Vector3f;

import com.microvoxel.input.Input;
import com.microvoxel.world.World;

/**
 * Jugador con fisica y colision.
 *
 * <p>
 * Puerto de {@code Entity} y {@code Player} de rd-161348 en lo que respecta al
 * movimiento: aceleracion relativa a la orientacion, gravedad, salto solo con
 * los pies en el suelo, frenado por rozamiento, y un {@link #move} que resuelve
 * el desplazamiento contra los cubos solidos del mundo.
 * </p>
 *
 * <p>
 * <b>Paso fijo.</b> El original integra la fisica en el <i>tick</i> del juego
 * (20 Hz) y sus constantes estan calibradas para ese paso; con un delta variable
 * el salto y el rozamiento cambiarian de sensacion segun los FPS. Por eso
 * {@link #update} acumula el tiempo real y ejecuta pasos de
 * {@value #STEP_SECONDS} segundos, como maximo
 * {@value #MAX_STEPS_PER_UPDATE} por fotograma (para no entrar en una espiral de
 * simulacion si un fotograma se alarga).
 * </p>
 *
 * <p>
 * <b>Posicion.</b> {@code eyeX/eyeY/eyeZ} es la posicion de los ojos, que es la
 * que sigue la camara; los pies quedan {@value #EYE_HEIGHT} unidades por debajo.
 * La caja envolvente mide {@value #WIDTH} x {@value #HEIGHT} x {@value #WIDTH}
 * unidades y esta centrada horizontalmente en la posicion.
 * </p>
 *
 * <p>
 * <b>Interpolacion del renderizado.</b> La simulacion avanza a 20 Hz, pero el
 * juego dibuja a la frecuencia que pueda. Sin mas, la camara solo cambiaria de
 * sitio uno de cada tres fotogramas a 60 FPS y el movimiento se veria a saltos.
 * Por eso se conserva la posicion anterior a cada paso y se ofrece
 * {@link #interpolatedEyePosition()}, que dibuja el punto intermedio en funcion
 * del tiempo pendiente: es el equivalente de {@code prevX/prevY/prevZ} y
 * {@code partialTicks} del original, que resolvia el mismo desajuste.
 * </p>
 *
 * <p>
 * <b>Diferencia con el original.</b> Alli la base de la camara tiene el eje
 * frontal en {@code -Z} para {@code yRotation = 0}; aqui {@code yaw = 0} mira
 * hacia {@code +Z} y el eje "derecha en pantalla" es {@code (-cos yaw, 0,
 * sin yaw)}, asi que se usa esa base para {@link #moveRelative}. El resto de
 * constantes son las del original.
 * </p>
 */
public final class Player {

    /**
     * Duracion de un paso de simulacion, en segundos (20 Hz, como el tick
     * original). Es {@code double} a proposito: con {@code 0.05f} el valor real
     * (0.050000000745...) hacia que un delta de 0,25 s produjera 4 pasos en lugar
     * de 5, y el numero de pasos por fotograma dejaba de ser predecible.
     */
    public static final double STEP_SECONDS = 0.05;
    /** Numero maximo de pasos que se ejecutan en un solo fotograma. */
    public static final int MAX_STEPS_PER_UPDATE = 5;

    /** Altura de los ojos sobre los pies. */
    public static final float EYE_HEIGHT = 1.62f;
    /** Ancho de la caja del jugador. */
    public static final float WIDTH = 0.6f;
    /** Alto de la caja del jugador. */
    public static final float HEIGHT = 1.8f;

    /** Gravedad por paso (bloques/paso^2). */
    private static final double GRAVITY = 0.08;
    /** Impulso vertical del salto. */
    private static final double JUMP_SPEED = 0.5;
    /** Aceleracion aplicada por la entrada, en el suelo. */
    private static final double GROUND_ACCELERATION = 0.1;
    /** Aceleracion aplicada por la entrada, en el aire. */
    private static final double AIR_ACCELERATION = 0.02;
    /** Rozamiento horizontal por paso. */
    private static final double DRAG_XZ = 0.91;
    /** Rozamiento vertical por paso. */
    private static final double DRAG_Y = 0.98;
    /** Rozamiento adicional en el suelo. */
    private static final double GROUND_DRAG = 0.7;

    /** Distancia de aceleracion por debajo de la cual no se aplica entrada. */
    private static final double MIN_INPUT = 0.01;
    /** Altura maxima que busca {@link #unstuck()} para sacar al jugador de un bloque. */
    private static final double MAX_UNSTUCK = 64.0;

    private final World world;

    /** Caja envolvente en unidades de mundo. */
    private AABB boundingBox;

    private double eyeX;
    private double eyeY;
    private double eyeZ;

    private double motionX;
    private double motionY;
    private double motionZ;

    private boolean onGround;

    /** Tiempo pendiente de simular, para el paso fijo. */
    private double accumulator;

    /** Posicion de los ojos antes del ultimo paso, para interpolar el fotograma. */
    private double previousEyeX;
    private double previousEyeY;
    private double previousEyeZ;

    public Player(World world) {
        if (world == null) {
            throw new IllegalArgumentException("El jugador necesita un mundo");
        }
        this.world = world;
        setPosition(0.0, 0.0, 0.0);
    }

    // ------------------------------------------------------------------
    // Posicion.
    // ------------------------------------------------------------------

    /**
     * Coloca al jugador en una posicion, entendida como la de los ojos.
     * La caja se crea alrededor: los pies quedan {@link #EYE_HEIGHT} por debajo.
     */
    public void setPosition(double x, double y, double z) {
        eyeX = x;
        eyeY = y;
        eyeZ = z;

        double half = WIDTH / 2.0;
        double feet = y - EYE_HEIGHT;
        boundingBox = new AABB(x - half, feet, z - half, x + half, feet + HEIGHT, z + half);
        motionX = 0.0;
        motionY = 0.0;
        motionZ = 0.0;
        accumulator = 0.0;

        // Un teletransporte no se interpola: al no haber tiempo pendiente, la
        // posicion dibujada es la nueva desde el primer fotograma.
        previousEyeX = x;
        previousEyeY = y;
        previousEyeZ = z;
    }

    public double eyeX() {
        return eyeX;
    }

    public double eyeY() {
        return eyeY;
    }

    public double eyeZ() {
        return eyeZ;
    }

    public AABB boundingBox() {
        return boundingBox;
    }

    public boolean onGround() {
        return onGround;
    }

    /** Velocidad actual por paso, util para las estadisticas. */
    public double speed() {
        return Math.sqrt(motionX * motionX + motionZ * motionZ);
    }

    // ------------------------------------------------------------------
    // Simulacion.
    // ------------------------------------------------------------------

    /** Pasos de 20 Hz pendientes: descarta el sobrante para no acumular retraso. */
    public void update(double delta, Input input, float yawDegrees) {
        accumulator += Math.max(0.0, delta);

        int steps = 0;
        while (accumulator >= STEP_SECONDS && steps < MAX_STEPS_PER_UPDATE) {
            step(input, yawDegrees);
            accumulator -= STEP_SECONDS;
            steps++;
        }
        if (steps == MAX_STEPS_PER_UPDATE) {
            accumulator = 0.0;
        }
    }

    /** Un paso de simulacion: entrada, gravedad, desplazamiento y rozamiento. */
    private void step(Input input, float yawDegrees) {
        double forward = 0.0;
        double strafe = 0.0;
        if (input.isKeyDown(GLFW_KEY_W)) {
            forward += 1.0;
        }
        if (input.isKeyDown(GLFW_KEY_S)) {
            forward -= 1.0;
        }
        if (input.isKeyDown(GLFW_KEY_D)) {
            strafe += 1.0;
        }
        if (input.isKeyDown(GLFW_KEY_A)) {
            strafe -= 1.0;
        }
        if (input.isKeyDown(GLFW_KEY_SPACE) && onGround) {
            motionY = JUMP_SPEED;
        }

        moveRelative(forward, strafe, onGround ? GROUND_ACCELERATION : AIR_ACCELERATION, yawDegrees);

        motionY -= GRAVITY;
        move(motionX, motionY, motionZ);

        motionX *= DRAG_XZ;
        motionY *= DRAG_Y;
        motionZ *= DRAG_XZ;
        if (onGround) {
            motionX *= GROUND_DRAG;
            motionZ *= GROUND_DRAG;
        }
    }

    /**
     * Anade velocidad en la direccion a la que se mira.
     *
     * @param forward componente frontal ({@code +1} adelante, {@code -1} atras)
     * @param strafe  componente lateral ({@code +1} derecha, {@code -1} izquierda)
     * @param speed   fuerza de la aceleracion
     * @param yawDegrees orientacion horizontal de la camara, en grados
     */
    public void moveRelative(double forward, double strafe, double speed, float yawDegrees) {
        double squared = forward * forward + strafe * strafe;
        if (squared < MIN_INPUT) {
            return;
        }

        double scale = speed / Math.sqrt(squared);
        double f = forward * scale;
        double s = strafe * scale;

        double radians = Math.toRadians(yawDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        // Base de la camara: adelante = (sin yaw, 0, cos yaw),
        // derecha = adelante x arriba = (-cos yaw, 0, sin yaw).
        motionX += f * sin - s * cos;
        motionZ += f * cos + s * sin;
    }

    /**
     * Mueve al jugador resolviendo la colision, tal y como hace {@code Entity.move}
     * del original: primero se recorta el desplazamiento vertical, despues el
     * horizontal y por ultimo el lateral, moviendo la caja entre ejes para que
     * cada recorte vea la posicion ya corregida.
     *
     * @param dx desplazamiento deseado en X
     * @param dy desplazamiento deseado en Y
     * @param dz desplazamiento deseado en Z
     */
    public void move(double dx, double dy, double dz) {
        double wantedX = dx;
        double wantedY = dy;
        double wantedZ = dz;

        // Punto de partida de la interpolacion de este paso.
        previousEyeX = eyeX;
        previousEyeY = eyeY;
        previousEyeZ = eyeZ;

        List<AABB> cubes = world.solidCubes(boundingBox.expand(dx, dy, dz));

        for (AABB cube : cubes) {
            dy = cube.clipYCollide(boundingBox, dy);
        }
        boundingBox.move(0.0, dy, 0.0);

        for (AABB cube : cubes) {
            dx = cube.clipXCollide(boundingBox, dx);
        }
        boundingBox.move(dx, 0.0, 0.0);

        for (AABB cube : cubes) {
            dz = cube.clipZCollide(boundingBox, dz);
        }
        boundingBox.move(0.0, 0.0, dz);

        // Se esta apoyado cuando el descenso se ha recortado.
        onGround = wantedY != dy && wantedY < 0.0;

        // El choque anula la velocidad en el eje afectado.
        if (wantedX != dx) {
            motionX = 0.0;
        }
        if (wantedY != dy) {
            motionY = 0.0;
        }
        if (wantedZ != dz) {
            motionZ = 0.0;
        }

        eyeX = (boundingBox.minX + boundingBox.maxX) / 2.0;
        eyeY = boundingBox.minY + EYE_HEIGHT;
        eyeZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
    }

    /**
     * Fraccion del paso pendiente de simular, en {@code [0, 1)}.
     *
     * <p>Es el {@code partialTicks} del original: cuanto falta para el siguiente
     * paso de 20 Hz, normalizado por la duracion del paso.</p>
     */
    public double interpolation() {
        double alpha = accumulator / STEP_SECONDS;
        if (alpha < 0.0) {
            return 0.0;
        }
        return Math.min(alpha, 1.0);
    }

    /**
     * Posicion de los ojos que debe dibujarse en este fotograma.
     *
     * <p>Es la interpolacion lineal entre la posicion anterior al ultimo paso y
     * la actual. Sin ella, la camara permanece congelada hasta que el acumulador
     * vuelve a completar un paso y el movimiento se ve a tirones.</p>
     */
    public Vector3f interpolatedEyePosition() {
        double alpha = interpolation();
        return new Vector3f(
                (float) (previousEyeX + (eyeX - previousEyeX) * alpha),
                (float) (previousEyeY + (eyeY - previousEyeY) * alpha),
                (float) (previousEyeZ + (eyeZ - previousEyeZ) * alpha));
    }

    /**
     * {@code true} si la caja del jugador solapa algun cubo solido. Los cubos
     * solapados no recortan el movimiento, por lo que desde ahi se puede salir
     * caminando.
     */
    public boolean isInsideSolid() {
        for (AABB cube : world.solidCubes(boundingBox)) {
            if (boundingBox.intersects(cube)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Saca al jugador de dentro de un bloque desplazandolo hacia arriba hasta que
     * su caja quede libre. Se usa al volver del modo sin colision; si no encuentra
     * sitio en {@value #MAX_UNSTUCK} unidades, deja la posicion intacta.
     *
     * @return {@code true} si consiguio una posicion libre
     */
    public boolean unstuck() {
        if (!isInsideSolid()) {
            return true;
        }
        double originalY = eyeY;
        for (double lift = STEP_SECONDS; lift <= MAX_UNSTUCK; lift += STEP_SECONDS) {
            setPosition(eyeX, originalY + lift, eyeZ);
            if (!isInsideSolid()) {
                return true;
            }
        }
        setPosition(eyeX, originalY, eyeZ);
        return false;
    }
}
