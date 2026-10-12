package com.microvoxel.mesh;

/**
 * Datos de lineas (no triangulos) para el modo de depuracion.
 *
 * <p>Cada vertice es una posicion XYZ. Las lineas se interpretan por pares:
 * {@code (0,1)}, {@code (2,3)}, ... con {@code GL_LINES}.</p>
 */
public final class LineData {

    public static final int FLOATS_PER_VERTEX = 3;

    private final float[] positions;

    public LineData(float[] positions) {
        this.positions = positions;
    }

    public static LineData empty() {
        return new LineData(new float[0]);
    }

    public float[] positions() {
        return positions;
    }

    public int vertexCount() {
        return positions.length / FLOATS_PER_VERTEX;
    }

    public int lineCount() {
        return vertexCount() / 2;
    }

    public boolean isEmpty() {
        return positions.length == 0;
    }

    /** Acumulador simple de segmentos. */
    public static final class Builder {
        private float[] positions = new float[3 * 256];
        private int count = 0;

        public void addLine(float x0, float y0, float z0, float x1, float y1, float z1) {
            if (count + 6 > positions.length) {
                float[] bigger = new float[positions.length * 2];
                System.arraycopy(positions, 0, bigger, 0, count);
                positions = bigger;
            }
            positions[count++] = x0;
            positions[count++] = y0;
            positions[count++] = z0;
            positions[count++] = x1;
            positions[count++] = y1;
            positions[count++] = z1;
        }

        public LineData build() {
            float[] p = new float[count];
            System.arraycopy(positions, 0, p, 0, count);
            return new LineData(p);
        }
    }
}
