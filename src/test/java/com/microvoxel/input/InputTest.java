package com.microvoxel.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Comprueba la acumulacion de entrada del fotograma.
 *
 * <p>El motor lee el estado una vez por fotograma y lo reinicia al final, asi que
 * los eventos que lleguen entre medias (varios giros de rueda, varios informes de
 * movimiento del raton) deben sumarse en lugar de sobrescribirse.</p>
 */
class InputTest {

    private static final double TOL = 1e-9;

    @Test
    void laRuedaAcumulaLasMuescasDelFotograma() {
        Input input = new Input();

        input.onScroll(1.0);
        input.onScroll(1.0);
        input.onScroll(-1.0);

        assertEquals(1.0, input.scrollDelta(), TOL,
                "Las muescas del fotograma deben sumarse, no quedarse con la ultima");
    }

    @Test
    void laRuedaConservaLosIncrementosFraccionarios() {
        Input input = new Input();

        // Ruedas de alta resolucion y paneles tactiles no entregan muescas enteras.
        input.onScroll(0.25);
        input.onScroll(0.25);

        assertEquals(0.5, input.scrollDelta(), TOL);
    }

    @Test
    void elGiroDeLaRuedaSeReiniciaAlCerrarElFotograma() {
        Input input = new Input();

        input.onScroll(3.0);
        input.endFrame();

        assertEquals(0.0, input.scrollDelta(), TOL,
                "Un giro no debe volver a aplicarse en el fotograma siguiente");
    }

    @Test
    void laRuedaYElRatonSeAcumulanPorSeparado() {
        Input input = new Input();

        input.onMouseMove(5.0, -3.0);
        input.onScroll(-2.0);

        assertEquals(5.0, input.mouseDeltaX(), TOL);
        assertEquals(-3.0, input.mouseDeltaY(), TOL);
        assertEquals(-2.0, input.scrollDelta(), TOL);

        input.endFrame();

        assertEquals(0.0, input.mouseDeltaX(), TOL);
        assertEquals(0.0, input.mouseDeltaY(), TOL);
        assertEquals(0.0, input.scrollDelta(), TOL);
    }
}
