package com.microvoxel.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Agrupacion espacial de bloques logicos, al estilo de un <i>chunk</i> de
 * Minecraft: una cuadricula de {@value #BLOCKS_X} x {@value #BLOCKS_Y} x
 * {@value #BLOCKS_Z} bloques.
 *
 * <p>
 * La jerarquia de este motor es {@code Mundo -> chunks -> bloques logicos ->
 * 16x16x16 microvoxels}. Un chunk <b>no</b> es un bloque: es un conjunto de
 * bloques, y cada uno de ellos es un cubo macizo de 16x16x16 voxeles. El chunk
 * solo agrupa bloques para organizar el mundo y para poder construir una unica
 * malla combinada (menos draw calls) sin alterar la representacion interna de
 * cada bloque.
 * </p>
 *
 * <p>
 * Los bloques de un chunk viven en el {@link World} como cualquier otro bloque
 * (el mundo sigue siendo la autoridad de ocupacion global). El chunk solo
 * mantiene la lista de los que le pertenecen.
 * </p>
 */
public final class Chunk {

    /** Numero de bloques por lado del chunk en X. */
    public static final int BLOCKS_X = 16;
    /** Numero de bloques por lado del chunk en Y. */
    public static final int BLOCKS_Y = 16;
    /** Numero de bloques por lado del chunk en Z. */
    public static final int BLOCKS_Z = 16;

    private final int chunkX;
    private final int chunkY;
    private final int chunkZ;
    private final List<LogicalBlock> blocks = new ArrayList<>();

    public Chunk(int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkY() {
        return chunkY;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** Registra un bloque como perteneciente a este chunk. */
    public void add(LogicalBlock block) {
        if (block == null) {
            throw new IllegalArgumentException("El chunk no admite bloques nulos");
        }
        blocks.add(block);
    }

    public Collection<LogicalBlock> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public int blockCount() {
        return blocks.size();
    }

    // ------------------------------------------------------------------
    // Limites espaciales del chunk en unidades de mundo (bloques unitarios).
    // ------------------------------------------------------------------

    public float minWorldX() {
        return chunkX * BLOCKS_X;
    }

    public float maxWorldX() {
        return (chunkX + 1) * BLOCKS_X;
    }

    public float minWorldY() {
        return chunkY * BLOCKS_Y;
    }

    public float maxWorldY() {
        return (chunkY + 1) * BLOCKS_Y;
    }

    public float minWorldZ() {
        return chunkZ * BLOCKS_Z;
    }

    public float maxWorldZ() {
        return (chunkZ + 1) * BLOCKS_Z;
    }

    @Override
    public String toString() {
        return "Chunk(" + chunkX + ", " + chunkY + ", " + chunkZ + ") [bloques=" + blocks.size() + "]";
    }
}
