package com.microvoxel.mesh;

import java.util.HashMap;
import java.util.Map;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.material.VoxelMaterial;
import com.microvoxel.world.BlockPosition;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Generador de geometria a partir de los datos volumetricos.
 *
 * <p>Algoritmo (culling de caras internas):</p>
 * <ol>
 *   <li>Para cada microvoxel ocupado de un bloque se examinan sus seis vecinos
 *       en <b>coordenadas globales</b>.</li>
 *   <li>Se emite una cara solo si el vecino correspondiente esta vacio.</li>
 *   <li>Si el vecino esta ocupado, la cara se omite aunque los dos microvoxels
 *       tengan materiales distintos: nunca se dibuja una cara interna entre dos
 *       celdas ocupadas.</li>
 * </ol>
 *
 * <p>Como los vecinos se consultan en coordenadas globales a traves del
 * {@link World}, los bloques logicos adyacentes comparten frontera sin duplicar
 * ni separar caras, y el exterior del mundo se considera aire. Un bloque
 * completamente vacio no produce geometria.</p>
 *
 * <p>Los vertices se duplican por cara para disponer de normales planas por cara
 * (sombreado correcto en volumenes voxel). El orden de los vertices de cada cara
 * es antihorario visto desde fuera, coherente con el culling de caras traseras
 * ({@code GL_BACK} + {@code GL_CCW}).</p>
 */
public final class MicroVoxelMeshBuilder {

    /** Desplazamiento global del vecino de cada cara. */
    private static final int[][] FACE_OFFSET = {
            {1, 0, 0},   // +X
            {-1, 0, 0},  // -X
            {0, 1, 0},   // +Y
            {0, -1, 0},  // -Y
            {0, 0, 1},   // +Z
            {0, 0, -1},  // -Z
    };

    /** Esquinas (i,j,k) en 0/1 de cada cara, en orden antihorario visto desde fuera. */
    private static final int[][][] FACE_CORNER = {
            {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}}, // +X
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}}, // -X
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // +Y
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, // -Y
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}}, // +Z
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}}, // -Z
    };

    private MicroVoxelMeshBuilder() {
    }

    /**
     * Genera la malla de un unico bloque logico consultando sus vecinos en el
     * mundo. El bloque vacio produce una {@link MeshData#empty()}.
     */
    public static MeshData buildBlockMesh(World world, LogicalBlock block) {
        MeshData.Builder out = new MeshData.Builder();
        appendBlock(world, block, out);
        return out.build();
    }

    /**
     * Genera una malla combinada con todos los bloques del mundo.
     * Util para comprobar que no se duplican caras en las fronteras.
     */
    public static MeshData buildWorldMesh(World world) {
        MeshData.Builder out = new MeshData.Builder();
        for (LogicalBlock block : world.blocks()) {
            appendBlock(world, block, out);
        }
        return out.build();
    }

    // ------------------------------------------------------------------
    // Meshing de chunks de bloques "de Minecraft".
    // ------------------------------------------------------------------

    /**
     * Genera la malla combinada de un {@link Chunk}. Los bloques macizos
     * uniformes (el caso de un bloque de Minecraft: 16x16x16 microvoxels del
     * mismo material) se emiten como una sola cara por lado visible, de modo que
     * un terreno grande no produce millones de micro-caras. Los bloques cuya
     * distribucion no es uniforme delegan en el generador por microvoxel.
     */
    public static MeshData buildChunkMesh(World world, Chunk chunk) {
        MeshData.Builder out = new MeshData.Builder();
        Map<BlockPosition, Integer> uniform = new HashMap<>();
        for (LogicalBlock block : chunk.blocks()) {
            uniform.put(block.position(), block.uniformMaterial());
        }
        for (LogicalBlock block : chunk.blocks()) {
            appendChunkBlock(world, block, uniform, out);
        }
        return out.build();
    }

    /**
     * Clasificacion perezosa y cacheada de un bloque vecino: {@code 0} vacio,
     * {@code > 0} macizo uniforme de ese material y {@code -1} no uniforme.
     */
    private static int uniformCode(World world, Map<BlockPosition, Integer> uniform, BlockPosition position) {
        Integer cached = uniform.get(position);
        if (cached != null) {
            return cached;
        }
        LogicalBlock neighbor = world.getBlock(position);
        int code = neighbor == null ? 0 : neighbor.uniformMaterial();
        uniform.put(position, code);
        return code;
    }

    private static void appendChunkBlock(World world, LogicalBlock block,
            Map<BlockPosition, Integer> uniform, MeshData.Builder out) {
        BlockPosition position = block.position();
        int materialId = uniformCode(world, uniform, position);
        if (materialId == 0) {
            return; // bloque de aire: no genera geometria.
        }

        if (materialId < 0) {
            // Forma no uniforme (p. ej. un bloque de demostracion adyacente o un
            // bloque de terreno decorado): se usa el generador por microvoxel.
            appendBlock(world, block, out);
            return;
        }

        // Solo se puede fusionar la cara completa si ningun vecino es una forma
        // parcial, porque en ese caso podria ocluir solo una parte de la cara.
        for (int f = 0; f < FACE_OFFSET.length; f++) {
            BlockPosition np = position.offset(FACE_OFFSET[f][0], FACE_OFFSET[f][1], FACE_OFFSET[f][2]);
            if (uniformCode(world, uniform, np) < 0) {
                appendBlock(world, block, out);
                return;
            }
        }

        VoxelMaterial material = world.materials().getOrNull(materialId);
        float cr = material != null ? material.red() : 1f;
        float cg = material != null ? material.green() : 1f;
        float cb = material != null ? material.blue() : 1f;

        for (int f = 0; f < FACE_OFFSET.length; f++) {
            BlockPosition np = position.offset(FACE_OFFSET[f][0], FACE_OFFSET[f][1], FACE_OFFSET[f][2]);
            if (uniformCode(world, uniform, np) == 0) {
                appendFullFace(block, f, cr, cg, cb, out);
            }
        }
    }

    /** Emite una cara completa de un bloque macizo uniforme (cara de 1x1 unidad). */
    private static void appendFullFace(LogicalBlock block, int face,
            float r, float g, float b, MeshData.Builder out) {
        float bx = block.minWorldX();
        float by = block.minWorldY();
        float bz = block.minWorldZ();
        float nx = FACE_OFFSET[face][0];
        float ny = FACE_OFFSET[face][1];
        float nz = FACE_OFFSET[face][2];
        int[][] corners = FACE_CORNER[face];
        int a = 0, c = 0, d = 0, e = 0;
        for (int k = 0; k < 4; k++) {
            float px = bx + corners[k][0];
            float py = by + corners[k][1];
            float pz = bz + corners[k][2];
            int idx = out.addVertex(px, py, pz, nx, ny, nz, r, g, b);
            switch (k) {
                case 0 -> a = idx;
                case 1 -> c = idx;
                case 2 -> d = idx;
                case 3 -> e = idx;
                default -> throw new IllegalStateException();
            }
        }
        out.addQuad(a, c, d, e);
    }

    /**
     * Anade la geometria de un bloque a un acumulador existente.
     * Expuesto para permitir mallas combinadas y regeneraciones parciales.
     */
    public static void appendBlock(World world, LogicalBlock block, MeshData.Builder out) {
        MaterialRegistry registry = world.materials();
        int resX = block.resX();
        int resY = block.resY();
        int resZ = block.resZ();
        float sx = block.microVoxelSizeX();
        float sy = block.microVoxelSizeY();
        float sz = block.microVoxelSizeZ();
        float bx = block.minWorldX();
        float by = block.minWorldY();
        float bz = block.minWorldZ();

        int baseX = block.position().x() * resX;
        int baseY = block.position().y() * resY;
        int baseZ = block.position().z() * resZ;

        for (int lz = 0; lz < resZ; lz++) {
            for (int ly = 0; ly < resY; ly++) {
                for (int lx = 0; lx < resX; lx++) {
                    int materialId = block.getMaterial(lx, ly, lz);
                    if (materialId == 0) {
                        continue;
                    }
                    int gx = baseX + lx;
                    int gy = baseY + ly;
                    int gz = baseZ + lz;

                    VoxelMaterial material = registry.getOrNull(materialId);
                    float cr = material != null ? material.red() : 1f;
                    float cg = material != null ? material.green() : 1f;
                    float cb = material != null ? material.blue() : 1f;

                    for (int f = 0; f < FACE_OFFSET.length; f++) {
                        int nx = gx + FACE_OFFSET[f][0];
                        int ny = gy + FACE_OFFSET[f][1];
                        int nz = gz + FACE_OFFSET[f][2];
                        // Cara visible solo si el vecino global esta vacio.
                        if (world.getMaterialGlobal(nx, ny, nz) != 0) {
                            continue;
                        }
                        float normalX = FACE_OFFSET[f][0];
                        float normalY = FACE_OFFSET[f][1];
                        float normalZ = FACE_OFFSET[f][2];
                        int[][] corners = FACE_CORNER[f];
                        int a = 0, b = 0, c = 0, d = 0;
                        for (int k = 0; k < 4; k++) {
                            float px = bx + (lx + corners[k][0]) * sx;
                            float py = by + (ly + corners[k][1]) * sy;
                            float pz = bz + (lz + corners[k][2]) * sz;
                            int idx = out.addVertex(px, py, pz, normalX, normalY, normalZ, cr, cg, cb);
                            switch (k) {
                                case 0 -> a = idx;
                                case 1 -> b = idx;
                                case 2 -> c = idx;
                                case 3 -> d = idx;
                                default -> throw new IllegalStateException();
                            }
                        }
                        out.addQuad(a, b, c, d);
                    }
                }
            }
        }
    }

    /**
     * Genera las lineas de subdivision de las celdas visibles (modo F2).
     *
     * <p>Para cada cara visible se dibujan los cuatro bordes de esa celda. El
     * resultado es una rejilla que muestra la resolucion interna real del bloque
     * sin depender de texturas ni de una malla de cubos superpuesta. Se evita la
     * representacion wireframe de los triangulos, que produciria diagonales poco
     * utiles.</p>
     */
    public static LineData buildCellBorders(World world, LogicalBlock block) {
        LineData.Builder out = new LineData.Builder();
        appendCellBorders(world, block, out);
        return out.build();
    }

    public static void appendCellBorders(World world, LogicalBlock block, LineData.Builder out) {
        int uniform = block.uniformMaterial();
        if (uniform == 0) {
            return; // bloque de aire: sin geometria ni rejilla.
        }
        if (uniform > 0) {
            // Bloque macizo uniforme (un bloque de Minecraft): en lugar de dibujar
            // 4 bordes por cada una de las 256 micro-caras visibles, se dibuja una
            // sola rejilla de subdivision por cara expuesta. Mucho mas compacto.
            appendUniformCellBorders(world, block, out);
            return;
        }

        int resX = block.resX();
        int resY = block.resY();
        int resZ = block.resZ();
        float sx = block.microVoxelSizeX();
        float sy = block.microVoxelSizeY();
        float sz = block.microVoxelSizeZ();
        float bx = block.minWorldX();
        float by = block.minWorldY();
        float bz = block.minWorldZ();

        int baseX = block.position().x() * resX;
        int baseY = block.position().y() * resY;
        int baseZ = block.position().z() * resZ;

        for (int lz = 0; lz < resZ; lz++) {
            for (int ly = 0; ly < resY; ly++) {
                for (int lx = 0; lx < resX; lx++) {
                    if (block.getMaterial(lx, ly, lz) == 0) {
                        continue;
                    }
                    int gx = baseX + lx;
                    int gy = baseY + ly;
                    int gz = baseZ + lz;
                    for (int f = 0; f < FACE_OFFSET.length; f++) {
                        if (world.getMaterialGlobal(gx + FACE_OFFSET[f][0],
                                gy + FACE_OFFSET[f][1],
                                gz + FACE_OFFSET[f][2]) != 0) {
                            continue;
                        }
                        int[][] corners = FACE_CORNER[f];
                        float[] px = new float[4];
                        float[] py = new float[4];
                        float[] pz = new float[4];
                        for (int k = 0; k < 4; k++) {
                            px[k] = bx + (lx + corners[k][0]) * sx;
                            py[k] = by + (ly + corners[k][1]) * sy;
                            pz[k] = bz + (lz + corners[k][2]) * sz;
                        }
                        for (int k = 0; k < 4; k++) {
                            int n = (k + 1) % 4;
                            out.addLine(px[k], py[k], pz[k], px[n], py[n], pz[n]);
                        }
                    }
                }
            }
        }
    }

    /**
     * Rejilla de subdivision de un bloque macizo uniforme: para cada cara
     * expuesta dibuja las 16 divisiones en ambas direcciones sobre el plano de
     * esa cara.
     */
    private static void appendUniformCellBorders(World world, LogicalBlock block, LineData.Builder out) {
        int[] res = { block.resX(), block.resY(), block.resZ() };
        int[] base = {
            block.position().x() * block.resX(),
            block.position().y() * block.resY(),
            block.position().z() * block.resZ()
        };
        int[] probe = new int[3];

        for (int f = 0; f < FACE_OFFSET.length; f++) {
            int axis = FACE_OFFSET[f][0] != 0 ? 0 : (FACE_OFFSET[f][1] != 0 ? 1 : 2);
            int sign = FACE_OFFSET[f][axis];
            // Se consulta la celda central del bloque vecino que toca esta cara
            // (saltando la anchura completa del bloque, no un solo microvoxel):
            // para un bloque completo esa celda representa si la cara entera esta
            // ocluida.
            probe[0] = base[0] + res[0] / 2;
            probe[1] = base[1] + res[1] / 2;
            probe[2] = base[2] + res[2] / 2;
            probe[axis] = base[axis] + (sign > 0 ? res[axis] : -1);
            if (world.getMaterialGlobal(probe[0], probe[1], probe[2]) != 0) {
                continue;
            }
            appendFaceGrid(block, f, out);
        }
    }

    /** Dibuja la rejilla de 16x16 divisiones sobre una cara plana del bloque. */
    private static void appendFaceGrid(LogicalBlock block, int face, LineData.Builder out) {
        int axis = FACE_OFFSET[face][0] != 0 ? 0 : (FACE_OFFSET[face][1] != 0 ? 1 : 2);
        int sign = FACE_OFFSET[face][axis];
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        float[] mins = { block.minWorldX(), block.minWorldY(), block.minWorldZ() };
        float[] maxs = { block.maxWorldX(), block.maxWorldY(), block.maxWorldZ() };
        int[] res = { block.resX(), block.resY(), block.resZ() };
        float plane = sign > 0 ? maxs[axis] : mins[axis];
        float uMin = mins[u], uMax = maxs[u];
        float vMin = mins[v], vMax = maxs[v];
        float du = (uMax - uMin) / res[u];
        float dv = (vMax - vMin) / res[v];
        float[] p0 = new float[3];
        float[] p1 = new float[3];
        for (int i = 0; i <= res[v]; i++) {
            float pv = vMin + i * dv;
            p0[axis] = plane;
            p0[u] = uMin;
            p0[v] = pv;
            p1[axis] = plane;
            p1[u] = uMax;
            p1[v] = pv;
            out.addLine(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2]);
        }
        for (int i = 0; i <= res[u]; i++) {
            float pu = uMin + i * du;
            p0[axis] = plane;
            p0[u] = pu;
            p0[v] = vMin;
            p1[axis] = plane;
            p1[u] = pu;
            p1[v] = vMax;
            out.addLine(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2]);
        }
    }

    /** Lineas de los doce bordes de la caja de un bloque logico. */
    public static LineData buildBlockBounds(LogicalBlock block) {
        LineData.Builder out = new LineData.Builder();
        appendBlockBounds(block, out);
        return out.build();
    }

    public static void appendBlockBounds(LogicalBlock block, LineData.Builder out) {
        float x0 = block.minWorldX(), y0 = block.minWorldY(), z0 = block.minWorldZ();
        float x1 = block.maxWorldX(), y1 = block.maxWorldY(), z1 = block.maxWorldZ();
        // 4 aristas en X
        out.addLine(x0, y0, z0, x1, y0, z0);
        out.addLine(x0, y1, z0, x1, y1, z0);
        out.addLine(x0, y0, z1, x1, y0, z1);
        out.addLine(x0, y1, z1, x1, y1, z1);
        // 4 aristas en Y
        out.addLine(x0, y0, z0, x0, y1, z0);
        out.addLine(x1, y0, z0, x1, y1, z0);
        out.addLine(x0, y0, z1, x0, y1, z1);
        out.addLine(x1, y0, z1, x1, y1, z1);
        // 4 aristas en Z
        out.addLine(x0, y0, z0, x0, y0, z1);
        out.addLine(x1, y0, z0, x1, y0, z1);
        out.addLine(x0, y1, z0, x0, y1, z1);
        out.addLine(x1, y1, z0, x1, y1, z1);
    }
}
