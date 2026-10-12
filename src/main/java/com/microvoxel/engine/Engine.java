package com.microvoxel.engine;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glGetError;

import java.util.Locale;

import org.joml.Vector3f;

import com.microvoxel.camera.Camera;
import com.microvoxel.debug.DebugOverlay;
import com.microvoxel.input.Input;
import com.microvoxel.phys.Player;
import com.microvoxel.render.DebugRenderer;
import com.microvoxel.render.Renderer;
import com.microvoxel.scene.DemoScene;
import com.microvoxel.window.Window;
import com.microvoxel.world.World;

/**
 * Motor: orquesta el ciclo de vida completo de la aplicacion.
 *
 * <p>
 * Orden de arranque: inicializar GLFW y el contexto, inicializar OpenGL y los
 * shaders, construir la escena de demostracion, generar las mallas iniciales y
 * entrar en el bucle de entrada/actualizacion/renderizado. Al salir, libera
 * todos los recursos en orden inverso.
 * </p>
 *
 * <p>
 * Controles: WASD para desplazarse, Espacio para saltar, raton para orientar la
 * camara, F1 estadisticas, F2 bordes de microvoxels, F3 limites de bloques, F4
 * modo sin colision (vuelo libre), R reaparecer, Tab capturar/liberar el cursor
 * y Escape para salir.
 * </p>
 *
 * <p>
 * El movimiento por defecto lo resuelve {@link Player}, con gravedad, salto y
 * colision contra los cubos solidos del mundo. El modo sin colision (F4) delega
 * en la camara libre de siempre, para poder inspeccionar el terreno desde
 * cualquier angulo.
 * </p>
 */
public final class Engine {

    private static final String TITLE = "Voxelite v0.1";

    private Input input;
    private Window window;
    private Time time;
    private Camera camera;
    private DemoScene scene;
    private Renderer renderer;
    private DebugRenderer debugRenderer;
    private DebugOverlay overlay;
    private Player player;

    /** Modo sin colision: la camara vuela libremente y el jugador no se simula. */
    private boolean noclip = false;

    private boolean showStats = false;
    private boolean showCellBorders = false;
    private boolean showBlockBounds = false;

    /** Comprobacion de errores de OpenGL en arranque (y en el primer fotograma). */
    private boolean firstFrameChecked;

    /**
     * Ejecuta la aplicacion completa; captura y reporta cualquier fallo de
     * arranque.
     */
    public void run() {
        try {
            init();
            loop();
        } catch (IllegalStateException e) {
            System.err.println("Error de inicializacion del motor: " + e.getMessage());
            throw e;
        } finally {
            shutdown();
        }
    }

    private void init() {
        input = new Input();
        window = new Window(TITLE, input);
        window.create();

        time = new Time();
        camera = new Camera();

        scene = new DemoScene();
        World world = scene.world();

        renderer = new Renderer();
        renderer.initialize(world);
        checkGl("inicializacion del renderizador");

        debugRenderer = new DebugRenderer();
        debugRenderer.initialize();
        // Las lineas de depuracion se construyen de forma perezosa al activar F2/F3:
        // con un terreno grande, generarlas siempre en el arranque costaria tiempo y
        // memoria sin que el usuario las haya pedido.
        checkGl("inicializacion de las lineas de depuracion");

        overlay = new DebugOverlay();
        overlay.initialize();
        checkGl("inicializacion de la superposicion de estadisticas");

        // El jugador aparece de pie sobre la superficie solida del terreno; la
        // camara se coloca en sus ojos.
        player = new Player(world);
        Vector3f spawn = scene.playerSpawn();
        player.setPosition(spawn.x, spawn.y, spawn.z);
        camera.setPosition(spawn.x, spawn.y, spawn.z);
        camera.lookAt(scene.playerLookTarget());
        camera.setMovementSpeed(7.0f);

        System.out.println("Voxelite v0.1 iniciado.");
        System.out.println("Bloques logicos: " + world.blockCount()
                + " | resolucion: " + world.resolutionX() + "x" + world.resolutionY()
                + "x" + world.resolutionZ());
        System.out.println("Microvoxels almacenados: " + world.totalStoredMicroVoxels()
                + " | ocupados: " + world.totalOccupiedMicroVoxels());
        System.out.println("Jugador sobre la columna " + DemoScene.SPAWN_BLOCK_X + ","
                + DemoScene.SPAWN_BLOCK_Z + " | modo: caminar con colision"
                + " (F4 sin colision, R reaparecer)");
    }

    private void loop() {
        World world = scene.world();
        while (!window.shouldClose()) {
            window.pollEvents();
            time.update();

            handleInput();

            updateMovement(time.delta());
            // Regenera solo las mallas cuyos datos cambiaron (al inicio, ninguna).
            renderer.rebuildDirty(world);

            renderFrame(world);
            if (!firstFrameChecked) {
                checkGl("primer fotograma renderizado");
                firstFrameChecked = true;
            }

            window.swapBuffers();
            input.endFrame();
        }
    }

    /**
     * Actualiza el movimiento del fotograma: fisica con colision o camara libre.
     *
     * @param delta tiempo del fotograma en segundos
     */
    private void updateMovement(double delta) {
        if (noclip) {
            camera.update((float) delta, input);
            return;
        }
        // La orientacion sigue siendo de la camara; el desplazamiento lo resuelve
        // el jugador contra los cubos solidos del mundo.
        camera.updateLook(input);
        player.update(delta, input, camera.yaw());
        // La fisica avanza a 20 Hz, pero se dibuja la posicion interpolada del
        // fotograma: sin esto la camara solo cambiaria de sitio uno de cada tres
        // fotogramas a 60 FPS y el movimiento se veria a tirones.
        camera.setPosition(player.interpolatedEyePosition());
    }

    /** Activa o desactiva el modo sin colision. */
    private void setNoclip(boolean enabled) {
        noclip = enabled;
        if (!noclip) {
            // Al recuperar la colision, el jugador pasa a estar donde quedo la
            // camara libre y se le saca del bloque si habia volado dentro.
            Vector3f position = camera.position();
            player.setPosition(position.x, position.y, position.z);
            player.unstuck();
            camera.setPosition((float) player.eyeX(), (float) player.eyeY(), (float) player.eyeZ());
        }
    }

    /** Devuelve al jugador a la superficie del terreno. */
    private void respawnPlayer() {
        Vector3f spawn = scene.playerSpawn();
        player.setPosition(spawn.x, spawn.y, spawn.z);
        camera.setPosition(spawn.x, spawn.y, spawn.z);
    }

    private void handleInput() {
        if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
            window.requestClose();
        }
        if (input.wasKeyPressed(GLFW_KEY_F4)) {
            setNoclip(!noclip);
        }
        if (input.wasKeyPressed(GLFW_KEY_R)) {
            respawnPlayer();
        }
        if (input.wasKeyPressed(GLFW_KEY_F1)) {
            showStats = !showStats;
        }
        if (input.wasKeyPressed(GLFW_KEY_F2)) {
            showCellBorders = !showCellBorders;
            if (showCellBorders) {
                debugRenderer.rebuildCellBorders(scene.world());
            }
        }
        if (input.wasKeyPressed(GLFW_KEY_F3)) {
            showBlockBounds = !showBlockBounds;
            if (showBlockBounds) {
                debugRenderer.rebuildBlockBounds(scene.world());
            }
        }
        if (input.wasKeyPressed(GLFW_KEY_TAB)) {
            window.toggleCursorCapture();
        }
    }

    private void renderFrame(World world) {
        window.refreshFramebufferSize();
        int width = window.framebufferWidth();
        int height = window.framebufferHeight();
        float aspect = height > 0 ? (float) width / (float) height : 1.0f;

        renderer.render(world, camera, width, height);

        if (showCellBorders) {
            debugRenderer.renderCellBorders(camera, aspect);
        }
        if (showBlockBounds) {
            debugRenderer.renderBlockBounds(camera, aspect);
        }
        if (showStats) {
            overlay.render(buildStats(world), width, height);
        }
    }

    private String buildStats(World world) {
        long stored = world.totalStoredMicroVoxels();
        long occupied = world.totalOccupiedMicroVoxels();
        float fill = stored > 0 ? (occupied * 100.0f / stored) : 0.0f;

        return String.format(Locale.ROOT,
                "Voxelite v0.1%n"
                        + "FPS: %.1f   Frame: %.2f ms%n"
                        + "Blocks: %d   Resolution: %dx%dx%d%n"
                        + "Stored microvoxels: %d%n"
                        + "Occupied microvoxels: %d (%.1f%%)%n"
                        + "Mesh vertices (per-face): %d   Triangles: %d%n"
                        + "Camera: %.2f, %.2f, %.2f%n"
                        + "Player: onGround [%s] | Mode: %s | Speed: %.2f%n"
                        + "F1 stats [%s] | F2 cell borders [%s] | F3 block bounds [%s] | F4 noclip [%s]%n"
                        + "WASD move | Space jump | R respawn | Mouse look | Tab cursor [%s] | Esc quit",
                time.fps(), time.frameMillis(),
                world.blockCount(), world.resolutionX(), world.resolutionY(), world.resolutionZ(),
                stored,
                occupied, fill,
                renderer.totalVertices(), renderer.triangleCount(),
                camera.position().x, camera.position().y, camera.position().z,
                onOff(player.onGround()), noclip ? "noclip" : "walk", player.speed(),
                onOff(showStats), onOff(showCellBorders), onOff(showBlockBounds),
                onOff(noclip),
                onOff(window.isCursorCaptured()));
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    /**
     * Falla de inmediato si OpenGL registro un error. Convierte un fallo silencioso
     * de recursos graficos en un mensaje util durante el arranque.
     */
    private static void checkGl(String stage) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            throw new IllegalStateException(
                    "Error de OpenGL en " + stage + ": 0x" + Integer.toHexString(error));
        }
    }

    private void shutdown() {
        if (overlay != null) {
            overlay.close();
            overlay = null;
        }
        if (debugRenderer != null) {
            debugRenderer.close();
            debugRenderer = null;
        }
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (window != null) {
            window.destroy();
            window = null;
        }
    }
}
