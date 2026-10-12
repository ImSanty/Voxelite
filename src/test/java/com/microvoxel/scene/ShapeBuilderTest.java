package com.microvoxel.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.world.MicroVoxelGrid;

/**
 * Verifica las utilidades de construccion de formas volumetricas.
 *
 * <p>Las comprobaciones se hacen siempre sobre la ocupacion real de la grilla,
 * nunca sobre geometria renderizable: la forma de un bloque es un dato, no una
 * malla.</p>
 */
class ShapeBuilderTest {

    private static final int STONE = MaterialRegistry.STONE;

    @Test
    void laCajaRellenaExactamenteElVolumenIndicado() {
        MicroVoxelGrid grid = new MicroVoxelGrid(8);
        int written = ShapeBuilder.box(grid, 1, 1, 1, 3, 3, 3, STONE);

        assertEquals(27, written);
        assertEquals(27, grid.countOccupied());
        assertTrue(grid.isOccupied(1, 1, 1));
        assertTrue(grid.isOccupied(3, 3, 3));
        assertFalse(grid.isOccupied(0, 0, 0));
        assertFalse(grid.isOccupied(4, 1, 1));
    }

    @Test
    void laCajaNormalizaCoordenadasEnCualquierOrden() {
        MicroVoxelGrid a = new MicroVoxelGrid(8);
        MicroVoxelGrid b = new MicroVoxelGrid(8);
        ShapeBuilder.box(a, 1, 2, 3, 4, 5, 6, STONE);
        ShapeBuilder.box(b, 4, 5, 6, 1, 2, 3, STONE);

        assertEquals(a.countOccupied(), b.countOccupied());
        for (int z = 0; z < 8; z++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(a.getMaterial(x, y, z), b.getMaterial(x, y, z));
                }
            }
        }
    }

    @Test
    void solidoLlenaTodaLaGrilla() {
        MicroVoxelGrid grid = new MicroVoxelGrid(4, 5, 6);
        ShapeBuilder.solid(grid, STONE);

        assertFalse(grid.isEmpty());
        assertEquals(4 * 5 * 6, grid.countOccupied());
        assertEquals(STONE, grid.getMaterial(3, 4, 5));
    }

    @Test
    void elTunelDejaParedesAlrededorDeLaZonaVaciada() {
        MicroVoxelGrid grid = new MicroVoxelGrid(5);
        ShapeBuilder.solid(grid, STONE);
        ShapeBuilder.tunnelAlongX(grid, 2);

        // La seccion central del tunel queda vacia en todo el recorrido en X.
        for (int x = 0; x < 5; x++) {
            assertFalse(grid.isOccupied(x, 2, 2), "El centro del tunel debe estar vacio");
            assertFalse(grid.isOccupied(x, 1, 1));
        }
        // Alrededor sigue habiendo material: el tunel es un hueco, no un bloque hueco.
        assertTrue(grid.isOccupied(0, 0, 2));
        assertTrue(grid.isOccupied(0, 3, 2));
        assertTrue(grid.isOccupied(4, 0, 0));
    }

    @Test
    void laCavidadQuedaEncerradaDentroDelMaterial() {
        MicroVoxelGrid grid = new MicroVoxelGrid(5);
        ShapeBuilder.cavity(grid, 1, STONE);

        // Interior vacio.
        assertFalse(grid.isOccupied(2, 2, 2));
        assertFalse(grid.isOccupied(1, 1, 1));
        assertFalse(grid.isOccupied(3, 3, 3));
        // La cascara exterior permanece solida.
        assertTrue(grid.isOccupied(0, 2, 2));
        assertTrue(grid.isOccupied(4, 2, 2));
        assertTrue(grid.isOccupied(2, 0, 2));
        assertTrue(grid.isOccupied(2, 4, 2));
        // Ocupados = 5^3 - 3^3 = 98.
        assertEquals(98, grid.countOccupied());
    }

    @Test
    void laCavidadAbiertaTieneUnAccesoPorArriba() {
        MicroVoxelGrid grid = new MicroVoxelGrid(5);
        ShapeBuilder.openCavity(grid, 1, STONE);

        assertFalse(grid.isOccupied(2, 2, 2), "El interior sigue vacio");
        assertFalse(grid.isOccupied(2, 4, 2), "Hay un hueco de acceso en la cara superior");
    }

    @Test
    void laEscaleraGeneraNivelesCrecientes() {
        MicroVoxelGrid grid = new MicroVoxelGrid(16);
        ShapeBuilder.stairs(grid, 4, 2, STONE);

        assertFalse(grid.isEmpty());
        // El primer escalon esta en la base y el ultimo es el mas alto.
        assertTrue(grid.isOccupied(0, 0, 0));
        assertTrue(grid.isOccupied(0, 3, 6));
        // No deberia haber material por encima del escalon mas alto.
        assertFalse(grid.isOccupied(0, 5, 6));
    }

    @Test
    void losComponentesSeparadosEstanRealmenteDesconectados() {
        MicroVoxelGrid grid = new MicroVoxelGrid(16);
        ShapeBuilder.disconnectedComponents(grid, STONE, MaterialRegistry.DIRT, MaterialRegistry.GOLD);

        assertFalse(grid.isEmpty());
        assertEquals(5, cuentaComponentes(grid),
                "Cada region ocupada debe ser un componente independiente");
    }

    @Test
    void losMaterialesMixtosUsanVariosMaterialesEnLasTresDimensiones() {
        MicroVoxelGrid grid = new MicroVoxelGrid(16);
        ShapeBuilder.mixedMaterials(grid, MaterialRegistry.RED,
                MaterialRegistry.GREEN, MaterialRegistry.YELLOW);

        java.util.Set<Integer> usados = new java.util.HashSet<>();
        for (int z = 0; z < 16; z++) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int m = grid.getMaterial(x, y, z);
                    if (m != 0) {
                        usados.add(m);
                    }
                }
            }
        }
        assertEquals(3, usados.size());
        assertTrue(usados.contains(MaterialRegistry.RED));
        assertTrue(usados.contains(MaterialRegistry.GREEN));
        assertTrue(usados.contains(MaterialRegistry.YELLOW));
    }

    @Test
    void elArcoEsSimetricoRespectoAlPlanoCentral() {
        MicroVoxelGrid grid = new MicroVoxelGrid(16);
        ShapeBuilder.arch(grid, STONE);

        assertFalse(grid.isEmpty());
        // El hueco del arco esta en la base y a ambos lados del centro.
        assertFalse(grid.isOccupied(7, 0, 8));
        assertFalse(grid.isOccupied(8, 0, 8));
        // La clave del arco sigue ocupada.
        assertTrue(grid.isOccupied(7, 15, 8) || grid.isOccupied(8, 15, 8));
    }

    /** Cuenta componentes conexos de ocupacion con conectividad de 6 vecinos. */
    private static int cuentaComponentes(MicroVoxelGrid grid) {
        int sx = grid.sizeX();
        int sy = grid.sizeY();
        int sz = grid.sizeZ();
        boolean[] visitado = new boolean[sx * sy * sz];
        int componentes = 0;

        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    if (!grid.isOccupied(x, y, z) || visitado[index(x, y, z, sx, sy)]) {
                        continue;
                    }
                    componentes++;
                    Deque<int[]> pila = new ArrayDeque<>();
                    pila.push(new int[]{x, y, z});
                    visitado[index(x, y, z, sx, sy)] = true;
                    while (!pila.isEmpty()) {
                        int[] c = pila.pop();
                        for (int d = 0; d < 6; d++) {
                            int nx = c[0] + DX[d];
                            int ny = c[1] + DY[d];
                            int nz = c[2] + DZ[d];
                            if (!grid.inBounds(nx, ny, nz)) {
                                continue;
                            }
                            int i = index(nx, ny, nz, sx, sy);
                            if (!visitado[i] && grid.isOccupied(nx, ny, nz)) {
                                visitado[i] = true;
                                pila.push(new int[]{nx, ny, nz});
                            }
                        }
                    }
                }
            }
        }
        return componentes;
    }

    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private static int index(int x, int y, int z, int sx, int sy) {
        return x + sx * (y + sy * z);
    }
}
