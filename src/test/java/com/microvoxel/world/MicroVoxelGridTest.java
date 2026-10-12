package com.microvoxel.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifica la representacion de datos volumetricos (seccion 13.1 del enunciado).
 */
class MicroVoxelGridTest {

    @Test
    void unaGrillaNuevaEstaVacia() {
        MicroVoxelGrid grid = new MicroVoxelGrid(16, 16, 16);
        assertTrue(grid.isEmpty());
        assertEquals(0, grid.countOccupied());
        assertEquals(16 * 16 * 16, grid.cellCount());
        assertEquals(0, grid.getMaterial(0, 0, 0));
        assertEquals(0, grid.getMaterial(15, 15, 15));
    }

    @Test
    void sePuedeOcuparYVaciarrUnaCelda() {
        MicroVoxelGrid grid = new MicroVoxelGrid(8);
        grid.setMaterial(3, 4, 5, 7);
        assertTrue(grid.isOccupied(3, 4, 5));
        assertEquals(7, grid.getMaterial(3, 4, 5));
        assertEquals(1, grid.countOccupied());

        grid.clear(3, 4, 5);
        assertFalse(grid.isOccupied(3, 4, 5));
        assertEquals(0, grid.getMaterial(3, 4, 5));
        assertEquals(0, grid.countOccupied());
    }

    @Test
    void elMaterialDeUnaCeldaSeRecuperaCorrectamente() {
        MicroVoxelGrid grid = new MicroVoxelGrid(4, 5, 6);
        grid.setMaterial(1, 2, 3, 42);
        assertEquals(42, grid.getMaterial(1, 2, 3));
        // Las celdas vecinas siguen vacias.
        assertEquals(0, grid.getMaterial(1, 2, 2));
        assertEquals(0, grid.getMaterial(1, 1, 3));
        assertEquals(0, grid.getMaterial(2, 2, 3));
    }

    @Test
    void lasCoordenadasFueraDeLimitesSeGestionanDeFormaExplicita() {
        MicroVoxelGrid grid = new MicroVoxelGrid(4, 4, 4);
        // Lectura estricta: lanza.
        assertThrows(IndexOutOfBoundsException.class, () -> grid.getMaterial(-1, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.getMaterial(0, 4, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.getMaterial(0, 0, 4));
        // Escritura estricta: lanza.
        assertThrows(IndexOutOfBoundsException.class, () -> grid.setMaterial(4, 0, 0, 1));
        // Variante tolerante: devuelve vacio.
        assertEquals(0, grid.getMaterialOrEmpty(-1, -1, -1));
        assertEquals(0, grid.getMaterialOrEmpty(4, 4, 4));
        assertFalse(grid.inBounds(-1, 0, 0));
        assertFalse(grid.inBounds(0, 0, 4));
        assertTrue(grid.inBounds(3, 3, 3));
    }

    @Test
    void laConversionDeCoordenadasNoMezclaEjesNiSobrescribeCeldas() {
        MicroVoxelGrid grid = new MicroVoxelGrid(3, 4, 5);
        Set<Integer> indices = new HashSet<>();
        int material = 1;
        for (int z = 0; z < grid.sizeZ(); z++) {
            for (int y = 0; y < grid.sizeY(); y++) {
                for (int x = 0; x < grid.sizeX(); x++) {
                    int index = grid.index(x, y, z);
                    // Cada celda tiene un indice unico.
                    assertTrue(indices.add(index),
                            "Indice repetido en (" + x + ", " + y + ", " + z + ")");
                    // Y cada celda recibe su propio material sin pisar a otras.
                    grid.setMaterial(x, y, z, material++);
                }
            }
        }
        assertEquals(3 * 4 * 5, indices.size());
        // Comprobacion explicita de que los ejes no se mezclan.
        assertEquals(grid.index(1, 0, 0) + 1, grid.index(2, 0, 0));
        assertNotEquals(grid.index(1, 0, 0), grid.index(0, 1, 0));
        assertNotEquals(grid.index(0, 1, 0), grid.index(0, 0, 1));
        // Todos los materiales son distintos y recuperables.
        Set<Integer> materials = new HashSet<>();
        for (int z = 0; z < grid.sizeZ(); z++) {
            for (int y = 0; y < grid.sizeY(); y++) {
                for (int x = 0; x < grid.sizeX(); x++) {
                    materials.add(grid.getMaterial(x, y, z));
                }
            }
        }
        assertEquals(3 * 4 * 5, materials.size());
    }

    @Test
    void rellenarYVaciarVolumenesRectangulares() {
        MicroVoxelGrid grid = new MicroVoxelGrid(8);
        int written = grid.fillBox(1, 1, 1, 3, 3, 3, 5);
        assertEquals(27, written);
        assertEquals(27, grid.countOccupied());
        // Un cuboide completamente contenido tiene 27 celdas; las de fuera siguen vacias.
        assertEquals(0, grid.getMaterial(0, 0, 0));
        assertEquals(0, grid.getMaterial(4, 1, 1));

        int cleared = grid.clearBox(1, 1, 1, 1, 3, 3);
        assertEquals(9, cleared);
        assertEquals(18, grid.countOccupied());
    }

    @Test
    void laResolucionNoEstaFijadaYSeReflejaEnLaGrilla() {
        MicroVoxelGrid grid = new MicroVoxelGrid(32, 8, 4);
        assertEquals(32, grid.sizeX());
        assertEquals(8, grid.sizeY());
        assertEquals(4, grid.sizeZ());
        assertEquals(32 * 8 * 4, grid.cellCount());
        grid.setMaterial(31, 7, 3, 1);
        assertTrue(grid.isOccupied(31, 7, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.setMaterial(32, 0, 0, 1));
    }

    @Test
    void laResolucionDebeSerPositiva() {
        assertThrows(IllegalArgumentException.class, () -> new MicroVoxelGrid(0));
        assertThrows(IllegalArgumentException.class, () -> new MicroVoxelGrid(4, -1, 4));
    }

    @Test
    void elContadorDeOcupadosCoincideConElRecuentoDirectoEnCadaOperacion() {
        MicroVoxelGrid grid = new MicroVoxelGrid(6, 5, 7);
        assertCoincide(grid, "grilla nueva");

        grid.setMaterial(1, 1, 1, 3);
        assertCoincide(grid, "setMaterial en celda vacia");
        grid.setMaterial(1, 1, 1, 4);
        assertCoincide(grid, "sobrescritura con otro material");
        grid.setMaterial(1, 1, 1, 0);
        assertCoincide(grid, "setMaterial con aire");
        grid.setMaterial(1, 1, 1, 0);
        assertCoincide(grid, "setMaterial con aire sobre celda ya vacia");

        grid.fillBox(0, 0, 0, 3, 3, 3, 5);
        assertCoincide(grid, "fillBox");
        grid.fillBox(1, 1, 1, 2, 2, 2, 9);
        assertCoincide(grid, "fillBox solapado");
        grid.fillBox(4, 4, 4, 4, 4, 4, 0);
        assertCoincide(grid, "fillBox de aire");

        grid.clear(0, 0, 0);
        assertCoincide(grid, "clear de celda ocupada");
        grid.clear(0, 0, 0);
        assertCoincide(grid, "clear de celda ya vacia");
        grid.clear(grid.sizeX() - 1, grid.sizeY() - 1, grid.sizeZ() - 1);
        assertCoincide(grid, "clear fuera del volumen rellenado");

        grid.clearBox(3, 3, 3, 4, 4, 4);
        assertCoincide(grid, "clearBox");
        grid.clearBox(0, 0, 0, 5, 4, 6);
        assertCoincide(grid, "clearBox de toda la grilla");

        grid.fillAll(2);
        assertEquals(grid.cellCount(), grid.countOccupied());
        assertFalse(grid.isEmpty());
        grid.fillAll(0);
        assertEquals(0, grid.countOccupied());
        assertTrue(grid.isEmpty());

        grid.setMaterial(0, 0, 0, 7);
        assertEquals(1, grid.countOccupied());
        assertFalse(grid.isEmpty());
        grid.clearAll();
        assertEquals(0, grid.countOccupied());
        assertTrue(grid.isEmpty());
    }

    @Test
    void elContadorSigueCoincidiendoTrasMuchasEscriturasAleatorias() {
        MicroVoxelGrid grid = new MicroVoxelGrid(12, 9, 11);
        java.util.Random rnd = new java.util.Random(20261008L);
        for (int i = 0; i < 20_000; i++) {
            int x = rnd.nextInt(grid.sizeX());
            int y = rnd.nextInt(grid.sizeY());
            int z = rnd.nextInt(grid.sizeZ());
            int material = rnd.nextInt(4); // 0 = aire
            switch (rnd.nextInt(4)) {
                case 0 -> grid.setMaterial(x, y, z, material);
                case 1 -> grid.clear(x, y, z);
                case 2 -> grid.fillBox(x, y, z, Math.min(x + 2, grid.sizeX() - 1),
                        Math.min(y + 2, grid.sizeY() - 1),
                        Math.min(z + 2, grid.sizeZ() - 1), material);
                default -> grid.clearBox(x, y, z, Math.min(x + 1, grid.sizeX() - 1),
                        Math.min(y + 1, grid.sizeY() - 1),
                        Math.min(z + 1, grid.sizeZ() - 1));
            }
        }
        assertCoincide(grid, "secuencia aleatoria de 20.000 escrituras");
    }

    /**
     * Compara el contador incremental con un recuento directo sobre el array
     * crudo: es la comprobacion que detecta cualquier desvio del contador.
     */
    private static void assertCoincide(MicroVoxelGrid grid, String paso) {
        int directo = recuentoDirecto(grid);
        assertEquals(directo, grid.countOccupied(), "Se desvio el contador tras: " + paso);
        assertEquals(directo == 0, grid.isEmpty(), "Se desvio isEmpty tras: " + paso);
    }

    private static int recuentoDirecto(MicroVoxelGrid grid) {
        int n = 0;
        for (byte b : grid.rawCells()) {
            if (b != 0) {
                n++;
            }
        }
        return n;
    }
}
