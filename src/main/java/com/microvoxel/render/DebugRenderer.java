package com.microvoxel.render;

import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;

import org.joml.Matrix4f;

import com.microvoxel.camera.Camera;
import com.microvoxel.mesh.LineData;
import com.microvoxel.mesh.LineData.Builder;
import com.microvoxel.mesh.MicroVoxelMeshBuilder;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Renderizador del modo de depuracion.
 *
 * <p>Dibuja dos conjuntos de lineas independientes del sombreado normal:</p>
 * <ul>
 *   <li>Bordes de las celdas visibles (F2): una rejilla que muestra la
 *       subdivision real en microvoxels, sin texturas ni mallas de cubos
 *       superpuestas.</li>
 *   <li>Limites de los bloques logicos (F3): la caja de una unidad de cada
 *       bloque, util para distinguir la frontera entre bloques vecinos.</li>
 * </ul>
 *
 * <p>Usa el shader de depuracion con una proyeccion en perspectiva igual a la de
 * la camara. Las lineas se dibujan con prueba de profundidad para que queden
 * ocultas tras la geometria, salvo un ligero desplazamiento de relleno aplicado
 * por {@link Renderer} que evita el z-fighting en las caras visibles.</p>
 */
public final class DebugRenderer {

    private static final float CELL_BORDER_R = 0.10f;
    private static final float CELL_BORDER_G = 1.00f;
    private static final float CELL_BORDER_B = 0.65f;

    private static final float BLOCK_BOUNDS_R = 1.00f;
    private static final float BLOCK_BOUNDS_G = 0.55f;
    private static final float BLOCK_BOUNDS_B = 0.15f;

    private ShaderProgram debugShader;
    private Mesh cellBorders;
    private Mesh blockBounds;

    private final Matrix4f identity = new Matrix4f();

    public void initialize() {
        debugShader = ShaderProgram.fromResources("debug", "debug.vert", "debug.frag");
    }

    /** Regenera la rejilla de bordes de celda visibles de todos los bloques. */
    public void rebuildCellBorders(World world) {
        Builder builder = new Builder();
        for (LogicalBlock block : world.blocks()) {
            MicroVoxelMeshBuilder.appendCellBorders(world, block, builder);
        }
        replaceCellBorders(builder.build());
    }

    /** Regenera las cajas de los limites de todos los bloques logicos. */
    public void rebuildBlockBounds(World world) {
        Builder builder = new Builder();
        for (LogicalBlock block : world.blocks()) {
            MicroVoxelMeshBuilder.appendBlockBounds(block, builder);
        }
        replaceBlockBounds(builder.build());
    }

    private void replaceCellBorders(LineData data) {
        if (cellBorders != null) {
            cellBorders.close();
            cellBorders = null;
        }
        if (!data.isEmpty()) {
            cellBorders = Mesh.fromLineData(data);
        }
    }

    private void replaceBlockBounds(LineData data) {
        if (blockBounds != null) {
            blockBounds.close();
            blockBounds = null;
        }
        if (!data.isEmpty()) {
            blockBounds = Mesh.fromLineData(data);
        }
    }

    public void renderCellBorders(Camera camera, float aspect) {
        draw(cellBorders, camera, aspect, CELL_BORDER_R, CELL_BORDER_G, CELL_BORDER_B);
    }

    public void renderBlockBounds(Camera camera, float aspect) {
        draw(blockBounds, camera, aspect, BLOCK_BOUNDS_R, BLOCK_BOUNDS_G, BLOCK_BOUNDS_B);
    }

    private void draw(Mesh mesh, Camera camera, float aspect,
                      float r, float g, float b) {
        if (mesh == null || mesh.isEmpty()) {
            return;
        }
        // Las lineas conservan la prueba de profundidad (quedan ocultas tras la
        // geometria) pero no participan en el culling de caras.
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glLineWidth(1.0f);

        debugShader.bind();
        debugShader.setMatrix4("uProjection", camera.projectionMatrix(aspect));
        debugShader.setMatrix4("uView", camera.viewMatrix());
        debugShader.setFloat("uScale", 1.0f);
        debugShader.setVector4("uColor", r, g, b, 1.0f);

        mesh.drawLines();

        glEnable(GL_CULL_FACE);
        ShaderProgram.unbind();
    }

    /** Numero de segmentos de linea del ultimo conjunto de bordes generado. */
    public int cellBorderSegments() {
        return cellBorders != null ? cellBorders.vertexCount() / 2 : 0;
    }

    public void close() {
        if (cellBorders != null) {
            cellBorders.close();
            cellBorders = null;
        }
        if (blockBounds != null) {
            blockBounds.close();
            blockBounds = null;
        }
        if (debugShader != null) {
            debugShader.close();
            debugShader = null;
        }
    }
}
