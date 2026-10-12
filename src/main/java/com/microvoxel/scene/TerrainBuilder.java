package com.microvoxel.scene;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Terreno de demostracion fijo con estructura de Minecraft.
 *
 * <p>
 * El terreno se organiza en una cuadricula de {@value #CHUNKS_X} x
 * {@value #CHUNKS_Z} <b>chunks</b> ({@value #CHUNKS_X} x {@value #CHUNKS_Z}
 * {@value #CHUNKS_Y} = {@code 16} chunks). Cada chunk cubre
 * {@value com.microvoxel.world.Chunk#BLOCKS_X} x
 * {@value com.microvoxel.world.Chunk#BLOCKS_Y} x
 * {@value com.microvoxel.world.Chunk#BLOCKS_Z} bloques logicos, de modo que el
 * terreno completo mide {@value #BLOCKS_X} x {@value #LAYERS} x
 * {@value #BLOCKS_Z} bloques: 64 columnas y {@value #LAYERS} bloques de altura.
 * </p>
 *
 * <p>
 * La altura se calcula <b>por bloque</b>, no por microvoxel. Por tanto cada
 * bloque del terreno es un cubo macizo de 16x16x16 microvoxels del mismo
 * material (un "bloque de Minecraft") o esta completamente vacio. La subdivison
 * interna sigue existiendo (4.096 voxeles por bloque) y se puede inspeccionar
 * con el modo de depuracion F2, pero el relieve se cuantiza a la rejilla de
 * bloques.
 * </p>
 *
 * <p>
 * El terreno ocupa alturas no negativas: desde {@code y = 0} (fondo) hasta
 * {@code y = }{@value #LAYERS}{@code - 1} (superficie mas alta). Los bloques de
 * demostracion de {@link DemoScene} se colocan por encima de esta region.
 * </p>
 *
 * <p>
 * <b>No es procedural.</b> La altura de cada columna de bloques se obtiene de
 * una funcion de ruido de valor con semilla fija: el resultado es determinista
 * y
 * siempre el mismo en cada ejecucion.
 * </p>
 *
 * <p>
 * Cada bloque se clasifica por profundidad respecto a la superficie: cesped en
 * la capa superior (arena en las orillas sumergidas), tierra en las capas
 * inmediatamente inferiores y piedra en el interior. Los valles por debajo del
 * nivel del agua se rellenan con bloques de agua, formando lagos.
 * </p>
 */
public final class TerrainBuilder {

    /** Numero de chunks del terreno a lo largo de X. */
    public static final int CHUNKS_X = 8;
    /** Numero de chunks del terreno a lo largo de Y (un chunk de alto). */
    public static final int CHUNKS_Y = 1;
    /** Numero de chunks del terreno a lo largo de Z. */
    public static final int CHUNKS_Z = 8;

    /** Bloques por lado de un chunk (igual que {@link Chunk#BLOCKS_X}). */
    public static final int CHUNK_BLOCKS = Chunk.BLOCKS_X;
    /** Bloques del terreno a lo largo de X. */
    public static final int BLOCKS_X = CHUNK_BLOCKS * CHUNKS_X;
    /** Bloques del terreno a lo largo de Z. */
    public static final int BLOCKS_Z = CHUNK_BLOCKS * CHUNKS_Z;

    /**
     * Capa de bloque (coordenada Y) mas baja del terreno: el terreno empieza en y =
     * 0.
     */
    public static final int BASE_BLOCK_Y = 0;
    /** Altura en bloques del terreno (altura de un chunk en Y). */
    public static final int LAYERS = Chunk.BLOCKS_Y * CHUNKS_Y;

    /** Altura de superficie mas baja, en coordenadas de bloque (>= 0). */
    public static final int MIN_SURFACE_BLOCK_Y = 1;
    /** Altura de superficie mas alta; dentro de la altura del chunk. */
    public static final int MAX_SURFACE_BLOCK_Y = 14;
    /** Nivel del agua: las columnas por debajo se inundan. */
    public static final int WATER_LEVEL_BLOCK_Y = 4;

    /** Semillas fijas de las dos octavas de ruido (terreno determinista). */
    private static final int SEED_LOW = 20261004;
    private static final int SEED_HIGH = 1337;
    /** Frecuencias espaciales de las octavas, en ciclos por bloque. */
    private static final float FREQ_LOW = 0.035f;
    private static final float FREQ_HIGH = 0.09f;

    private TerrainBuilder() {
    }

    /**
     * Construye el terreno completo dentro de un mundo ya existente.
     *
     * <p>
     * Registra unicamente los bloques que contienen material solido o agua. Los
     * bloques de aire no se registran: el mundo trata como aire cualquier
     * posicion sin bloque, de modo que el culling de las caras es correcto y no
     * se desperdicia memoria en regiones vacias.
     * </p>
     */
    public static void build(World world) {
        for (int cy = 0; cy < CHUNKS_Y; cy++) {
            for (int cz = 0; cz < CHUNKS_Z; cz++) {
                for (int cx = 0; cx < CHUNKS_X; cx++) {
                    buildChunk(world, cx, cy, cz);
                }
            }
        }
    }

    /** Construye los bloques de un chunk y lo registra en el mundo. */
    private static void buildChunk(World world, int cx, int cy, int cz) {
        Chunk chunk = new Chunk(cx, cy, cz);
        int baseY = cy * Chunk.BLOCKS_Y;

        for (int lz = 0; lz < Chunk.BLOCKS_Z; lz++) {
            for (int lx = 0; lx < Chunk.BLOCKS_X; lx++) {
                int bx = cx * Chunk.BLOCKS_X + lx;
                int bz = cz * Chunk.BLOCKS_Z + lz;
                int top = surfaceTopBlock(bx, bz);
                boolean underwater = top < WATER_LEVEL_BLOCK_Y;

                // Columna de bloques solidos, desde el fondo del chunk hasta la
                // superficie.
                for (int by = baseY; by <= top; by++) {
                    int material = surfaceMaterial(top - by, underwater);
                    addBlock(world, chunk, bx, by, bz, material);
                }
                // Bloques de agua desde la superficie hasta el nivel del lago.
                for (int by = top + 1; by <= WATER_LEVEL_BLOCK_Y; by++) {
                    addBlock(world, chunk, bx, by, bz, MaterialRegistry.WATER);
                }
            }
        }

        world.addChunk(chunk);
    }

    /** Crea un bloque macizo (16x16x16 voxeles) del material dado y lo registra. */
    private static void addBlock(World world, Chunk chunk, int bx, int by, int bz, int material) {
        LogicalBlock block = world.addBlock(bx, by, bz);
        block.grid().fillAll(material);
        chunk.add(block);
    }

    /**
     * Material de un bloque segun su profundidad (en bloques) bajo la superficie.
     */
    private static int surfaceMaterial(int depth, boolean underwater) {
        if (underwater) {
            // Orillas y fondos: arena en la capa superior, piedra debajo.
            return depth <= 1 ? MaterialRegistry.SAND : MaterialRegistry.STONE;
        }
        if (depth == 0) {
            return MaterialRegistry.GRASS;
        }
        if (depth <= 3) {
            return MaterialRegistry.DIRT;
        }
        return MaterialRegistry.STONE;
    }

    /** Altura de superficie de una columna, en coordenadas de bloque. */
    private static int surfaceTopBlock(int bx, int bz) {
        float n = clamp01(0.65f * valueNoise(bx, bz, FREQ_LOW, SEED_LOW)
                + 0.35f * valueNoise(bx, bz, FREQ_HIGH, SEED_HIGH));
        int span = MAX_SURFACE_BLOCK_Y - MIN_SURFACE_BLOCK_Y;
        return MIN_SURFACE_BLOCK_Y + Math.round(n * span);
    }

    /**
     * Ruido de valor bilineal con interpolacion suave. Es determinista: la misma
     * coordenada y semilla producen siempre el mismo valor en {@code [0, 1]}.
     */
    private static float valueNoise(int x, int z, float frequency, int seed) {
        float fx = x * frequency;
        float fz = z * frequency;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        float tx = smooth(fx - x0);
        float tz = smooth(fz - z0);

        float v00 = hash01(x0, z0, seed);
        float v10 = hash01(x0 + 1, z0, seed);
        float v01 = hash01(x0, z0 + 1, seed);
        float v11 = hash01(x0 + 1, z0 + 1, seed);

        float a = v00 + (v10 - v00) * tx;
        float b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    /** Curva de interpolacion suave (smoothstep) para evitar aristas duras. */
    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    /** Hash entero determinista que devuelve un valor pseudoaleatorio en [0, 1). */
    private static float hash01(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0x00FFFFFF) / (float) 0x01000000;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
