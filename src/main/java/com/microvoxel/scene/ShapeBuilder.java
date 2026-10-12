package com.microvoxel.scene;

import com.microvoxel.world.MicroVoxelGrid;

/**
 * Utilidades para construir formas de microvoxels dentro de una grilla.
 *
 * <p>Todas las formas se definen escribiendo o vaciando celdas: nunca mediante
 * modelos externos ni geometria tradicional anadida como decoracion. Las
 * coordenadas son locales a la grilla y el rectangulo de {@code fillBox} /
 * {@code clearBox} es inclusivo.</p>
 *
 * <p>Las operaciones con {@code x1 &lt; x0} se normalizan automaticamente, de
 * modo que es comodo escribir rangos en cualquier orden.</p>
 */
public final class ShapeBuilder {

    private ShapeBuilder() {
    }

    /** Rellena un cuboide inclusivo, recortado a los limites de la grilla. */
    public static int box(MicroVoxelGrid grid, int x0, int y0, int z0, int x1, int y1, int z1, int material) {
        return grid.fillBox(min(x0, x1), min(y0, y1), min(z0, z1),
                max(x0, x1), max(y0, y1), max(z0, z1), material);
    }

    /** Vacia un cuboide inclusivo, recortado a los limites de la grilla. */
    public static int carveBox(MicroVoxelGrid grid, int x0, int y0, int z0, int x1, int y1, int z1) {
        return grid.clearBox(min(x0, x1), min(y0, y1), min(z0, z1),
                max(x0, x1), max(y0, y1), max(z0, z1));
    }

    /** Vacia toda la grilla. */
    public static void clear(MicroVoxelGrid grid) {
        grid.clearAll();
    }

    /** Rellena toda la grilla con un material. */
    public static void solid(MicroVoxelGrid grid, int material) {
        grid.fillAll(material);
    }

    /**
     * Túnel que atraviesa la grilla a lo largo del eje Z, centrado en X e Y.
     *
     * @param size    lado de la seccion transversal del tunel (en microvoxels)
     * @param material material de las paredes que se conservan
     */
    public static void tunnelAlongZ(MicroVoxelGrid grid, int size, int material) {
        int cx = grid.sizeX() / 2;
        int cy = grid.sizeY() / 2;
        int half = Math.max(0, size / 2);
        int lo = half * 2 == size ? half : half;
        int x0 = cx - lo;
        int x1 = x0 + size - 1;
        int y0 = cy - lo;
        int y1 = y0 + size - 1;
        carveBox(grid, x0, y0, 0, x1, y1, grid.sizeZ() - 1);
    }

    /** Túnel que atraviesa la grilla a lo largo del eje X. */
    public static void tunnelAlongX(MicroVoxelGrid grid, int size) {
        int cy = grid.sizeY() / 2;
        int cz = grid.sizeZ() / 2;
        int lo = size / 2;
        carveBox(grid, 0, cy - lo, cz - lo, grid.sizeX() - 1, cy - lo + size - 1, cz - lo + size - 1);
    }

    /** Túnel que atraviesa la grilla a lo largo del eje Y (pozo vertical). */
    public static void tunnelAlongY(MicroVoxelGrid grid, int size) {
        int cx = grid.sizeX() / 2;
        int cz = grid.sizeZ() / 2;
        int lo = size / 2;
        carveBox(grid, cx - lo, 0, cz - lo, cx - lo + size - 1, grid.sizeY() - 1, cz - lo + size - 1);
    }

    /**
     * Cavidad interior cerrada: vacia una region que no toca las caras de la
     * grilla, de modo que queda encerrada dentro del material.
     */
    public static void cavity(MicroVoxelGrid grid, int margin, int material) {
        solid(grid, material);
        carveBox(grid, margin, margin, margin,
                grid.sizeX() - 1 - margin, grid.sizeY() - 1 - margin, grid.sizeZ() - 1 - margin);
    }

    /**
     * Cavidad abierta por arriba: como {@link #cavity} pero deja un hueco de
     * acceso en la cara superior para que la camara pueda entrar.
     */
    public static void openCavity(MicroVoxelGrid grid, int margin, int material) {
        cavity(grid, margin, material);
        int cx = grid.sizeX() / 2;
        int cz = grid.sizeZ() / 2;
        carveBox(grid, cx, grid.sizeY() - 1, cz, cx, grid.sizeY() - 1, cz);
    }

    /**
     * Escalera de niveles: cada nivel es un escalon que desciende (o asciende)
     * una fila de microvoxels. La forma se apoya en el suelo de la grilla.
     *
     * @param steps numero de escalones
     * @param depth grosor de cada escalon en Z
     */
    public static void stairs(MicroVoxelGrid grid, int steps, int depth, int material) {
        int y = 0;
        for (int i = 0; i < steps; i++) {
            int z0 = i * depth;
            int z1 = Math.min(grid.sizeZ() - 1, z0 + depth - 1);
            if (z0 > grid.sizeZ() - 1) {
                break;
            }
            // Cada escalon es una losa de un microvoxel de alto, creciendo en X.
            int height = Math.min(grid.sizeY() - 1, y + i);
            box(grid, 0, height, z0, grid.sizeX() - 1, height, z1, material);
        }
    }

    /**
     * Arco aproximado con celdas: un muro con una abertura semicircular tallada.
     * El arco se apoya en la base de la grilla.
     */
    public static void arch(MicroVoxelGrid grid, int material) {
        solid(grid, material);
        int cx = (grid.sizeX() - 1) / 2;
        int cz = (grid.sizeZ() - 1) / 2;
        float radius = Math.min(grid.sizeX(), grid.sizeY()) * 0.35f;
        float radiusSq = radius * radius;
        for (int z = 0; z < grid.sizeZ(); z++) {
            for (int y = 0; y < grid.sizeY(); y++) {
                for (int x = 0; x < grid.sizeX(); x++) {
                    float dx = x - cx;
                    float dy = y - 0.5f;
                    if (dx * dx + dy * dy <= radiusSq) {
                        grid.clear(x, y, z);
                    }
                }
            }
        }
        // Recorta los lados para que quede un arco reconocible y no un bloque hueco.
        int thickness = Math.max(1, grid.sizeZ() / 4);
        carveBox(grid, 0, 0, 0, grid.sizeX() - 1, grid.sizeY() - 1, cz - thickness);
        carveBox(grid, 0, 0, cz + thickness, grid.sizeX() - 1, grid.sizeY() - 1, grid.sizeZ() - 1);
    }

    /**
     * Estructura asimetrica: una base maciza con protuberancias y huecos
     * irregulares, deliberadamente no simetrica en ningun eje.
     */
    public static void asymmetric(MicroVoxelGrid grid, int material) {
        int s = grid.sizeX();
        // Base.
        box(grid, 0, 0, 0, s - 1, 2, s - 1, material);
        // Protuberancia alta descentrada.
        box(grid, 1, 3, 1, 4, 10, 4, material);
        // Voladizo hacia un lado.
        box(grid, 5, 5, 1, 12, 6, 3, material);
        // Bloque flotante en una esquina opuesta.
        box(grid, 12, 8, 12, 14, 12, 14, material);
        // Hueco tallado en la base.
        carveBox(grid, 6, 0, 6, 10, 1, 10);
        // Muesca en un lateral.
        carveBox(grid, 0, 3, 8, 3, 6, 11);
    }

    /**
     * Escultura de dos materiales entrelazados en las tres dimensiones: util
     * para comprobar que la ocupacion es realmente volumetrica.
     */
    public static void mixedMaterials(MicroVoxelGrid grid, int primary, int secondary, int accent) {
        int s = grid.sizeX();
        for (int z = 0; z < grid.sizeZ(); z++) {
            for (int y = 0; y < grid.sizeY(); y++) {
                for (int x = 0; x < grid.sizeX(); x++) {
                    // Patron diagonal 3D: bandas por (x + y + z).
                    int band = (x + y + z) % 3;
                    int mat = band == 0 ? primary : (band == 1 ? secondary : accent);
                    // Vacía las esquinas para que la forma no sea un cubo completo.
                    boolean corner = (x < 2 || x >= s - 2) && (y < 2 || y >= s - 2);
                    if (!corner) {
                        grid.setMaterial(x, y, z, mat);
                    }
                }
            }
        }
    }

    /**
     * Componentes separados: varias regiones ocupadas sin conexion entre si,
     * distribuidas en las tres dimensiones.
     */
    public static void disconnectedComponents(MicroVoxelGrid grid, int a, int b, int c) {
        box(grid, 1, 1, 1, 4, 4, 4, a);        // cubo flotante bajo
        box(grid, 10, 2, 1, 14, 3, 6, b);      // losa
        box(grid, 2, 8, 9, 6, 13, 13, c);      // torre alta
        box(grid, 11, 10, 11, 14, 14, 14, a);  // cubo flotante alto
        box(grid, 1, 10, 3, 3, 12, 5, b);      // cubo flotante medio
    }

    /**
     * Rejilla hueca tipo jaula: paredes finas con aberturas, para comprobar que
     * se ve el interior a traves de las perforaciones.
     */
    public static void cage(MicroVoxelGrid grid, int material) {
        int s = grid.sizeX();
        int m = s / 4;
        // Seis paredes finas con un hueco central en cada una.
        box(grid, m, m, 0, s - 1 - m, s - 1 - m, 0, material);
        box(grid, m, m, s - 1, s - 1 - m, s - 1 - m, s - 1, material);
        box(grid, 0, m, m, 0, s - 1 - m, s - 1 - m, material);
        box(grid, s - 1, m, m, s - 1, s - 1 - m, s - 1 - m, material);
        box(grid, m, 0, m, s - 1 - m, 0, s - 1 - m, material);
        box(grid, m, s - 1, m, s - 1 - m, s - 1, s - 1 - m, material);
        // Perforaciones centrales.
        int lo = s / 2 - 1;
        int hi = s / 2 + 1;
        carveBox(grid, lo, lo, 0, hi, hi, 0);
        carveBox(grid, lo, lo, s - 1, hi, hi, s - 1);
        carveBox(grid, 0, lo, lo, 0, hi, hi);
        carveBox(grid, s - 1, lo, lo, s - 1, hi, hi);
        carveBox(grid, lo, 0, lo, hi, 0, hi);
        carveBox(grid, lo, s - 1, lo, hi, s - 1, hi);
    }

    private static int min(int a, int b) {
        return Math.min(a, b);
    }

    private static int max(int a, int b) {
        return Math.max(a, b);
    }
}
