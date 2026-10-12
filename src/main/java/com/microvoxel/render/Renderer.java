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
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glPolygonOffset;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import java.util.IdentityHashMap;
import java.util.Map;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.microvoxel.camera.Camera;
import com.microvoxel.mesh.MeshData;
import com.microvoxel.mesh.MicroVoxelMeshBuilder;
import com.microvoxel.world.BlockPosition;
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
 *
 * <p>El aspecto (luz calida del sol contra cielo frio, luz de contorno, niebla
 * atmosferica hacia el color del cielo y saturacion) vive en los shaders; aqui
 * solo estan sus parametros: {@link #SKY_ZENITH}, {@link #SKY_HORIZON} y
 * {@link #FOG_DENSITY}. El cielo y la niebla comparten los mismos dos colores,
 * asi que no pueden desentonar entre si.</p>
 */
public final class Renderer {

    private static final Vector3f LIGHT_DIRECTION = new Vector3f(0.45f, 0.85f, 0.30f).normalize();
    private static final float AMBIENT = 0.28f;
    private static final float LIGHT_INTENSITY = 0.85f;

    /**
     * Cielo en el cenit: azul profundo. Es el color dominante del fondo y, con el
     * del horizonte, el destino de la niebla lejana: al compartirlos, el terreno
     * distante se disuelve en el cielo sin dejar una linea de horizonte artificial.
     */
    private static final Vector3f SKY_ZENITH = new Vector3f(0.13f, 0.20f, 0.36f);

    /** Cielo a ras de horizonte: mas claro y apagado, como una calima. */
    private static final Vector3f SKY_HORIZON = new Vector3f(0.44f, 0.50f, 0.58f);

    /**
     * Densidad de la niebla atmosferica, en 1 / unidades de mundo. Elegida para
     * que a ~25 unidades (los bloques de demostracion) apenas se note y a ~100 (el
     * terreno lejano) domine el color del cielo.
     */
    private static final float FOG_DENSITY = 0.008f;

    private ShaderProgram voxelShader;
    /** Shader y VAO vacio del fondo de cielo (el triangulo se genera en el shader). */
    private ShaderProgram skyShader;
    private int skyVao;
    /**
     * Una malla por unidad de dibujo: un {@link Chunk} (malla combinada de todos
     * sus bloques) o un {@link LogicalBlock} suelto (bloque de demostracion).
     */
    private final Map<Object, Mesh> meshes = new IdentityHashMap<>();
    private final Matrix4f model = new Matrix4f();
    private final Matrix4f inverseViewProjection = new Matrix4f();
    private final Matrix4f viewProjection = new Matrix4f();
    /**
     * Volumen de la vista, para no dibujar los chunks que quedan fuera. Con una
     * banda vertical de ~50 bloques por chunk, mirar hacia abajo dejaria la mitad
     * del mundo oculta tras el terreno y el descarte deja de ser un adorno.
     */
    private final FrustumIntersection frustum = new FrustumIntersection();

    private int totalVertices;
    private int totalIndices;

    /** Compila el shader y genera la malla inicial de todos los bloques. */
    public void initialize(World world) {
        voxelShader = ShaderProgram.fromResources("voxel", "voxel.vert", "voxel.frag");
        skyShader = ShaderProgram.fromResources("sky", "sky.vert", "sky.frag");
        // El triangulo del cielo no usa atributos, pero el perfil Core exige un VAO
        // enlazado para poder dibujar.
        skyVao = glGenVertexArrays();

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        // Color de limpieza: el horizonte, para que un fotograma sin cielo (o el
        // instante previo al primero) ya sea del color del fondo.
        glClearColor(SKY_HORIZON.x, SKY_HORIZON.y, SKY_HORIZON.z, 1.0f);

        // El terreno se dibuja como una malla combinada por chunk (pocos draw
        // calls aunque haya miles de bloques generados); los bloques sueltos, uno
        // a uno.
        for (Chunk chunk : world.chunks()) {
            putMesh(chunk, MicroVoxelMeshBuilder.buildChunkMesh(world, chunk));
            for (LogicalBlock block : chunk.blocks()) {
                block.markClean();
            }
            chunk.markClean();
        }
        for (LogicalBlock block : world.looseBlocks()) {
            rebuild(block, world);
        }
        recomputeTotals();
    }

    /** Reconstruye unicamente las mallas de las unidades marcadas como sucias. */
    public void rebuildDirty(World world) {
        for (Chunk chunk : world.chunks()) {
            boolean dirty = chunk.isDirty();
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
                chunk.markClean();
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
     * Marca como sucios un bloque y sus vecinos directos.
     *
     * <p>Necesario cuando una edicion toca el limite del bloque, porque las caras
     * de la frontera se comparten con el bloque contiguo. Tambien marca su chunk y,
     * si la edicion esta pegada a un borde, los chunks vecinos: la malla de un chunk
     * cubre la banda entera, y una cara nueva en la frontera es trabajo suyo.</p>
     */
    public void markBlockAndNeighborsDirty(World world, LogicalBlock block) {
        markChunksAround(world, block);
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

    /** Marca sucios los chunks que cubren un bloque (y los que tocan sus bordes). */
    private static void markChunksAround(World world, LogicalBlock block) {
        BlockPosition p = block.position();
        for (Chunk chunk : world.chunks()) {
            int cx0 = chunk.chunkX() * Chunk.BLOCKS_X;
            int cz0 = chunk.chunkZ() * Chunk.BLOCKS_Z;
            int cx1 = cx0 + Chunk.BLOCKS_X - 1;
            int cz1 = cz0 + Chunk.BLOCKS_Z - 1;
            boolean inside = p.x() >= cx0 && p.x() <= cx1 && p.z() >= cz0 && p.z() <= cz1;
            if (!inside) {
                continue;
            }
            chunk.markDirty();
            boolean onBorder = p.x() == cx0 || p.x() == cx1 || p.z() == cz0 || p.z() == cz1;
            if (onBorder) {
                markNeighborChunksDirty(world, chunk);
            }
        }
    }

    /** Marca sucios los chunks contiguos a uno dado (los que comparten pared). */
    private static void markNeighborChunksDirty(World world, Chunk chunk) {
        for (Chunk other : world.chunks()) {
            int dx = Math.abs(other.chunkX() - chunk.chunkX());
            int dz = Math.abs(other.chunkZ() - chunk.chunkZ());
            if (dx + dz == 1) {
                other.markDirty();
            }
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

        float aspect = framebufferHeight > 0
                ? (float) framebufferWidth / (float) framebufferHeight
                : 1.0f;

        Matrix4f view = camera.viewMatrix();
        Matrix4f projection = camera.projectionMatrix(aspect);

        // El fondo se dibuja siempre, aunque no haya nada que mostrar.
        drawSky(camera, view, projection);

        if (meshes.isEmpty()) {
            return;
        }

        voxelShader.bind();
        voxelShader.setMatrix4("uModel", model.identity());
        voxelShader.setMatrix4("uView", view);
        voxelShader.setMatrix4("uProjection", projection);
        voxelShader.setVector3("uLightDirection", LIGHT_DIRECTION);
        voxelShader.setFloat("uAmbient", AMBIENT);
        voxelShader.setFloat("uLightIntensity", LIGHT_INTENSITY);
        voxelShader.setVector3("uCameraPos", camera.position());
        voxelShader.setVector3("uSkyZenith", SKY_ZENITH);
        voxelShader.setVector3("uSkyHorizon", SKY_HORIZON);
        voxelShader.setFloat("uFogDensity", FOG_DENSITY);

        // Desplaza ligeramente la profundidad del relleno para que las lineas de
        // depuracion dibujadas despues sobre la superficie no sufran z-fighting.
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.0f, 1.0f);

        viewProjection.set(projection).mul(view);
        frustum.set(viewProjection);

        // Chunks de terreno: una malla por chunk, dibujada solo si su banda puede
        // estar a la vista.
        for (Chunk chunk : world.chunks()) {
            Mesh mesh = meshes.get(chunk);
            if (mesh == null) {
                continue;
            }
            if (!frustum.testAab(chunk.minWorldX(), chunk.minWorldY(), chunk.minWorldZ(),
                    chunk.maxWorldX(), chunk.maxWorldY(), chunk.maxWorldZ())) {
                continue;
            }
            mesh.draw();
        }

        // Bloques sueltos (escena de demostracion): malla propia, uno a uno.
        for (LogicalBlock block : world.looseBlocks()) {
            Mesh mesh = meshes.get(block);
            if (mesh == null) {
                continue;
            }
            if (!frustum.testAab(block.minWorldX(), block.minWorldY(), block.minWorldZ(),
                    block.maxWorldX(), block.maxWorldY(), block.maxWorldZ())) {
                continue;
            }
            mesh.draw();
        }

        glDisable(GL_POLYGON_OFFSET_FILL);
        ShaderProgram.unbind();
    }

    /**
     * Dibuja el gradiente de cielo como fondo, antes que la geometria.
     *
     * <p>Es un unico triangulo de pantalla completa sin profundidad: no escribe en
     * el buffer de profundidad (taparia el terreno) ni necesita geometria propia.
     * El color de cada pixel sale de la direccion de vista, que se obtiene
     * desproyectando la esquina del plano lejano con la inversa de vista por
     * proyeccion.</p>
     */
    private void drawSky(Camera camera, Matrix4f view, Matrix4f projection) {
        glDepthMask(false);
        glDisable(GL_DEPTH_TEST);

        skyShader.bind();
        inverseViewProjection.set(projection).mul(view).invert();
        skyShader.setMatrix4("uInverseViewProjection", inverseViewProjection);
        skyShader.setVector3("uCameraPos", camera.position());
        skyShader.setVector3("uSkyZenith", SKY_ZENITH);
        skyShader.setVector3("uSkyHorizon", SKY_HORIZON);

        glBindVertexArray(skyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);

        ShaderProgram.unbind();
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
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
        if (skyVao != 0) {
            glDeleteVertexArrays(skyVao);
            skyVao = 0;
        }
        if (skyShader != null) {
            skyShader.close();
            skyShader = null;
        }
        if (voxelShader != null) {
            voxelShader.close();
            voxelShader = null;
        }
    }
}
