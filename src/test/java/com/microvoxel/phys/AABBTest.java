package com.microvoxel.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifica la geometria del sistema de colision extraido de rd-161348.
 *
 * <p>Son pruebas puras de matematica: no hay mundo ni camara. El convenio es el
 * del original, {@code this} es la caja estatica (un cubo solido) y el argumento
 * la caja que se mueve.</p>
 */
class AABBTest {

    private static final double EPS = 1e-9;
    /**
     * Los recortes se miden con menos precision porque la caja del jugador se
     * construye con {@link Player#WIDTH} y {@link Player#HEIGHT}, que son
     * {@code float}: 0,6f no es exactamente 0,6 en {@code double}.
     */
    private static final double CLIP_EPS = 1e-6;

    /** Caja de un microvoxel en el origen, usada como estatica. */
    private static AABB cube(double x, double y, double z, double size) {
        return new AABB(x, y, z, x + size, y + size, z + size);
    }

    /** Caja del jugador (0.6 x 1.8 x 0.6) centrada horizontalmente en (x, z). */
    private static AABB body(double x, double feetY, double z) {
        double half = Player.WIDTH / 2.0;
        return new AABB(x - half, feetY, z - half, x + half, feetY + Player.HEIGHT, z + half);
    }

    @Test
    void expandCreceHaciaElSentidoDelMovimiento() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);

        AABB positive = box.expand(2, 3, 4);
        assertEquals(0, positive.minX, EPS);
        assertEquals(3, positive.maxX, EPS);
        assertEquals(0, positive.minZ, EPS);
        assertEquals(5, positive.maxZ, EPS);
        assertEquals(0, positive.minY, EPS);
        assertEquals(4, positive.maxY, EPS);

        AABB negative = box.expand(-2, -3, -4);
        assertEquals(-2, negative.minX, EPS);
        assertEquals(1, negative.maxX, EPS);
        assertEquals(-3, negative.minY, EPS);
        assertEquals(1, negative.maxY, EPS);
        assertEquals(-4, negative.minZ, EPS);
        assertEquals(1, negative.maxZ, EPS);
    }

    @Test
    void elRecorteEnXFrenaElAvanceContraLaCaraIzquierda() {
        AABB wall = new AABB(2, 0, 0, 3, 3, 3);
        AABB movil = body(0.0, 0.0, 0.0);

        // El cuerpo mide 0.6 de ancho: su cara derecha esta en 0.3.
        double permitido = wall.clipXCollide(movil, 5.0);

        assertEquals(1.7, permitido, CLIP_EPS, "La cara derecha debe quedar en x = 2");
        assertEquals(2.0, movil.maxX + permitido, CLIP_EPS, "Debe quedar apoyada en la cara del cubo");
    }

    @Test
    void elRecorteEnXFrenaElRetrocesoContraLaCaraDerecha() {
        AABB wall = new AABB(2, 0, 0, 3, 3, 3);
        AABB movil = body(4.0, 0.0, 0.0);

        double permitido = wall.clipXCollide(movil, -5.0);

        assertEquals(-0.7, permitido, CLIP_EPS, "La cara izquierda debe quedar en x = 3");
    }

    @Test
    void noSeRecortaSiLasCajasNoSeSolapanEnElOtroEje() {
        // Muro alto pero lejos en X: el cuerpo lo alcanzaria, y debe frenarse.
        AABB wall = new AABB(1, 0, 0, 2, 3, 3);
        assertTrue(wall.clipXCollide(body(0.0, 0.0, 0.0), 5.0) < 5.0);

        // Muro por encima de la cabeza: no solapa en Y, no frena nada.
        AABB techo = new AABB(1, 3, 0, 2, 4, 3);
        assertEquals(5.0, techo.clipXCollide(body(0.0, 0.0, 0.0), 5.0), CLIP_EPS);

        // Muro desplazado en Z: tampoco solapa.
        AABB lateral = new AABB(1, 0, 5, 2, 3, 6);
        assertEquals(5.0, lateral.clipXCollide(body(0.0, 0.0, 0.0), 5.0), CLIP_EPS);
    }

    @Test
    void elRecorteEnYDetectaElSueloYElTecho() {
        AABB suelo = cube(0, 0, 0, 1);
        AABB cuerpo = body(0.5, 1.0, 0.5);

        // Cayendo: los pies se quedan a 1.0, sobre el suelo.
        assertEquals(0.0, suelo.clipYCollide(cuerpo, -1.0), CLIP_EPS);
        // Subiendo contra el techo (misma caja como techo por encima).
        AABB techo = cube(0, 2.8, 0, 1);
        assertEquals(0.0, techo.clipYCollide(cuerpo, 1.0), CLIP_EPS);
    }

    @Test
    void elRecorteEnZFrenaEnLosDosSentidos() {
        AABB muro = new AABB(0, 0, 2, 3, 3, 3);
        assertEquals(1.7, muro.clipZCollide(body(0.0, 0.0, 0.0), 5.0), CLIP_EPS);

        AABB muroDetras = new AABB(0, 0, 2, 3, 3, 3);
        assertEquals(-0.7, muroDetras.clipZCollide(body(0.0, 0.0, 4.0), -5.0), CLIP_EPS);
    }

    @Test
    void unRecorteNoEmpujaNiFrenaUnaCajaQueYaSolapa() {
        AABB dentro = new AABB(0, 0, 0, 3, 3, 3);
        AABB movil = body(0.5, 0.0, 0.5);

        // Al solapar, el recorte no cambia el desplazamiento: se puede salir.
        assertEquals(5.0, dentro.clipXCollide(movil, 5.0), CLIP_EPS);
        assertEquals(2.0, dentro.clipYCollide(movil, 2.0), CLIP_EPS);
        assertEquals(1.0, dentro.clipZCollide(movil, 1.0), CLIP_EPS);
    }

    @Test
    void intersectsIgnoraElContactoExacto() {
        AABB base = new AABB(0, 0, 0, 1, 1, 1);

        assertTrue(base.intersects(new AABB(0.5, 0.5, 0.5, 1.5, 1.5, 1.5)));
        // Tocandose por la cara no hay solape.
        assertFalse(base.intersects(new AABB(1, 0, 0, 2, 1, 1)));
        assertFalse(base.intersects(new AABB(0, 1, 0, 1, 2, 1)));
        assertFalse(base.intersects(new AABB(2, 0, 0, 3, 1, 1)));
    }

    @Test
    void moveDesplazaYOffsetYCopyNoCompartenEstado() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);

        AABB offset = box.offset(1, 2, 3);
        assertEquals(1, offset.minX, EPS);
        assertEquals(2, offset.minY, EPS);
        assertEquals(3, offset.minZ, EPS);
        assertEquals(4, offset.maxZ, EPS);

        AABB copy = box.copy();
        box.move(1, 0, 0);
        assertEquals(1, box.minX, EPS);
        assertEquals(0, copy.minX, EPS, "La copia no debe cambiar al mover el original");
    }

    @Test
    void growExpandePorLosDosLadosSinMoverElCentro() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);
        AABB grown = box.grow(0.5, 1.0, 2.0);

        assertEquals(-0.5, grown.minX, EPS);
        assertEquals(1.5, grown.maxX, EPS);
        assertEquals(-1.0, grown.minY, EPS);
        assertEquals(-2.0, grown.minZ, EPS);
        assertEquals(3.0, grown.maxZ, EPS);
        assertEquals(0.5, (grown.minX + grown.maxX) / 2.0, EPS);
    }
}
