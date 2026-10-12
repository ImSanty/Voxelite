package com.microvoxel.world;

/**
 * Posicion entera de un bloque logico dentro del mundo.
 *
 * <p>Sistema de coordenadas 1 de los cuatro definidos por el motor:</p>
 * <ol>
 *   <li><b>Coordenadas de bloque logico</b>: esta clase. Un bloque ocupa la
 *       celda unitaria {@code [x, x+1) x [y, y+1) x [z, z+1)} en unidades de mundo.</li>
 *   <li><b>Coordenadas locales de microvoxel</b>: {@code (lx, ly, lz)} dentro de
 *       un {@link LogicalBlock}, en el rango {@code [0, resolucion)}.</li>
 *   <li><b>Coordenadas globales de microvoxel</b>: {@code (gx, gy, gz)} enteras,
 *       resultado de {@code gx = bx * resolucionX + lx}. Identifican de forma
 *       unica cualquier celda del mundo y son la base del culling entre bloques.</li>
 *   <li><b>Coordenadas de mundo flotantes</b>: unidades de mundo usadas por la
 *       camara y el renderizador.</li>
 * </ol>
 *
 * <p>Las conversiones 1&lt;-&gt;2&lt;-&gt;3 se realizan con division y modulo
 * <i>con signo matematico</i> ({@link Math#floorDiv(int, int)} /
 * {@link Math#floorMod(int, int)}), de modo que las posiciones negativas se
 * comportan igual que las positivas. Esto es lo que garantiza que la ultima
 * celda de un bloque y la primera del bloque vecino compartan exactamente la
 * misma frontera, sin separaciones ni solapes.</p>
 *
 * @param x indice entero del bloque en el eje X
 * @param y indice entero del bloque en el eje Y
 * @param z indice entero del bloque en el eje Z
 */
public record BlockPosition(int x, int y, int z) {

    /** Posicion del bloque que contiene una coordenada global de microvoxel dada. */
    public static BlockPosition ofGlobalMicroVoxel(int gx, int gy, int gz, int resX, int resY, int resZ) {
        if (resX <= 0 || resY <= 0 || resZ <= 0) {
            throw new IllegalArgumentException("La resolucion debe ser positiva");
        }
        return new BlockPosition(
                Math.floorDiv(gx, resX),
                Math.floorDiv(gy, resY),
                Math.floorDiv(gz, resZ));
    }

    public BlockPosition offset(int dx, int dy, int dz) {
        return new BlockPosition(x + dx, y + dy, z + dz);
    }

    /** Esquina minima del bloque en unidades de mundo (coincide con su indice entero). */
    public float minWorldX() {
        return x;
    }

    public float minWorldY() {
        return y;
    }

    public float minWorldZ() {
        return z;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
