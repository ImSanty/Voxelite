package com.microvoxel.render;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL11.glPolygonOffset;

import java.util.IdentityHashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.microvoxel.camera.Camera;
import com.microvoxel.mesh.MeshData;
import com.microvoxel.mesh.MicroVoxelMeshBuilder;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Renderizador del mundo de voxels.
 *
 * <p>Mantiene una malla de GPU por bloque logico. Las mallas solo se reconstruyen
 * cuando el bloque esta marcado como sucio, de modo que el bucle de dibujo no
 * toca la CPU para regenerar geometria salvo cuando los datos cambian.</p>
 *
 * <p>Configura el estado fijo de OpenGL 3.3 Core (profundidad y culling de caras
 * posteriores) una sola vez, y en cada fotograma solo fija uniformes y emite
 * dibujos indexados. La geometria ya viene en coordenadas de mundo, por lo que
 * la matriz de modelo es la identidad.</p>
 */
public final class Renderer {

    private static final Vector3f LIGHT_DIRECTION = new Vector3f(0.45f, 0.85f, 0.30f).normalize();
    private static final float AMBIENT = 0.28f;
    private static final float LIGHT_INTENSITY = 0.85f;

    private ShaderProgram voxelShader;
    /**
     * Una malla por unidad de dibujo: un {@link Chunk} (malla combinada de todos
     * sus bloques) o un {@link LogicalBlock} suelto (bloque de demostracion).
     */
    private final Map<Object, Mesh> meshes = new IdentityHashMap<>();
    private final Matrix4f model = new Matrix4f();

    private int totalVertices;
    private int totalIndices;

    /** Compila el shader y genera la malla inicial de todos los bloques. */
    public void initialize(World world) {
        voxelShader = ShaderProgram.fromResources("voxel", "voxel.vert", "voxel.frag");

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glClearColor(0.055f, 0.065f, 0.085f, 1.0f);

        // El terreno se dibuja como una malla combinada por chunk (pocos draw
        // calls aunque haya miles de bloques); los bloques sueltos, uno a uno.
        for (Chunk chunk : world.chunks()) {
            putMesh(chunk, MicroVoxelMeshBuilder.buildChunkMesh(world, chunk));
            for (LogicalBlock block : chunk.blocks()) {
                block.markClean();
            }
        }
        for (LogicalBlock block : world.looseBlocks()) {
            rebuild(block, world);
        }
        recomputeTotals();
    }

    /** Reconstruye unicamente las mallas de las unidades marcadas como sucias. */
    public void rebuildDirty(World world) {
        for (Chunk chunk : world.chunks()) {
            boolean dirty = false;
            for (LogicalBlock block : chunk.blocks()) {
                if (block.isDirty()) {
                    dirty = true;
                    break;
                }
            }
            if (dirty) {
                putMesh(chunk, MicroVoxelMeshBuilder.buildChunkMesh(world, chunk));
                for (LogicalBlock block : chunk.blocks()) {
                    block.markClean();
                }
            }
        }
        // Solo los bloques sueltos pueden necesitar una malla individual; los del
        // terreno se revisan arriba, por chunk.
        for (LogicalBlock block : world.looseBlocks()) {
            if (block.isDirty()) {
                rebuild(block, world);
            }
        }
        recomputeTotals();
    }

    /**
     * Marca como sucios un bloque y sus vecinos directos. Necesario cuando una
     * edicion toca el limite del bloque, porque las caras de la frontera se
     * comparten con el bloque contiguo.
     */
    public void markBlockAndNeighborsDirty(World world, LogicalBlock block) {
        block.markDirty();
        int x = block.position().x();
        int y = block.position().y();
        int z = block.position().z();
        LogicalBlock neighbor;
        neighbor = world.getBlock(x + 1, y, z);
        if (neighbor != null) {
            neighbor.markDirty();
        }
        neighbor = world.getBlock(x - 1, y, z);
        if (neighbor != null) {
            neighbor.markDirty();
        }
        neighbor = world.getBlock(x, y + 1, z);
        if (neighbor != null) {
            neighbor.markDirty();
        }
        neighbor = world.getBlock(x, y - 1, z);
        if (neighbor != null) {
            neighbor.markDirty();
        }
        neighbor = world.getBlock(x, y, z + 1);
        if (neighbor != null) {
            neighbor.markDirty();
        }
        neighbor = world.getBlock(x, y, z - 1);
        if (neighbor != null) {
            neighbor.markDirty();
        }
    }

    private void rebuild(LogicalBlock block, World world) {
        putMesh(block, MicroVoxelMeshBuilder.buildBlockMesh(world, block));
        block.markClean();
    }

    /** Sustituye la malla de una unidad de dibujo (chunk o bloque). */
    private void putMesh(Object key, MeshData data) {
        Mesh old = meshes.remove(key);
        if (old != null) {
            old.close();
        }
        if (!data.isEmpty()) {
            meshes.put(key, Mesh.fromVoxelData(data));
        }
    }

    private void recomputeTotals() {
        totalVertices = 0;
        totalIndices = 0;
        for (Mesh mesh : meshes.values()) {
            totalVertices += mesh.vertexCount();
            totalIndices += mesh.indexCount();
        }
    }

    /** Dibuja toda la escena. */
    public void render(World world, Camera camera, int framebufferWidth, int framebufferHeight) {
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (meshes.isEmpty()) {
            return;
        }

        float aspect = framebufferHeight > 0
                ? (float) framebufferWidth / (float) framebufferHeight
                : 1.0f;

        Matrix4f view = camera.viewMatrix();
        Matrix4f projection = camera.projectionMatrix(aspect);

        voxelShader.bind();
        voxelShader.setMatrix4("uModel", model.identity());
        voxelShader.setMatrix4("uView", view);
        voxelShader.setMatrix4("uProjection", projection);
        voxelShader.setVector3("uLightDirection", LIGHT_DIRECTION);
        voxelShader.setFloat("uAmbient", AMBIENT);
        voxelShader.setFloat("uLightIntensity", LIGHT_INTENSITY);
        voxelShader.setVector3("uCameraPos", camera.position());

        // Desplaza ligeramente la profundidad del relleno para que las lineas de
        // depuracion dibujadas despues sobre la superficie no sufran z-fighting.
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.0f, 1.0f);

        for (Mesh mesh : meshes.values()) {
            mesh.draw();
        }

        glDisable(GL_POLYGON_OFFSET_FILL);
        ShaderProgram.unbind();
    }

    public int totalVertices() {
        return totalVertices;
    }

    public int totalIndices() {
        return totalIndices;
    }

    public int triangleCount() {
        return totalIndices / 3;
    }

    public int meshCount() {
        return meshes.size();
    }

    /** Libera shaders y buffers de GPU. */
    public void close() {
        for (Mesh mesh : meshes.values()) {
            mesh.close();
        }
        meshes.clear();
        if (voxelShader != null) {
            voxelShader.close();
            voxelShader = null;
        }
    }
}
