package com.microvoxel.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.material.VoxelMaterial;
import com.microvoxel.phys.AABB;
import com.microvoxel.scene.ShapeBuilder;

/** Verifica la consulta de cubos solidos que usa la colision. */
class WorldCollisionTest {

    private static final int RES = 16;

    private static World world() {
        return new World(MaterialRegistry.createDemo(), RES);
    }

    private static AABB region(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    @Test
    void unBloqueMacizoAportaUnUnicoCuboDeUnaUnidad() {
        World world = world();
        world.addBlock(0, 0, 0).grid().fillAll(MaterialRegistry.STONE);

        List<AABB> cubes = world.solidCubes(region(-1, -1, -1, 2, 2, 2));

        assertEquals(1, cubes.size(), "Un bloque macizo no debe generar 4.096 cajas");
        AABB cube = cubes.get(0);
        assertEquals(0.0, cube.minX, 1e-9);
        assertEquals(0.0, cube.minY, 1e-9);
        assertEquals(0.0, cube.minZ, 1e-9);
        assertEquals(1.0, cube.maxX, 1e-9);
        assertEquals(1.0, cube.maxY, 1e-9);
        assertEquals(1.0, cube.maxZ, 1e-9);
    }

    @Test
    void elAireYElAguaNoAportanCubos() {
        World world = world();
        world.addBlock(0, 0, 0).grid().fillAll(MaterialRegistry.WATER);
        world.addBlock(1, 0, 0).grid().fillAll(VoxelMaterial.AIR_ID);

        assertTrue(world.solidCubes(region(-1, -1, -1, 3, 2, 2)).isEmpty(),
                "El agua no es solida y el resto esta vacio");
    }

    @Test
    void unBloqueConFormaAportaUnaCajaPorCeldaOcupadaDeLaRegion() {
        World world = world();
        LogicalBlock block = world.addBlock(0, 0, 0);
        ShapeBuilder.solid(block.grid(), MaterialRegistry.STONE);
        // Se vacia la capa superior completa: el bloque deja de ser uniforme.
        block.grid().clearBox(0, RES - 1, 0, RES - 1, RES - 1, RES - 1);

        // Region que solo toca las dos primeras capas, en un unico microvoxel.
        List<AABB> cubes = world.solidCubes(region(0.0, 0.0, 0.0, 1.0 / RES, 2.0 / RES, 1.0 / RES));

        assertEquals(2, cubes.size(), "Una caja por celda ocupada dentro de la region");
        for (AABB cube : cubes) {
            assertEquals(1.0 / RES, cube.maxX - cube.minX, 1e-9, "Cada caja mide un microvoxel");
            assertEquals(1.0 / RES, cube.maxY - cube.minY, 1e-9);
            assertEquals(1.0 / RES, cube.maxZ - cube.minZ, 1e-9);
        }
        assertEquals(0.0, cubes.get(0).minY, 1e-9);
        assertEquals(1.0 / RES, cubes.get(1).minY, 1e-9);
    }

    @Test
    void soloSeDevuelvenLosCubosQueSolapanLaRegion() {
        World world = world();
        LogicalBlock block = world.addBlock(0, 0, 0);
        ShapeBuilder.solid(block.grid(), MaterialRegistry.STONE);
        block.grid().clearBox(0, RES - 1, 0, RES - 1, RES - 1, RES - 1);

        // La region queda por encima de la capa vaciada: no hay nada que devolver.
        List<AABB> cubes = world.solidCubes(
                region(0.0, 1.0 - 1.0 / RES, 0.0, 1.0, 2.0, 1.0));

        assertTrue(cubes.isEmpty(), "El hueco de la capa superior no debe aportar colision");
    }

    @Test
    void unaRegionLejosDelMundoNoDevuelveNada() {
        World world = world();
        world.addBlock(0, 0, 0).grid().fillAll(MaterialRegistry.STONE);

        assertTrue(world.solidCubes(region(40, 40, 40, 42, 42, 42)).isEmpty());
    }

    @Test
    void losLimitesExactosDeUnaRegionNoIncluyenElBloqueVecino() {
        World world = world();
        world.addBlock(0, 0, 0).grid().fillAll(MaterialRegistry.STONE);
        world.addBlock(1, 0, 0).grid().fillAll(MaterialRegistry.STONE);

        // Region contenida en el primer bloque: no debe arrastrar el segundo.
        List<AABB> cubes = world.solidCubes(region(0.1, 0.1, 0.1, 0.9, 0.9, 0.9));

        assertEquals(1, cubes.size());
        assertEquals(0.0, cubes.get(0).minX, 1e-9);
    }
}
