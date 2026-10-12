package com.microvoxel.world;

/**
 * Grilla tridimensional compacta de microvoxels.
 *
 * <p>El almacenamiento es un unico {@code byte[]} plano: un identificador de
 * material por celda. {@code 0} significa vacio (aire); cualquier valor positivo
 * es un material registrado. La ocupacion se deduce del identificador, por lo
 * que no se guarda ningun objeto por microvoxel.</p>
 *
 * <p>La resolucion es un parametro del constructor y no esta fijada a 16 en
 * ningun punto. La indexacion es:</p>
 *
 * <pre>index = x + sizeX * (y + sizeY * z)</pre>
 *
 * <p>Es decir, X es el eje mas rapido, luego Y y por ultimo Z. Esta formula es
 * biyectiva dentro de los limites, de modo que celdas distintas nunca comparten
 * indice ni se pisan entre si.</p>
 */
public final class MicroVoxelGrid {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] cells;

    /**
     * Numero de celdas ocupadas, mantenido de forma incremental en cada
     * escritura. Sin esto, {@link #countOccupied()} tendria que recorrer las
     * {@code sizeX * sizeY * sizeZ} celdas cada vez que se pide (cientos de
     * millones de bytes por fotograma con el terreno de demostracion).
     */
    private int occupiedCount;

    public MicroVoxelGrid(int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException(
                    "La resolucion debe ser positiva: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        long total = (long) sizeX * sizeY * sizeZ;
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Resolucion demasiado grande: " + total + " celdas");
        }
        this.cells = new byte[(int) total];
    }

    /** Construye una grilla cubica. */
    public MicroVoxelGrid(int size) {
        this(size, size, size);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /** Numero total de celdas almacenadas (ocupadas o no). */
    public int cellCount() {
        return cells.length;
    }

    /** Comprueba si una coordenada local cae dentro de la grilla. */
    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    /**
     * Convierte coordenadas locales a indice lineal.
     *
     * @throws IndexOutOfBoundsException si la coordenada esta fuera de limites
     */
    public int index(int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            throw new IndexOutOfBoundsException(
                    "Microvoxel fuera de limites (" + x + ", " + y + ", " + z + ") en grilla "
                            + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        return x + sizeX * (y + sizeY * z);
    }

    /** Material de una celda; {@code 0} si esta vacia. */
    public int getMaterial(int x, int y, int z) {
        return cells[index(x, y, z)];
    }

    /**
     * Material de una celda o {@code 0} si la coordenada queda fuera de limites.
     * Util en los bordes de la grilla.
     */
    public int getMaterialOrEmpty(int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return 0;
        }
        return cells[index(x, y, z)];
    }

    public boolean isOccupied(int x, int y, int z) {
        return getMaterial(x, y, z) != 0;
    }

    /** Asigna un material a una celda. {@code 0} equivale a vaciarla. */
    public void setMaterial(int x, int y, int z, int materialId) {
        if (materialId < 0 || materialId > 255) {
            throw new IllegalArgumentException("Identificador de material fuera del rango byte: " + materialId);
        }
        int i = index(x, y, z);
        byte nuevo = (byte) materialId;
        byte anterior = cells[i];
        if (anterior == 0 && nuevo != 0) {
            occupiedCount++;
        } else if (anterior != 0 && nuevo == 0) {
            occupiedCount--;
        }
        cells[i] = nuevo;
    }

    public void clear(int x, int y, int z) {
        int i = index(x, y, z);
        if (cells[i] != 0) {
            cells[i] = 0;
            occupiedCount--;
        }
    }

    /** Vacia toda la grilla. */
    public void clearAll() {
        java.util.Arrays.fill(cells, (byte) 0);
        occupiedCount = 0;
    }

    /**
     * Rellena un volumen rectangular inclusivo con un material.
     * El rectangulo se recorta a los limites de la grilla.
     *
     * @return numero de celdas escritas
     */
    public int fillBox(int x0, int y0, int z0, int x1, int y1, int z1, int materialId) {
        byte nuevo = (byte) materialId;
        int written = 0;
        for (int z = Math.max(0, z0); z <= Math.min(sizeZ - 1, z1); z++) {
            for (int y = Math.max(0, y0); y <= Math.min(sizeY - 1, y1); y++) {
                for (int x = Math.max(0, x0); x <= Math.min(sizeX - 1, x1); x++) {
                    int i = index(x, y, z);
                    byte anterior = cells[i];
                    if (anterior == 0 && nuevo != 0) {
                        occupiedCount++;
                    } else if (anterior != 0 && nuevo == 0) {
                        occupiedCount--;
                    }
                    cells[i] = nuevo;
                    written++;
                }
            }
        }
        return written;
    }

    /**
     * Vacia un volumen rectangular inclusivo.
     *
     * @return numero de celdas vaciadas que estaban ocupadas
     */
    public int clearBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        int cleared = 0;
        for (int z = Math.max(0, z0); z <= Math.min(sizeZ - 1, z1); z++) {
            for (int y = Math.max(0, y0); y <= Math.min(sizeY - 1, y1); y++) {
                for (int x = Math.max(0, x0); x <= Math.min(sizeX - 1, x1); x++) {
                    int i = index(x, y, z);
                    if (cells[i] != 0) {
                        cleared++;
                    }
                    cells[i] = 0;
                }
            }
        }
        occupiedCount -= cleared;
        return cleared;
    }

    /** Rellena toda la grilla con un material. */
    public void fillAll(int materialId) {
        if (materialId < 0 || materialId > 255) {
            throw new IllegalArgumentException("Identificador de material fuera del rango byte: " + materialId);
        }
        java.util.Arrays.fill(cells, (byte) materialId);
        occupiedCount = materialId == 0 ? 0 : cells.length;
    }

    /**
     * Numero de microvoxels ocupados (con material distinto de aire).
     *
     * <p>Coste constante: el contador se mantiene actualizado en cada escritura.</p>
     */
    public int countOccupied() {
        return occupiedCount;
    }

    /** {@code true} si no hay ninguna celda ocupada. Coste constante. */
    public boolean isEmpty() {
        return occupiedCount == 0;
    }

    /**
     * Acceso directo al array interno, solo para lecturas de alto rendimiento (el
     * generador de malla y las pruebas).
     *
     * <p>Escribir en este array al margen de los metodos de la clase deja
     * desincronizado el contador de ocupacion, por lo que solo debe usarse para
     * leer.</p>
     */
    public byte[] rawCells() {
        return cells;
    }
}
