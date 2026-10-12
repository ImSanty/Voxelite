package com.microvoxel.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import com.microvoxel.input.Input;
import com.microvoxel.scene.DemoScene;

/**
 * Comprueba la matematica de la camara sin necesidad de un contexto grafico.
 *
 * <p>Esto verifica de forma determinista que la camara apunta al objetivo
 * inicial y que las proyecciones de los puntos son coherentes con la orientacion
 * (lo que garantiza que el renderizado coloca la escena en el centro de la
 * vista).</p>
 *
 * <p>La camara inicial es la del jugador: aparece de pie sobre el terreno, en el
 * punto que devuelve {@link DemoScene#playerSpawn()}, mirando a los bloques de
 * demostracion.</p>
 */
class CameraTest {

    private static final float EPS = 1e-3f;

    private static Camera sceneCamera() {
        DemoScene scene = new DemoScene();
        Camera camera = new Camera();
        Vector3f eye = scene.playerSpawn();
        camera.setPosition(eye.x, eye.y, eye.z);
        camera.lookAt(scene.playerLookTarget());
        return camera;
    }

    private static Vector3f project(Camera camera, float aspect, Vector3f worldPoint) {
        Matrix4f view = camera.viewMatrix();
        Matrix4f projection = camera.projectionMatrix(aspect);
        Vector4f clip = projection.mul(view, new Matrix4f()).transform(new Vector4f(worldPoint, 1.0f));
        return new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w);
    }

    @Test
    void elCentroDeLaEscenaProyectaEnElCentroDeLaVista() {
        Camera camera = sceneCamera();
        Vector3f target = new DemoScene().playerLookTarget();

        Vector3f ndc = project(camera, 16.0f / 9.0f, target);

        assertEquals(0.0f, ndc.x, EPS, "El objetivo debe quedar en el centro horizontal");
        assertEquals(0.0f, ndc.y, EPS, "El objetivo debe quedar en el centro vertical");
        assertTrue(ndc.z > -1.0f && ndc.z < 1.0f, "El objetivo debe quedar dentro del frustum");
    }

    @Test
    void unPuntoALaDerechaProyectaALaDerecha() {
        Camera camera = sceneCamera();
        Vector3f target = new DemoScene().playerLookTarget();
        Vector3f front = camera.front();

        // Vector derecha de la camara = front x (0,1,0).
        Vector3f right = new Vector3f(front).cross(0.0f, 1.0f, 0.0f).normalize();
        Vector3f point = new Vector3f(target).fma(1.0f, right);

        Vector3f ndc = project(camera, 16.0f / 9.0f, point);
        assertTrue(ndc.x > 0.0f, "Un punto a la derecha de la camara debe tener NDC x positivo");
    }

    @Test
    void unPuntoPorEncimaProyectaHaciaArriba() {
        Camera camera = sceneCamera();
        Vector3f target = new DemoScene().playerLookTarget();
        Vector3f point = new Vector3f(target).add(0.0f, 2.0f, 0.0f);

        Vector3f ndc = project(camera, 16.0f / 9.0f, point);
        assertTrue(ndc.y > 0.0f, "Un punto mas alto en el mundo debe quedar por encima del centro");
    }

    @Test
    void alArrancarLosBloquesDeDemostracionQuedanALaVista() {
        Camera camera = sceneCamera();
        float aspect = 16.0f / 9.0f;

        // El jugador arranca de pie sobre el terreno y los bloques de demostracion
        // (cuadricula 3x3, en y = DemoScene.DEMO_BLOCK_Y) son el asunto de la
        // escena: deben estar dentro del frustum inicial.
        int demoY = DemoScene.DEMO_BLOCK_Y;
        for (int x = 0; x <= 5; x += 5) {
            for (int y = demoY; y <= demoY + 1; y++) {
                for (int z = 0; z <= 5; z += 5) {
                    assertVisible(camera, aspect, new Vector3f(x, y, z));
                }
            }
        }
    }

    private static void assertVisible(Camera camera, float aspect, Vector3f corner) {
        Vector3f ndc = project(camera, aspect, corner);
        assertTrue(Math.abs(ndc.x) < 1.0f && Math.abs(ndc.y) < 1.0f,
                "La esquina " + corner + " deberia verse en el arranque");
    }

    @Test
    void moverElRatonHaciaLaDerechaGiraLaVistaHaciaLaDerecha() {
        Camera camera = new Camera();
        camera.setPosition(0.0f, 0.0f, 0.0f);
        Input input = new Input();
        input.onMouseMove(25.0, 0.0);
        camera.update(0.016f, input);

        // Al girar hacia la derecha, un punto situado justo enfrente debe
        // desplazarse hacia la IZQUIERDA de la pantalla (NDC x negativo).
        Vector3f ndc = project(camera, 1.0f, new Vector3f(0.0f, 0.0f, 5.0f));
        assertTrue(ndc.x < 0.0f,
                "El movimiento horizontal del raton esta invertido: giro a la derecha y el punto se fue a " + ndc.x);
    }

    @Test
    void moverElRatonHaciaLaIzquierdaGiraLaVistaHaciaLaIzquierda() {
        Camera camera = new Camera();
        camera.setPosition(0.0f, 0.0f, 0.0f);
        Input input = new Input();
        input.onMouseMove(-25.0, 0.0);
        camera.update(0.016f, input);

        Vector3f ndc = project(camera, 1.0f, new Vector3f(0.0f, 0.0f, 5.0f));
        assertTrue(ndc.x > 0.0f,
                "El movimiento horizontal del raton esta invertido: giro a la izquierda y el punto se fue a " + ndc.x);
    }

    @Test
    void elPitchSeLimitaParaEvitarVoltearLaCamara() {
        Camera camera = new Camera();
        camera.setPosition(0, 0, 0);
        // Un objetivo casi vertical no debe producir NaN ni volcar la camara.
        camera.lookAt(new Vector3f(0.0f, 100.0f, 0.0f));
        assertTrue(Float.isFinite(camera.pitch()));
        assertTrue(camera.pitch() <= 89.0f + EPS);
    }
}
