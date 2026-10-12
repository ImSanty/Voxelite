package com.microvoxel.world;

import com.microvoxel.material.MaterialRegistry;

/**
 * Bloque logico del mundo.
 *
 * <p>Es el equivalente conceptual al bloque tradicional de un juego de voxels,
 * pero su forma no es un cubo fijo: viene determinada por la distribucion de
 * ocupacion de su {@link MicroVoxelGrid} interno. Un bloque puede ser solido,
 * estar perforado, tener cavidades o contener varios componentes separados.</p>
 *
 * <p>Ocupa exactamente una unidad de mundo por eje, con independencia de su
 * resolucion interna. Con resolucion 16, cada microvoxel mide 1/16 de unidad.</p>
 *
 * <p>Esta clase no contiene ninguna llamada a OpenGL: los datos del mundo y la
 * geometria renderizable estan deliberadamente separados.</p>
 */
public final class LogicalBlock {

    private final BlockPosition position;
    private final MicroVoxelGrid grid;

    /** Marca de reconstruccion: la malla se regenera solo cuando los datos cambian. */
    private boolean dirty = true;

    /** Clasificacion cacheada de la grilla y version de la que procede. */
    private int uniformCode;
    private int uniformVersion = -1;

    public LogicalBlock(BlockPosition position, MicroVoxelGrid grid) {
        if (position == null) {
            throw new IllegalArgumentException("El bloque necesita una posicion");
        }
        if (grid == null) {
            throw new IllegalArgumentException("El bloque necesita una grilla");
        }
        this.position = position;
        this.grid = grid;
    }

    public LogicalBlock(BlockPosition position, int resX, int resY, int resZ) {
        this(position, new MicroVoxelGrid(resX, resY, resZ));
    }

    public BlockPosition position() {
        return position;
    }

    public MicroVoxelGrid grid() {
        return grid;
    }

    public int resX() {
        return grid.sizeX();
    }

    public int resY() {
        return grid.sizeY();
    }

    public int resZ() {
        return grid.sizeZ();
    }

    /** Dimension de un microvoxel en unidades de mundo. */
    public float microVoxelSizeX() {
        return 1.0f / grid.sizeX();
    }

    public float microVoxelSizeY() {
        return 1.0f / grid.sizeY();
    }

    public float microVoxelSizeZ() {
        return 1.0f / grid.sizeZ();
    }

    public int countOccupied() {
        return grid.countOccupied();
    }

    /**
     * Clasifica el contenido del bloque de forma barata para el generador de
     * malla del terreno:
     *
     * <ul>
     *   <li>{@code 0}: el bloque esta completamente vacio (aire).</li>
     *   <li>{@code > 0}: el bloque esta <b>completamente ocupado por un unico
     *       material</b>, que es el devuelto. Es el caso de un "bloque de
     *       Minecraft": un cubo macizo de 16x16x16 microvoxels del mismo
     *       material.</li>
     *   <li>{@code -1}: la distribucion no es uniforme (varias celdas distintas
     *       o parcialmente ocupado).</li>
     * </ul>
     */
    public int uniformMaterial() {
        // La clasificacion recorre las 4.096 celdas, asi que se cachea y solo se
        // recalcula cuando la grilla cambia de version. Es la consulta mas repetida
        // del generador de malla y de la colision: sin cache, remallar un chunk
        // costaba mas en clasificar que en emitir caras.
        int version = grid.version();
        if (uniformVersion != version) {
            uniformCode = classify();
            uniformVersion = version;
        }
        return uniformCode;
    }

    private int classify() {
        byte[] cells = grid.rawCells();
        if (cells.length == 0) {
            return 0;
        }
        int first = cells[0] & 0xFF;
        for (int i = 1; i < cells.length; i++) {
            if ((cells[i] & 0xFF) != first) {
                return -1;
            }
        }
        return first;
    }

    public boolean isEmpty() {
        return grid.isEmpty();
    }

    // ------------------------------------------------------------------
    // Consulta / edicion de microvoxels (coordenadas locales).
    // ------------------------------------------------------------------

    public int getMaterial(int lx, int ly, int lz) {
        return grid.getMaterial(lx, ly, lz);
    }

    public void setMaterial(int lx, int ly, int lz, int materialId) {
        grid.setMaterial(lx, ly, lz, materialId);
        dirty = true;
    }

    public void clear(int lx, int ly, int lz) {
        grid.clear(lx, ly, lz);
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Coordenadas globales de microvoxel.
    // ------------------------------------------------------------------

    public int globalMicroX(int lx) {
        return position.x() * grid.sizeX() + lx;
    }

    public int globalMicroY(int ly) {
        return position.y() * grid.sizeY() + ly;
    }

    public int globalMicroZ(int lz) {
        return position.z() * grid.sizeZ() + lz;
    }

    /**
     * Convierte una coordenada global de microvoxel en local dentro de este
     * bloque. No comprueba pertenencia: usar junto a {@link #containsGlobalMicro}.
     */
    public int toLocalX(int globalX) {
        return Math.floorMod(globalX, grid.sizeX());
    }

    public int toLocalY(int globalY) {
        return Math.floorMod(globalY, grid.sizeY());
    }

    public int toLocalZ(int globalZ) {
        return Math.floorMod(globalZ, grid.sizeZ());
    }

    /** Comprueba si una coordenada global de microvoxel pertenece a este bloque. */
    public boolean containsGlobalMicro(int gx, int gy, int gz) {
        return BlockPosition.ofGlobalMicroVoxel(gx, gy, gz, resX(), resY(), resZ()).equals(position);
    }

    /** Material de una coordenada global de microvoxel, o {@code 0} si queda fuera. */
    public int getMaterialGlobal(int gx, int gy, int gz) {
        if (!containsGlobalMicro(gx, gy, gz)) {
            return 0;
        }
        return grid.getMaterial(toLocalX(gx), toLocalY(gy), toLocalZ(gz));
    }

    // ------------------------------------------------------------------
    // Limites espaciales (unidades de mundo).
    // ------------------------------------------------------------------

    public float minWorldX() {
        return position.minWorldX();
    }

    public float minWorldY() {
        return position.minWorldY();
    }

    public float minWorldZ() {
        return position.minWorldZ();
    }

    public float maxWorldX() {
        return position.x() + 1.0f;
    }

    public float maxWorldY() {
        return position.y() + 1.0f;
    }

    public float maxWorldZ() {
        return position.z() + 1.0f;
    }

    /** Centro del bloque en unidades de mundo, util para posicionar la camara. */
    public org.joml.Vector3f center() {
        return new org.joml.Vector3f(
                position.x() + 0.5f,
                position.y() + 0.5f,
                position.z() + 0.5f);
    }

    // ------------------------------------------------------------------
    // Estado de reconstruccion de malla.
    // ------------------------------------------------------------------

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void markClean() {
        dirty = false;
    }

    /**
     * Valida que todos los materiales usados en la grilla existan en el registro.
     * Pensado para depuracion y pruebas.
     */
    public boolean validateMaterials(MaterialRegistry registry) {
        byte[] cells = grid.rawCells();
        for (byte b : cells) {
            int id = b & 0xFF;
            if (id != 0 && !registry.contains(id)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "LogicalBlock" + position + " [" + resX() + "x" + resY() + "x" + resZ()
                + ", ocupados=" + countOccupied() + "]";
    }
}
