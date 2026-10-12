package com.microvoxel.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.microvoxel.input.Input;
import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.scene.ShapeBuilder;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Verifica la adaptacion del movimiento con colision de rd-161348.
 *
 * <p>Los mundos son minimos (unos pocos bloques) para que las comprobaciones sean
 * exactas: no hace falta el terreno de demostracion para probar la fisica.</p>
 */
class PlayerTest {

    private static final int RES = 16;
    private static final double TOL = 1e-9;

    private static World world() {
        return new World(MaterialRegistry.createDemo(), RES);
    }

    private static LogicalBlock solidBlock(World world, int x, int y, int z, int material) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.solid(block.grid(), material);
        return block;
    }

    /** Avanza la simulacion un numero exacto de pasos de 20 Hz. */
    private static void advance(Player player, Input input, int steps, float yaw) {
        for (int i = 0; i < steps; i++) {
            player.update(Player.STEP_SECONDS, input, yaw);
        }
    }

    private static Input keys(int... pressed) {
        Input input = new Input();
        for (int key : pressed) {
            input.onKey(key, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        }
        return input;
    }

    @Test
    void laGravedadHaceCaerAlJugadorHastaApoyarseEnElSuelo() {
        World world = world();
        solidBlock(world, 0, 0, 0, MaterialRegistry.STONE);

        Player player = new Player(world);
        player.setPosition(0.5, 5.0, 0.5);
        Input input = new Input();

        advance(player, input, 40, 0.0f);

        assertTrue(player.onGround(), "Tras caer debe quedar apoyado");
        assertEquals(1.0 + Player.EYE_HEIGHT, player.eyeY(), 1e-6,
                "Los pies deben quedar justo sobre la cara superior del bloque");
    }

    @Test
    void elJugadorNoAtraviesaUnaPared() {
        World world = world();
        // Suelo continuo y un muro de dos bloques de alto en x = 1.
        for (int x = 0; x <= 2; x++) {
            solidBlock(world, x, 0, 0, MaterialRegistry.STONE);
        }
        solidBlock(world, 1, 1, 0, MaterialRegistry.STONE);

        Player player = new Player(world);
        player.setPosition(0.5, 1.0 + Player.EYE_HEIGHT, 0.5);
        Input input = keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W);

        // Con yaw = 90 el avance apunta a +X, contra el muro.
        advance(player, input, 60, 90.0f);

        assertEquals(1.0 - Player.WIDTH / 2.0, player.eyeX(), 1e-6,
                "La cara derecha debe quedar pegada al muro");
        assertFalse(player.isInsideSolid(), "El jugador no debe acabar dentro del muro");
    }

    @Test
    void laColisionRespetaLaSubdivisionEnMicrovoxeles() {
        World world = world();
        LogicalBlock floor = solidBlock(world, 0, 0, 0, MaterialRegistry.STONE);
        // Se vacia la ultima capa del bloque: el suelo real queda 1/16 mas abajo.
        floor.grid().clearBox(0, RES - 1, 0, RES - 1, RES - 1, RES - 1);

        Player player = new Player(world);
        player.setPosition(0.5, 3.0, 0.5);
        Input input = new Input();

        advance(player, input, 40, 0.0f);

        assertTrue(player.onGround());
        assertEquals(1.0 - 1.0 / RES + Player.EYE_HEIGHT, player.eyeY(), 1e-6,
                "Los pies deben apoyarse en la capa de microvoxels que queda");
    }

    @Test
    void elSaltoSoloFuncionaConLosPiesEnElSuelo() {
        World world = world();
        solidBlock(world, 0, 0, 0, MaterialRegistry.STONE);

        Player player = new Player(world);
        player.setPosition(0.5, 1.0 + Player.EYE_HEIGHT, 0.5);

        // El salto solo es valido con los pies ya apoyados: primero se asienta.
        advance(player, new Input(), 3, 0.0f);
        assertTrue(player.onGround());

        advance(player, keys(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE), 1, 0.0f);
        double trasPrimerPaso = player.eyeY();
        assertTrue(trasPrimerPaso > 1.0 + Player.EYE_HEIGHT,
                "Con los pies en el suelo, el paso siguiente debe elevar al jugador: "
                        + trasPrimerPaso);

        // Sin volver a pulsar, el impulso se agota y cae hasta el suelo.
        advance(player, new Input(), 40, 0.0f);
        assertEquals(1.0 + Player.EYE_HEIGHT, player.eyeY(), 1e-6,
                "Debe volver a apoyarse en el suelo");
        assertTrue(player.onGround());
    }

    @Test
    void enElAireNoVuelveASaltar() {
        World world = world();
        solidBlock(world, 0, 0, 0, MaterialRegistry.STONE);

        Player player = new Player(world);
        player.setPosition(0.5, 20.0, 0.5);
        Input input = keys(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);

        advance(player, input, 2, 0.0f);

        assertFalse(player.onGround());
        assertTrue(player.eyeY() < 20.0, "Sin apoyo, la gravedad manda");
    }

    @Test
    void elAvanceSigueLaOrientacionDeLaCamara() {
        World world = world();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                solidBlock(world, x, 0, z, MaterialRegistry.STONE);
            }
        }

        // yaw = 0 mira hacia +Z: avanzar debe aumentar Z y no tocar X.
        Player haciaAdelante = new Player(world);
        haciaAdelante.setPosition(4.0, 1.0 + Player.EYE_HEIGHT, 4.0);
        advance(haciaAdelante, keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W), 10, 0.0f);
        assertTrue(haciaAdelante.eyeZ() > 4.0, "Con yaw = 0, W debe avanzar hacia +Z");
        assertEquals(4.0, haciaAdelante.eyeX(), 1e-6);

        // yaw = 90 mira hacia +X.
        Player haciaLaDerecha = new Player(world);
        haciaLaDerecha.setPosition(4.0, 1.0 + Player.EYE_HEIGHT, 4.0);
        advance(haciaLaDerecha, keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W), 10, 90.0f);
        assertTrue(haciaLaDerecha.eyeX() > 4.0, "Con yaw = 90, W debe avanzar hacia +X");
        assertEquals(4.0, haciaLaDerecha.eyeZ(), 1e-6);

        // yaw = 0 y D: la derecha en pantalla es -X.
        Player deLado = new Player(world);
        deLado.setPosition(4.0, 1.0 + Player.EYE_HEIGHT, 4.0);
        advance(deLado, keys(org.lwjgl.glfw.GLFW.GLFW_KEY_D), 10, 0.0f);
        assertTrue(deLado.eyeX() < 4.0, "Con yaw = 0, D debe desplazar hacia -X");
        assertEquals(4.0, deLado.eyeZ(), 1e-6);
    }

    @Test
    void alFrenarElMovimientoSeDetiene() {
        World world = world();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                solidBlock(world, x, 0, z, MaterialRegistry.STONE);
            }
        }

        Player player = new Player(world);
        player.setPosition(6.0, 1.0 + Player.EYE_HEIGHT, 6.0);
        advance(player, keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W), 10, 0.0f);
        assertTrue(player.speed() > 0.0, "Debe estar en movimiento");

        advance(player, new Input(), 60, 0.0f);
        assertEquals(0.0, player.speed(), TOL, "Sin entrada y con rozamiento debe detenerse");
    }

    @Test
    void elPasoFijoNoDependeDelDeltaDelFotograma() {
        World world = world();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                solidBlock(world, x, 0, z, MaterialRegistry.STONE);
            }
        }
        // Un solo fotograma puede simular como maximo MAX_STEPS_PER_UPDATE pasos.
        int pasos = Player.MAX_STEPS_PER_UPDATE;
        double esperado = Player.STEP_SECONDS * pasos;

        // Un fotograma largo debe simular los mismos pasos que muchos cortos.
        Player largo = new Player(world);
        largo.setPosition(6.0, 1.0 + Player.EYE_HEIGHT, 6.0);
        largo.update((float) esperado, keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W), 0.0f);

        Player cortos = new Player(world);
        cortos.setPosition(6.0, 1.0 + Player.EYE_HEIGHT, 6.0);
        advance(cortos, keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W), pasos, 0.0f);

        assertEquals(cortos.eyeZ(), largo.eyeZ(), 1e-9,
                "El desplazamiento debe depender solo del tiempo simulado");
    }

    @Test
    void laPosicionDibujadaInterpolaEntreLosDosUltimosPasos() {
        World world = world();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                solidBlock(world, x, 0, z, MaterialRegistry.STONE);
            }
        }

        Player player = new Player(world);
        player.setPosition(6.0, 1.0 + Player.EYE_HEIGHT, 6.0);

        // Tras el primer paso, la posicion simulada ya avanzo pero el fotograma
        // dibuja el punto de partida (no hay tiempo pendiente hacia el siguiente).
        player.update(Player.STEP_SECONDS, keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W), 0.0f);
        double trasPrimerPaso = player.eyeZ();
        assertTrue(trasPrimerPaso > 6.0, "El paso debe haber avanzado");
        assertEquals(6.0, player.interpolatedEyePosition().z, 1e-6);

        // Con medio paso pendiente, el fotograma dibuja el punto medio, sin que la
        // simulacion haya avanzado (0 pasos).
        player.update(Player.STEP_SECONDS / 2.0, new Input(), 0.0f);
        assertEquals(0.5, player.interpolation(), 1e-9);
        assertEquals(trasPrimerPaso, player.eyeZ(), 1e-9);
        assertEquals((6.0 + trasPrimerPaso) / 2.0, player.interpolatedEyePosition().z, 1e-6);
    }

    @Test
    void elDibujoEsContinuoAunqueLaFisicaVayaA20Hz() {
        World world = world();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                solidBlock(world, x, 0, z, MaterialRegistry.STONE);
            }
        }

        Player player = new Player(world);
        player.setPosition(8.0, 1.0 + Player.EYE_HEIGHT, 8.0);
        Input input = keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W);

        // Un segundo de simulacion para alcanzar velocidad estable.
        advance(player, input, 20, 0.0f);

        // Un segundo a 60 FPS: solo 1 de cada 3 fotogramas ejecuta un paso, pero se
        // dibuja en todos. Sin interpolar, la camara daria un salto cada 3 fotogramas.
        int frames = 30;
        float frameDelta = 1.0f / 60.0f;
        double previous = player.interpolatedEyePosition().z;
        double maximo = 0.0;
        double total = 0.0;
        for (int i = 0; i < frames; i++) {
            player.update(frameDelta, input, 0.0f);
            double actual = player.interpolatedEyePosition().z;
            double salto = actual - previous;
            previous = actual;
            maximo = Math.max(maximo, salto);
            total += salto;
        }

        double media = total / frames;
        assertTrue(media > 0.0, "El jugador debe estar avanzando");
        assertTrue(maximo <= media * 1.25,
                "Ningun fotograma debe dar un salto grande: maximo=" + maximo + " media=" + media);
    }

    @Test
    void unTeletransporteNoSeInterpola() {
        World world = world();
        solidBlock(world, 0, 0, 0, MaterialRegistry.STONE);

        Player player = new Player(world);
        player.setPosition(0.5, 5.0, 0.5);
        advance(player, new Input(), 5, 0.0f);

        // Reaparecer no debe dibujar un deslizamiento desde donde estaba.
        player.setPosition(0.5, 20.0, 0.5);
        assertEquals(0.0, player.interpolation(), 1e-9);
        assertEquals(20.0, player.interpolatedEyePosition().y, 1e-6);
    }

    @Test
    void unstuckSacaAlJugadorDeDentroDeUnBloque() {
        World world = world();
        solidBlock(world, 0, 0, 0, MaterialRegistry.STONE);

        Player player = new Player(world);
        player.setPosition(0.5, 0.9, 0.5);
        assertTrue(player.isInsideSolid(), "El jugador esta dentro del bloque");

        assertTrue(player.unstuck(), "Debe encontrar sitio libre hacia arriba");
        assertFalse(player.isInsideSolid());
        assertNotEquals(0.9, player.eyeY());
        assertTrue(player.eyeY() >= 1.0 + Player.EYE_HEIGHT - TOL,
                "Debe quedar por encima de la cara superior");
    }

    @Test
    void elPasoFijoNoSeAcumulaSinLimite() {
        World world = world();
        solidBlock(world, 0, 0, 0, MaterialRegistry.STONE);

        Player player = new Player(world);
        player.setPosition(0.5, 1.0 + Player.EYE_HEIGHT, 0.5);
        Input input = keys(org.lwjgl.glfw.GLFW.GLFW_KEY_W);

        // Un fotograma enorme solo puede simular MAX_STEPS_PER_UPDATE pasos.
        player.update(100.0f, input, 0.0f);

        double maximo = Player.MAX_STEPS_PER_UPDATE * 1.0;
        assertTrue(player.eyeZ() - 0.5 < maximo,
                "El retraso no debe convertirse en una simulacion sin fin");
    }
}
