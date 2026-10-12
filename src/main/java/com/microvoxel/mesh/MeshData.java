package com.microvoxel.mesh;

/**
 * Datos de malla en CPU, listos para subir a la GPU.
 *
 * <p>La geometria renderizable es una representacion <i>derivada</i>: nunca es
 * la fuente de verdad sobre la forma de un bloque. Los datos volumetricos viven
 * en {@code MicroVoxelGrid}; esta clase solo transporta triangulos.</p>
 *
 * <p>Formato de vertice (intercalado, 9 floats = 36 bytes):</p>
 * <pre>
 *   posicion (x, y, z) | normal (x, y, z) | color (r, g, b)
 * </pre>
 *
 * <p>Se duplican vertices por cara para poder tener normales planas por cara
 * (sombreado correcto en volumenes voxel) y colores por material. Por tanto, al
 * comparar cantidades en las pruebas hay que hablar de <b>vertices duplicados
 * por cara</b> e <b>indices de triangulos</b>, no de vertices unicos.</p>
 */
public final class MeshData {

    /** Numero de componentes float por vertice. */
    public static final int FLOATS_PER_VERTEX = 9;
    /** Numero de componentes float de la posicion. */
    public static final int POSITION_COMPONENTS = 3;
    /** Numero de componentes float de la normal. */
    public static final int NORMAL_COMPONENTS = 3;
    /** Numero de componentes float del color. */
    public static final int COLOR_COMPONENTS = 3;

    private final float[] vertices;
    private final int[] indices;

    public MeshData(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    public static MeshData empty() {
        return new MeshData(new float[0], new int[0]);
    }

    public float[] vertices() {
        return vertices;
    }

    public int[] indices() {
        return indices;
    }

    public int vertexCount() {
        return vertices.length / FLOATS_PER_VERTEX;
    }

    public int indexCount() {
        return indices.length;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    public boolean isEmpty() {
        return indices.length == 0;
    }

    /** Acumulador de crecimiento dinamico usado por los generadores de malla. */
    public static final class Builder {
        private float[] vertices = new float[FLOATS_PER_VERTEX * 1024];
        private int[] indices = new int[1024 * 6];
        private int vertexFloats = 0;
        private int indexCount = 0;

        /** Anade un vertice y devuelve su indice. */
        public int addVertex(float px, float py, float pz,
                             float nx, float ny, float nz,
                             float r, float g, float b) {
            if (vertexFloats + FLOATS_PER_VERTEX > vertices.length) {
                float[] bigger = new float[vertices.length * 2];
                System.arraycopy(vertices, 0, bigger, 0, vertexFloats);
                vertices = bigger;
            }
            int index = vertexFloats / FLOATS_PER_VERTEX;
            vertices[vertexFloats++] = px;
            vertices[vertexFloats++] = py;
            vertices[vertexFloats++] = pz;
            vertices[vertexFloats++] = nx;
            vertices[vertexFloats++] = ny;
            vertices[vertexFloats++] = nz;
            vertices[vertexFloats++] = r;
            vertices[vertexFloats++] = g;
            vertices[vertexFloats++] = b;
            return index;
        }

        public void addTriangle(int a, int b, int c) {
            if (indexCount + 3 > indices.length) {
                int[] bigger = new int[indices.length * 2];
                System.arraycopy(indices, 0, bigger, 0, indexCount);
                indices = bigger;
            }
            indices[indexCount++] = a;
            indices[indexCount++] = b;
            indices[indexCount++] = c;
        }

        public void addQuad(int a, int b, int c, int d) {
            addTriangle(a, b, c);
            addTriangle(a, c, d);
        }

        public boolean isEmpty() {
            return indexCount == 0;
        }

        public MeshData build() {
            float[] v = new float[vertexFloats];
            System.arraycopy(vertices, 0, v, 0, vertexFloats);
            int[] i = new int[indexCount];
            System.arraycopy(indices, 0, i, 0, indexCount);
            return new MeshData(v, i);
        }
    }
}
