package com.microvoxel.scene;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.world.BlockSource;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.World;

/**
 * Terreno de demostracion generado <b>por columnas</b>, no por bloques.
 *
 * <p>
 * Es el cambio de fondo respecto al terreno anterior, que creaba un
 * {@code LogicalBlock} (con su grilla de 16x16x16) por cada posicion solida:
 * aqui
 * el terreno <b>no existe como objetos</b>. Solo se guarda la altura de cada
 * columna (un {@code int}, x + {@value #BLOCKS_X} * z) y los estratos se
 * deducen
 * de la profundidad. Un mundo de {@value #BLOCKS_X} x {@value #BLOCKS_Z}
 * columnas
 * y 192 capas de alto (de {@value #MIN_BLOCK_Y} a
 * {@value #MAX_SURFACE_BLOCK_Y})
 * ocupa {@code 16.384 int = 64 KB} en lugar de ~9 GB.
 * </p>
 *
 * <p>
 * El mundo consulta esta clase a traves de {@link BlockSource}, de modo que
 * dibujar, chocar y calcular caras obtienen la misma respuesta sin materializar
 * nada. La subida de {@value #MIN_BLOCK_Y} a {@link World#MAX_BUILD_BLOCK_Y}
 * queda
 * como aire disponible para construir.
 * </p>
 *
 * <p>
 * <b>No es procedural</b> en el sentido de cambiante: la altura de cada columna
 * sale de un ruido de valor con semilla fija, asi que el mundo es el mismo en
 * cada
 * ejecucion. La altura se calcula <b>por bloque</b>, no por microvoxel: cada
 * bloque
 * del terreno es un cubo macizo de 16x16x16 del mismo material.
 * </p>
 *
 * <p>
 * La cache de alturas es un dato estatico: el mundo de demostracion es unico y
 * determinista, y asi cualquier consulta (bandas de chunk, altura de la escena
 * de
 * demostracion o punto de aparicion) puede resolverse sin construir el terreno.
 * </p>
 */
public final class TerrainGenerator implements BlockSource {

    /** Chunks del terreno a lo largo de X y Z. */
    public static final int CHUNKS_X = 8;
    public static final int CHUNKS_Z = 8;

    /** Bloques por lado de un chunk (igual que {@link Chunk#BLOCKS_X}). */
    public static final int CHUNK_BLOCKS = Chunk.BLOCKS_X;

    /** Ancho del terreno, en bloques. */
    public static final int BLOCKS_X = CHUNK_BLOCKS * CHUNKS_X;
    public static final int BLOCKS_Z = CHUNK_BLOCKS * CHUNKS_Z;

    /** Fondo del terreno: coincide con la cota inferior del mundo. */
    public static final int MIN_BLOCK_Y = World.MIN_BLOCK_Y;

    /** Superficie mas baja y mas alta del relieve, en coordenadas de bloque. */
    public static final int MIN_SURFACE_BLOCK_Y = 12;
    public static final int MAX_SURFACE_BLOCK_Y = 64;

    /** Nivel del agua: las columnas cuya superficie queda por debajo se inundan. */
    public static final int WATER_LEVEL_BLOCK_Y = 24;

    /** Espesor de tierra bajo el cesped. */
    private static final int DIRT_DEPTH = 3;

    /** Semillas fijas de las dos octavas de ruido (terreno determinista). */
    private static final int SEED_LOW = 20261004;
    private static final int SEED_HIGH = 1337;
    /** Frecuencias espaciales de las octavas, en ciclos por bloque. */
    private static final float FREQ_LOW = 0.035f;
    private static final float FREQ_HIGH = 0.09f;

    /** Altura de superficie de cada columna: {@code x + BLOCKS_X * z}. */
    private static int[] heightmap;

    private TerrainGenerator() {
    }

    // ------------------------------------------------------------------
    // Altura de superficie (relieve)
    // ------------------------------------------------------------------

    /**
     * Altura de la superficie de una columna, en coordenadas de bloque, o
     * {@code MIN_BLOCK_Y - 1} si la columna queda fuera del terreno (aire).
     */
    public static int surfaceTop(int blockX, int blockZ) {
        if (blockX < 0 || blockX >= BLOCKS_X || blockZ < 0 || blockZ >= BLOCKS_Z) {
            return MIN_BLOCK_Y - 1;
        }
        return heightmap()[blockX + BLOCKS_X * blockZ];
    }

    /** Altura de superficie mas baja de un rectangulo inclusivo de columnas. */
    public static int minSurfaceTop(int x0, int x1, int z0, int z1) {
        int min = Integer.MAX_VALUE;
        for (int z = Math.max(0, z0); z <= Math.min(BLOCKS_Z - 1, z1); z++) {
            for (int x = Math.max(0, x0); x <= Math.min(BLOCKS_X - 1, x1); x++) {
                min = Math.min(min, surfaceTop(x, z));
            }
        }
        return min == Integer.MAX_VALUE ? MIN_BLOCK_Y : min;
    }

    /** Altura de superficie mas alta de un rectangulo inclusivo de columnas. */
    public static int maxSurfaceTop(int x0, int x1, int z0, int z1) {
        int max = Integer.MIN_VALUE;
        for (int z = Math.max(0, z0); z <= Math.min(BLOCKS_Z - 1, z1); z++) {
            for (int x = Math.max(0, x0); x <= Math.min(BLOCKS_X - 1, x1); x++) {
                max = Math.max(max, surfaceTop(x, z));
            }
        }
        return max == Integer.MIN_VALUE ? MIN_BLOCK_Y : max;
    }

    /**
     * Banda vertical que puede producir caras en un rectangulo de columnas.
     *
     * <p>
     * Las paredes miran a las columnas vecinas, asi que se mira una columna de
     * margen. Si el rectangulo toca el borde del mundo, la muralla exterior queda
     * expuesta de arriba abajo y hay que mallarla entera hasta el fondo.
     * </p>
     */
    @Override
    public int[] faceRange(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int x0 = minBlockX - 1;
        int x1 = maxBlockX + 1;
        int z0 = minBlockZ - 1;
        int z1 = maxBlockZ + 1;
        if (x1 < 0 || z1 < 0 || x0 >= BLOCKS_X || z0 >= BLOCKS_Z) {
            return null; // el rectangulo cae fuera del terreno.
        }
        int max = Math.max(WATER_LEVEL_BLOCK_Y, maxSurfaceTop(x0, x1, z0, z1));
        if (x0 < 0 || z0 < 0 || x1 >= BLOCKS_X || z1 >= BLOCKS_Z) {
            return new int[] { MIN_BLOCK_Y, max };
        }
        return new int[] { Math.max(MIN_BLOCK_Y, minSurfaceTop(x0, x1, z0, z1) + 1), max };
    }

    /** {@code true} si la columna esta dentro del terreno. */
    public static boolean insideFootprint(int blockX, int blockZ) {
        return blockX >= 0 && blockX < BLOCKS_X && blockZ >= 0 && blockZ < BLOCKS_Z;
    }

    /** {@code true} si la superficie de la columna queda bajo el agua. */
    public static boolean isUnderwaterColumn(int blockX, int blockZ) {
        return surfaceTop(blockX, blockZ) < WATER_LEVEL_BLOCK_Y;
    }

    // ------------------------------------------------------------------
    // Bloques
    // ------------------------------------------------------------------

    @Override
    public int materialAt(int blockX, int blockY, int blockZ) {
        if (blockY < MIN_BLOCK_Y || !insideFootprint(blockX, blockZ)) {
            return 0;
        }
        int top = surfaceTop(blockX, blockZ);
        if (blockY > top) {
            // Por encima de la superficie: agua hasta el nivel del lago y aire.
            return blockY <= WATER_LEVEL_BLOCK_Y ? MaterialRegistry.WATER : 0;
        }
        return strata(top - blockY, top < WATER_LEVEL_BLOCK_Y);
    }

    @Override
    public boolean isHiddenInterior(int blockX, int blockY, int blockZ) {
        // El fondo del mundo siempre se visita: su cara inferior esta expuesta.
        if (blockY <= MIN_BLOCK_Y) {
            return false;
        }
        int top = surfaceTop(blockX, blockZ);
        if (blockY > top) {
            return false; // aire o agua: puede generar caras.
        }
        // Con la celda de arriba ocupada (blockY < top) y las cuatro columnas
        // vecinas llegando al menos hasta aqui, las seis caras quedan tapadas.
        int neighborMin = Math.min(
                Math.min(surfaceTop(blockX - 1, blockZ), surfaceTop(blockX + 1, blockZ)),
                Math.min(surfaceTop(blockX, blockZ - 1), surfaceTop(blockX, blockZ + 1)));
        return blockY < top && blockY <= neighborMin;
    }

    /**
     * Material de un bloque solido segun su profundidad bajo la superficie: cesped
     * (arena en las orillas sumergidas), tierra y piedra hacia abajo.
     */
    private static int strata(int depth, boolean underwater) {
        if (underwater) {
            return depth <= 1 ? MaterialRegistry.SAND : MaterialRegistry.STONE;
        }
        if (depth == 0) {
            return MaterialRegistry.GRASS;
        }
        if (depth <= DIRT_DEPTH) {
            return MaterialRegistry.DIRT;
        }
        return MaterialRegistry.STONE;
    }

    /**
     * Numero de bloques que describe el terreno (solido mas agua), calculado sin
     * materializar ninguno.
     */
    public static long generatedBlockCount() {
        long total = 0;
        int[] map = heightmap();
        for (int z = 0; z < BLOCKS_Z; z++) {
            for (int x = 0; x < BLOCKS_X; x++) {
                int top = map[x + BLOCKS_X * z];
                total += top - MIN_BLOCK_Y + 1;
                total += Math.max(0, WATER_LEVEL_BLOCK_Y - top);
            }
        }
        return total;
    }

    /**
     * Instala el terreno en un mundo: registra una columna de chunks por cada
     * posicion horizontal y enlaza este generador como fuente de bloques.
     *
     * <p>
     * No se crea ningun {@code LogicalBlock}: los chunks existen para agrupar el
     * orden de dibujo, no para contener bloques.
     * </p>
     */
    public static void build(World world) {
        world.setBlockSource(new TerrainGenerator());
        for (int cz = 0; cz < CHUNKS_Z; cz++) {
            for (int cx = 0; cx < CHUNKS_X; cx++) {
                world.addChunk(new Chunk(cx, 0, cz));
            }
        }
    }

    // ------------------------------------------------------------------
    // Relieve determinista
    // ------------------------------------------------------------------

    private static synchronized int[] heightmap() {
        if (heightmap == null) {
            int[] map = new int[BLOCKS_X * BLOCKS_Z];
            for (int z = 0; z < BLOCKS_Z; z++) {
                for (int x = 0; x < BLOCKS_X; x++) {
                    map[x + BLOCKS_X * z] = computeSurfaceTop(x, z);
                }
            }
            heightmap = map;
        }
        return heightmap;
    }

    private static int computeSurfaceTop(int blockX, int blockZ) {
        float n = clamp01(0.65f * valueNoise(blockX, blockZ, FREQ_LOW, SEED_LOW)
                + 0.35f * valueNoise(blockX, blockZ, FREQ_HIGH, SEED_HIGH));
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
