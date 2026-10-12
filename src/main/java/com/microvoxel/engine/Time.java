package com.microvoxel.engine;

/**
 * Reloj del bucle principal.
 *
 * <p>Cronometra el tiempo transcurrido entre fotogramas para que el movimiento
 * dependa del tiempo real y no de una velocidad fija por fotograma. Tambien
 * calcula una estimacion suavizada de los FPS y el tiempo por fotograma en
 * milisegundos para la superposicion de depuracion.</p>
 */
public final class Time {

    /** Limite del delta para evitar saltos enormes tras una pausa o un arrastre de ventana. */
    private static final double MAX_DELTA = 0.25;

    private double lastNanos;
    private double deltaSeconds;
    private double elapsedSeconds;

    /** Media movil de la duracion del fotograma, en segundos. */
    private double smoothedFrameSeconds = 1.0 / 60.0;
    private boolean firstFrame = true;

    public Time() {
        lastNanos = System.nanoTime();
    }

    /** Debe llamarse una vez al inicio de cada fotograma. */
    public void update() {
        double now = System.nanoTime();
        double dt = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;

        if (firstFrame) {
            // El primer fotograma no tiene una referencia valida anterior.
            firstFrame = false;
            deltaSeconds = 0.0;
        } else {
            deltaSeconds = Math.min(dt, MAX_DELTA);
            elapsedSeconds += deltaSeconds;
        }

        if (deltaSeconds > 0.0) {
            // Media exponencial: suaviza el ruido del cronometro sin ocultar caidas.
            smoothedFrameSeconds = smoothedFrameSeconds * 0.9 + deltaSeconds * 0.1;
        }
    }

    /** Tiempo transcurrido desde el fotograma anterior, en segundos. */
    public double delta() {
        return deltaSeconds;
    }

    /** Tiempo total acumulado, en segundos. */
    public double elapsed() {
        return elapsedSeconds;
    }

    /** Estimacion de FPS basada en la media suavizada del tiempo por fotograma. */
    public double fps() {
        return smoothedFrameSeconds > 0.0 ? 1.0 / smoothedFrameSeconds : 0.0;
    }

    /** Tiempo por fotograma en milisegundos. */
    public double frameMillis() {
        return smoothedFrameSeconds * 1000.0;
    }
}
