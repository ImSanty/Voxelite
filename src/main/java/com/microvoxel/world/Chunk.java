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
 * mantiene la lista de los que le pertenecen: los <b>materializados</b>, que son
 * los editados y los de la escena de demostracion. El terreno generado no crea
 * bloques, asi que un chunk de terreno normal esta <b>vacio</b>.
 * </p>
 *
 * <p>
 * Un chunk tambien guarda la <b>banda vertical</b> que su malla necesita
 * ({@link #bandMinBlockY()} - {@link #bandMaxBlockY()}). Se calcula al generar la
 * malla (a partir del relieve y de los bloques que contiene) y sirve para dos
 * cosas: acotar el recorrido del generador de malla y decidir el descarte por
 * frustum sin tocar la malla.
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

    /** Banda vertical de la malla, en coordenadas de bloque (inclusiva). */
    private int bandMinBlockY;
    private int bandMaxBlockY;

    /** Marca de reconstruccion propia: una edicion puede afectar a la banda. */
    private boolean dirty = true;

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

    /** {@code true} si el chunk guarda bloques materializados (editados o demo). */
    public boolean hasMaterializedBlocks() {
        return !blocks.isEmpty();
    }

    // ------------------------------------------------------------------
    // Banda vertical de la malla.
    // ------------------------------------------------------------------

    /** Limite inferior (inclusive) de la banda que hay que mallar. */
    public int bandMinBlockY() {
        return bandMinBlockY;
    }

    /** Limite superior (inclusive) de la banda que hay que mallar. */
    public int bandMaxBlockY() {
        return bandMaxBlockY;
    }

    /** Fija la banda vertical que la malla de este chunk cubre. */
    public void setBand(int minBlockY, int maxBlockY) {
        this.bandMinBlockY = minBlockY;
        this.bandMaxBlockY = maxBlockY;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void markClean() {
        dirty = false;
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

    /** Altura minima del chunk: el fondo de su banda de malla. */
    public float minWorldY() {
        return bandMinBlockY;
    }

    /** Altura maxima del chunk: el techo de su banda de malla. */
    public float maxWorldY() {
        return bandMaxBlockY + 1.0f;
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
