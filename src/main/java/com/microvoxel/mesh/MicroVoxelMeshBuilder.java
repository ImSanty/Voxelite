package com.microvoxel.mesh;

import java.util.ArrayList;
import java.util.List;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.material.VoxelMaterial;
import com.microvoxel.world.BlockPosition;
import com.microvoxel.world.BlockSource;
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
     * Genera la malla de un {@link Chunk}.
     *
     * <p>La malla tiene dos partes:</p>
     *
     * <ol>
     *   <li>La <b>banda</b> vertical del terreno generado (relieve, paredes y
     *       fondo). En un terreno macizo la inmensa mayoria de las celdas de la
     *       banda son roca enterrada: {@link BlockSource#isHiddenInterior} las
     *       salta sin consultar vecinos, de modo que el recorrido cuesta un puñado
     *       de comparaciones por celda y solo las expuestas pagan las seis
     *       consultas.</li>
     *   <li>Los <b>bloques materializados</b> que el chunk contiene (editados o de
     *       la escena de demostracion). Si alguno tiene forma propia, las celdas de
     *       la banda que lo tocan delegan en el generador por microvoxel, porque una
     *       forma parcial puede ocluir solo una parte de la cara.</li>
     * </ol>
     *
     * <p>Los bloques materializados que caen en el chunk pero pertenecen a otro
     * (o son sueltos, como los de demostracion) se dejan a su dueño: asi ninguna
     * cara se dibuja dos veces.</p>
     */
    public static MeshData buildChunkMesh(World world, Chunk chunk) {
        int x0 = chunk.chunkX() * Chunk.BLOCKS_X;
        int z0 = chunk.chunkZ() * Chunk.BLOCKS_Z;
        int x1 = x0 + Chunk.BLOCKS_X - 1;
        int z1 = z0 + Chunk.BLOCKS_Z - 1;

        List<LogicalBlock> local = blocksInFootprint(world, x0, z0, x1, z1);
        // Con cualquier bloque materializado en el entorno se renuncia al atajo de
        // roca enterrada: un bloque editado (o retirado) expone caras nuevas.
        boolean fastSkip = blocksInFootprint(world, x0 - 1, z0 - 1, x1 + 1, z1 + 1).isEmpty();

        int bandMin = World.MAX_BUILD_BLOCK_Y;
        int bandMax = World.MIN_BLOCK_Y;
        BlockSource source = world.blockSource();
        if (source != null) {
            int[] range = source.faceRange(x0, z0, x1, z1);
            if (range != null) {
                bandMin = Math.min(bandMin, range[0]);
                bandMax = Math.max(bandMax, range[1]);
            }
        }
        for (LogicalBlock block : chunk.blocks()) {
            bandMin = Math.min(bandMin, block.position().y());
            bandMax = Math.max(bandMax, block.position().y());
        }
        if (bandMin > bandMax) {
            // Nada que mallar: sin terreno en esta columna de chunks.
            chunk.setBand(0, -1);
            return MeshData.empty();
        }
        chunk.setBand(bandMin, bandMax);

        MeshData.Builder out = new MeshData.Builder();
        if (source != null) {
            appendBand(world, source, local, fastSkip, x0, z0, x1, z1, bandMin, bandMax, out);
        }
        for (LogicalBlock block : chunk.blocks()) {
            appendChunkBlock(world, block, out);
        }
        return out.build();
    }

    /** Bloques materializados del mundo cuya columna cae en el rectangulo dado. */
    private static List<LogicalBlock> blocksInFootprint(World world, int x0, int z0, int x1, int z1) {
        List<LogicalBlock> found = new ArrayList<>();
        for (LogicalBlock block : world.blocks()) {
            BlockPosition p = block.position();
            if (p.x() >= x0 && p.x() <= x1 && p.z() >= z0 && p.z() <= z1) {
                found.add(block);
            }
        }
        return found;
    }

    /** Bloque materializado en una posicion de la lista local, o {@code null}. */
    private static LogicalBlock localAt(List<LogicalBlock> local, int x, int y, int z) {
        for (int i = 0; i < local.size(); i++) {
            LogicalBlock block = local.get(i);
            BlockPosition p = block.position();
            if (p.x() == x && p.y() == y && p.z() == z) {
                return block;
            }
        }
        return null;
    }

    /**
     * Recorre la banda del chunk emitiendo las caras de las celdas expuestas.
     *
     * <p>El orden de comprobaciones importa: primero la lista local (lineal, de
     * unos pocos bloques) para no tocar el mapa del mundo en cada celda, luego el
     * atajo de roca enterrada y, solo para las celdas que llegan al final, la
     * consulta de vecinos.</p>
     */
    private static void appendBand(World world, BlockSource source, List<LogicalBlock> local,
            boolean fastSkip, int x0, int z0, int x1, int z1, int bandMin, int bandMax,
            MeshData.Builder out) {
        for (int lz = 0; lz < Chunk.BLOCKS_Z; lz++) {
            for (int lx = 0; lx < Chunk.BLOCKS_X; lx++) {
                int bx = x0 + lx;
                int bz = z0 + lz;
                // En el borde del chunk los vecinos pueden caer fuera del rectangulo:
                // solo ahi hace falta consultar el mapa del mundo.
                boolean border = lx == 0 || lz == 0 || bx == x1 || bz == z1;

                for (int by = bandMin; by <= bandMax; by++) {
                    if (localAt(local, bx, by, bz) != null) {
                        continue; // materializado: lo emite appendChunkBlock().
                    }
                    if (fastSkip && source.isHiddenInterior(bx, by, bz)) {
                        continue;
                    }
                    int code = codeAt(world, local, border, bx, by, bz);
                    if (code <= 0) {
                        // 0 = aire. Menos de cero solo puede venir de una forma
                        // propia de otra unidad (una celda del borde que pertenece a
                        // otro chunk): la dibuja su dueño.
                        continue;
                    }

                    // Las caras se emiten completas. Una forma parcial en la celda
                    // vecina (un bloque de demostracion pegado a la roca) se solapa
                    // con su propia malla en lugar de recortar la cara: la escena de
                    // demostracion flota por encima del terreno y no llega a darse,
                    // y el camino exacto entre bloques materializados lo cubre
                    // appendChunkBlock().
                    VoxelMaterial material = world.materials().getOrNull(code);
                    float cr = material != null ? material.red() : 1f;
                    float cg = material != null ? material.green() : 1f;
                    float cb = material != null ? material.blue() : 1f;
                    for (int f = 0; f < FACE_OFFSET.length; f++) {
                        if (codeAt(world, local, border, bx + FACE_OFFSET[f][0],
                                by + FACE_OFFSET[f][1], bz + FACE_OFFSET[f][2]) == 0) {
                            appendFullFace(bx, by, bz, f, cr, cg, cb, out);
                        }
                    }
                }
            }
        }
    }

    /**
     * Material de una celda como codigo de clasificacion: {@code 0} aire,
     * {@code > 0} macizo uniforme y {@code -1} forma propia.
     */
    private static int codeAt(World world, List<LogicalBlock> local, boolean border, int x, int y, int z) {
        LogicalBlock own = localAt(local, x, y, z);
        if (own != null) {
            return own.uniformMaterial();
        }
        if (border) {
            LogicalBlock other = world.getBlock(x, y, z);
            if (other != null) {
                return other.uniformMaterial();
            }
        }
        BlockSource source = world.blockSource();
        return source != null ? source.materialAt(x, y, z) : 0;
    }

    /** Malla de un bloque materializado del chunk, con sus vecinos reales. */
    private static void appendChunkBlock(World world, LogicalBlock block, MeshData.Builder out) {
        BlockPosition position = block.position();
        int materialId = block.uniformMaterial();
        if (materialId == 0) {
            return; // bloque de aire: no genera geometria.
        }

        if (materialId < 0) {
            appendBlock(world, block, out);
            return;
        }

        // Solo se puede fusionar la cara completa si ningun vecino es una forma
        // parcial, porque en ese caso podria ocluir solo una parte de la cara.
        for (int f = 0; f < FACE_OFFSET.length; f++) {
            if (world.materialCode(position.x() + FACE_OFFSET[f][0],
                    position.y() + FACE_OFFSET[f][1], position.z() + FACE_OFFSET[f][2]) < 0) {
                appendBlock(world, block, out);
                return;
            }
        }

        VoxelMaterial material = world.materials().getOrNull(materialId);
        float cr = material != null ? material.red() : 1f;
        float cg = material != null ? material.green() : 1f;
        float cb = material != null ? material.blue() : 1f;

        for (int f = 0; f < FACE_OFFSET.length; f++) {
            if (world.materialCode(position.x() + FACE_OFFSET[f][0],
                    position.y() + FACE_OFFSET[f][1], position.z() + FACE_OFFSET[f][2]) == 0) {
                appendFullFace(position.x(), position.y(), position.z(), f, cr, cg, cb, out);
            }
        }
    }

    /** Emite una cara completa de un bloque macizo uniforme (cara de 1x1 unidad). */
    private static void appendFullFace(int blockX, int blockY, int blockZ, int face,
            float r, float g, float b, MeshData.Builder out) {
        float bx = blockX;
        float by = blockY;
        float bz = blockZ;
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

    /**
     * Rejilla de subdivision sobre la cara superior de cada columna expuesta del
     * terreno generado (modo F2), dentro de un rectangulo de columnas.
     *
     * <p>El terreno generado no tiene bloques a los que pedir sus caras, asi que se
     * deduce del mundo: se baja desde el techo de la banda hasta el primer bloque
     * solido y se dibuja su rejilla superior. Es ademas lo que acota el modo de
     * depuracion a un radio alrededor de la camara: con el mundo entero serian
     * cientos de miles de lineas.</p>
     */
    public static void appendSurfaceCellBorders(World world, int minBlockX, int minBlockZ,
            int maxBlockX, int maxBlockZ, LineData.Builder out) {
        BlockSource source = world.blockSource();
        if (source == null) {
            return;
        }
        int[] range = source.faceRange(minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (range == null) {
            return;
        }
        int resX = world.resolutionX();
        int resZ = world.resolutionZ();
        for (int z = minBlockZ; z <= maxBlockZ; z++) {
            for (int x = minBlockX; x <= maxBlockX; x++) {
                int top = topSolidBlock(world, x, z, range[1]);
                if (top < 0 || world.materialCode(x, top + 1, z) != 0) {
                    continue; // sin superficie seca: no hay cara superior que marcar.
                }
                appendTopFaceGrid(x, top, z, resX, resZ, out);
            }
        }
    }

    /** Cajas de los bloques de superficie del terreno generado (modo F3). */
    public static void appendSurfaceBlockBounds(World world, int minBlockX, int minBlockZ,
            int maxBlockX, int maxBlockZ, LineData.Builder out) {
        BlockSource source = world.blockSource();
        if (source == null) {
            return;
        }
        int[] range = source.faceRange(minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (range == null) {
            return;
        }
        for (int z = minBlockZ; z <= maxBlockZ; z++) {
            for (int x = minBlockX; x <= maxBlockX; x++) {
                int top = topSolidBlock(world, x, z, range[1]);
                if (top >= 0) {
                    appendUnitBounds(x, top, z, out);
                }
            }
        }
    }

    /** Bloque solido mas alto de una columna, o {@code -1} si no hay ninguno. */
    private static int topSolidBlock(World world, int blockX, int blockZ, int fromBlockY) {
        int from = Math.min(fromBlockY, World.MAX_BUILD_BLOCK_Y);
        for (int by = from; by >= World.MIN_BLOCK_Y; by--) {
            if (world.isSolidBlock(blockX, by, blockZ)) {
                return by;
            }
        }
        return -1;
    }

    /** Rejilla de subdivision sobre la cara +Y del bloque, en coordenadas de bloque. */
    private static void appendTopFaceGrid(int blockX, int blockY, int blockZ,
            int resX, int resZ, LineData.Builder out) {
        float y = blockY + 1.0f;
        float dx = 1.0f / resX;
        float dz = 1.0f / resZ;
        for (int i = 0; i <= resZ; i++) {
            float z = blockZ + i * dz;
            out.addLine(blockX, y, z, blockX + 1.0f, y, z);
        }
        for (int i = 0; i <= resX; i++) {
            float x = blockX + i * dx;
            out.addLine(x, y, blockZ, x, y, blockZ + 1.0f);
        }
    }

    /** Doce aristas de la caja de una unidad, en coordenadas de bloque. */
    private static void appendUnitBounds(int blockX, int blockY, int blockZ, LineData.Builder out) {
        float x0 = blockX, y0 = blockY, z0 = blockZ;
        float x1 = blockX + 1.0f, y1 = blockY + 1.0f, z1 = blockZ + 1.0f;
        out.addLine(x0, y0, z0, x1, y0, z0);
        out.addLine(x0, y1, z0, x1, y1, z0);
        out.addLine(x0, y0, z1, x1, y0, z1);
        out.addLine(x0, y1, z1, x1, y1, z1);
        out.addLine(x0, y0, z0, x0, y1, z0);
        out.addLine(x1, y0, z0, x1, y1, z0);
        out.addLine(x0, y0, z1, x0, y1, z1);
        out.addLine(x1, y0, z1, x1, y1, z1);
        out.addLine(x0, y0, z0, x0, y0, z1);
        out.addLine(x1, y0, z0, x1, y0, z1);
        out.addLine(x0, y1, z0, x0, y1, z1);
        out.addLine(x1, y1, z0, x1, y1, z1);
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
