package com.microvoxel.render;

import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import java.nio.ByteBuffer;

import com.microvoxel.mesh.LineData;
import com.microvoxel.mesh.MeshData;

/**
 * Conjunto de buffers de GPU (VAO + VBO + EBO) para una malla concreta.
 *
 * <p>Todo el estado de OpenGL de una malla vive aqui. La geometria se sube una
 * sola vez (o se actualiza cuando se reconstruye); nunca se crean ni destruyen
 * buffers por fotograma. La clase cubre tres usos:</p>
 * <ul>
 *   <li>Mallas de voxels intercaladas (posicion, normal, color): 9 floats/vertice.</li>
 *   <li>Lineas de depuracion (solo posicion): 3 floats/vertice, sin indices.</li>
 *   <li>Texto de la superposicion: vertices de 16 bytes (3 floats de posicion +
 *       4 bytes de color) generados por STB y un buffer de indices fijo que
 *       convierte los quads en triangulos (OpenGL Core no dibuja GL_QUADS).</li>
 * </ul>
 */
public final class Mesh {

    private int vao;
    private int vbo;
    private int ebo;

    private int vertexCount;
    private int indexCount;
    private final boolean indexed;

    private Mesh(int vao, int vbo, int ebo, int vertexCount, int indexCount, boolean indexed) {
        this.vao = vao;
        this.vbo = vbo;
        this.ebo = ebo;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.indexed = indexed;
    }

    /** Crea la malla de voxels a partir de los datos generados en CPU. */
    public static Mesh fromVoxelData(MeshData data) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data.vertices(), GL_STATIC_DRAW);

        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, data.indices(), GL_STATIC_DRAW);

        int stride = MeshData.FLOATS_PER_VERTEX * Float.BYTES;
        // posicion
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, MeshData.POSITION_COMPONENTS, GL_FLOAT, false, stride, 0L);
        // normal
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, MeshData.NORMAL_COMPONENTS, GL_FLOAT, false, stride,
                (long) MeshData.POSITION_COMPONENTS * Float.BYTES);
        // color
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, MeshData.COLOR_COMPONENTS, GL_FLOAT, false, stride,
                (long) (MeshData.POSITION_COMPONENTS + MeshData.NORMAL_COMPONENTS) * Float.BYTES);

        glBindVertexArray(0);

        return new Mesh(vao, vbo, ebo, data.vertexCount(), data.indexCount(), true);
    }

    /** Crea una malla de lineas no indexada (solo posiciones). */
    public static Mesh fromLineData(LineData data) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data.positions(), GL_STATIC_DRAW);

        int stride = LineData.FLOATS_PER_VERTEX * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);

        glBindVertexArray(0);

        return new Mesh(vao, vbo, 0, data.vertexCount(), 0, false);
    }

    /**
     * Crea una malla dinamica para texto de depuracion.
     *
     * <p>Reserva espacio para {@code maxQuads} quads generados por STB. Cada quad
     * son 4 vertices de 16 bytes (posicion + color empaquetado). El buffer de
     * indices convierte cada quad en dos triangulos, una sola vez.</p>
     */
    public static Mesh createTextMesh(int maxQuads) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int quadSizeBytes = 4 * 16;

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) maxQuads * quadSizeBytes, GL_DYNAMIC_DRAW);

        // El atributo de posicion de STB ocupa los 12 primeros bytes de cada
        // vertice de 16; el color va en los 4 restantes y se ignora (se usa un
        // color uniforme en el shader).
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 16, 0L);

        int[] indices = new int[maxQuads * 6];
        for (int q = 0; q < maxQuads; q++) {
            int base = q * 4;
            int o = q * 6;
            indices[o] = base;
            indices[o + 1] = base + 1;
            indices[o + 2] = base + 2;
            indices[o + 3] = base;
            indices[o + 4] = base + 2;
            indices[o + 5] = base + 3;
        }
        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glBindVertexArray(0);

        return new Mesh(vao, vbo, ebo, 0, 0, true);
    }

    /** Sube de nuevo los vertices de la malla de texto a partir de los datos de STB. */
    public void updateTextVertices(ByteBuffer data, int quadCount) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, data);
        this.vertexCount = quadCount * 4;
        this.indexCount = quadCount * 6;
    }

    /** Dibuja el contenido con el modo de primitivas adecuado. */
    public void draw() {
        glBindVertexArray(vao);
        if (indexed) {
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
        } else {
            glDrawArrays(org.lwjgl.opengl.GL11.GL_LINES, 0, vertexCount);
        }
        glBindVertexArray(0);
    }

    public void drawLines() {
        glBindVertexArray(vao);
        glDrawArrays(org.lwjgl.opengl.GL11.GL_LINES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int indexCount() {
        return indexCount;
    }

    public boolean isEmpty() {
        return vertexCount == 0 && indexCount == 0;
    }

    /** Libera los buffers de OpenGL. */
    public void close() {
        if (ebo != 0) {
            glDeleteBuffers(ebo);
            ebo = 0;
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
    }
}
