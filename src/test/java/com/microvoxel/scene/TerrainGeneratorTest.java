package com.microvoxel.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.microvoxel.input.Input;
import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.phys.Player;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Verifica el terreno generado <b>por columnas</b>.
 *
 * <p>Lo que distingue a este terreno del anterior es que <b>no existe como
 * bloques</b>: hay que comprobar que el mundo responde por el (dibujo, colision y
 * cotas) sin que se materialice ni un solo {@code LogicalBlock}.</p>
 */
class TerrainGeneratorTest {

    private static World world16() {
        return new World(MaterialRegistry.createDemo(), 16);
    }

    private static World terrainWorld() {
        World world = world16();
        TerrainGenerator.build(world);
        return world;
    }

    @Test
    void elTerrenoEsDeterminista() {
        int[][] samples = new int[5][3];
        int i = 0;
        for (int x : new int[] { 0, 17, 63, 100, 127 }) {
            samples[i][0] = x;
            samples[i][1] = TerrainGenerator.surfaceTop(x, 40);
            samples[i][2] = TerrainGenerator.surfaceTop(40, x);
            i++;
        }
        // Recalcular el relieve (ya cacheado) debe dar exactamente lo mismo.
        for (int[] sample : samples) {
            assertEquals(sample[1], TerrainGenerator.surfaceTop(sample[0], 40));
            assertEquals(sample[2], TerrainGenerator.surfaceTop(40, sample[0]));
        }
        long generated = TerrainGenerator.generatedBlockCount();
        assertEquals(generated, TerrainGenerator.generatedBlockCount());
        assertTrue(generated > 2_000_000L,
                "El terreno de -128 a 64 debe describir millones de bloques: " + generated);
    }

    @Test
    void ocupaUnaCuadriculaDe8x8ChunksSinMaterializarNingunBloque() {
        World world = terrainWorld();

        assertEquals(TerrainGenerator.CHUNKS_X * TerrainGenerator.CHUNKS_Z, world.chunks().size());
        for (Chunk chunk : world.chunks()) {
            assertTrue(chunk.chunkX() >= 0 && chunk.chunkX() < TerrainGenerator.CHUNKS_X);
            assertTrue(chunk.chunkZ() >= 0 && chunk.chunkZ() < TerrainGenerator.CHUNKS_Z);
            assertEquals(0, chunk.blockCount(), "Un chunk de terreno no contiene bloques");
        }

        // Esta es la razon de ser del generador por columnas: millones de bloques
        // descritos con cero objetos.
        assertEquals(0, world.blockCount(), "El terreno no debe materializar bloques");
        assertEquals(0L, world.totalStoredMicroVoxels(), "Ni reservar celdas de microvoxel");
        assertNotNull(world.blockSource(), "El mundo debe tener la fuente de bloques del terreno");
    }

    @Test
    void elRelieveViveEntreLasCotasDeclaradasYHayRocaHastaElFondo() {
        World world = terrainWorld();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int z = 0; z < TerrainGenerator.BLOCKS_Z; z++) {
            for (int x = 0; x < TerrainGenerator.BLOCKS_X; x++) {
                int top = TerrainGenerator.surfaceTop(x, z);
                min = Math.min(min, top);
                max = Math.max(max, top);

                assertTrue(top >= TerrainGenerator.MIN_SURFACE_BLOCK_Y
                        && top <= TerrainGenerator.MAX_SURFACE_BLOCK_Y,
                        "Superficie fuera de rango en " + x + "," + z + ": " + top);
                // Roca hasta el fondo, aire por encima del relieve (o agua).
                assertTrue(world.isSolidBlock(x, TerrainGenerator.MIN_BLOCK_Y, z),
                        "El fondo del mundo debe ser solido en " + x + "," + z);
                assertEquals(0, world.materialCode(x, TerrainGenerator.MAX_SURFACE_BLOCK_Y + 1, z),
                        "Por encima del relieve debe haber aire");
            }
        }

        // Relieve real, no una meseta.
        assertTrue(max - min >= 20, "El relieve debe variar: " + min + ".." + max);
    }

    @Test
    void losEstratosSonCespedTierraPiedraYArenaBajoElAgua() {
        World world = terrainWorld();

        int dryColumnX = -1;
        int dryColumnZ = -1;
        int floodedColumnX = -1;
        int floodedColumnZ = -1;
        for (int z = 0; z < TerrainGenerator.BLOCKS_Z && floodedColumnX < 0; z++) {
            for (int x = 0; x < TerrainGenerator.BLOCKS_X; x++) {
                int top = TerrainGenerator.surfaceTop(x, z);
                if (top > TerrainGenerator.WATER_LEVEL_BLOCK_Y + 1 && dryColumnX < 0) {
                    dryColumnX = x;
                    dryColumnZ = z;
                }
                if (top < TerrainGenerator.WATER_LEVEL_BLOCK_Y - 1 && floodedColumnX < 0) {
                    floodedColumnX = x;
                    floodedColumnZ = z;
                    break;
                }
            }
        }
        assertTrue(dryColumnX >= 0, "Debe haber columnas secas");
        assertTrue(floodedColumnX >= 0, "Debe haber columnas inundadas");

        int top = TerrainGenerator.surfaceTop(dryColumnX, dryColumnZ);
        assertEquals(MaterialRegistry.GRASS, world.materialCode(dryColumnX, top, dryColumnZ));
        assertEquals(MaterialRegistry.DIRT, world.materialCode(dryColumnX, top - 1, dryColumnZ));
        assertEquals(MaterialRegistry.DIRT, world.materialCode(dryColumnX, top - 3, dryColumnZ));
        assertEquals(MaterialRegistry.STONE, world.materialCode(dryColumnX, top - 8, dryColumnZ));
        assertEquals(MaterialRegistry.STONE, world.materialCode(dryColumnX, TerrainGenerator.MIN_BLOCK_Y, dryColumnZ));

        int floodedTop = TerrainGenerator.surfaceTop(floodedColumnX, floodedColumnZ);
        assertEquals(MaterialRegistry.SAND, world.materialCode(floodedColumnX, floodedTop, floodedColumnZ),
                "Las orillas sumergidas son de arena");
        assertEquals(MaterialRegistry.WATER, world.materialCode(floodedColumnX, floodedTop + 1, floodedColumnZ));
        assertEquals(MaterialRegistry.WATER,
                world.materialCode(floodedColumnX, TerrainGenerator.WATER_LEVEL_BLOCK_Y, floodedColumnZ));
        assertEquals(0, world.materialCode(floodedColumnX, TerrainGenerator.WATER_LEVEL_BLOCK_Y + 1, floodedColumnZ),
                "El agua no puede pasar del nivel del lago");
    }

    @Test
    void lasConsultasGlobalesDeMicrovoxelTambienVenElTerrenoGenerado() {
        World world = terrainWorld();
        int x = 30;
        int z = 30;
        int top = TerrainGenerator.surfaceTop(x, z);

        // Celda central del bloque de superficie: material global igual al del bloque.
        int gx = x * world.resolutionX() + world.resolutionX() / 2;
        int gy = top * world.resolutionY() + world.resolutionY() / 2;
        int gz = z * world.resolutionZ() + world.resolutionZ() / 2;
        assertEquals(world.materialCode(x, top, z), world.getMaterialGlobal(gx, gy, gz));
        assertTrue(world.isOccupiedGlobal(gx, gy, gz));

        // Fuera de la huella del terreno no hay nada.
        assertEquals(0, world.materialCode(-1, 0, 0));
        assertEquals(0, world.materialCode(TerrainGenerator.BLOCKS_X, 0, 0));
        assertEquals(0, world.materialCode(0, TerrainGenerator.MIN_BLOCK_Y - 1, 0));
    }

    @Test
    void elMundoAceptaBloquesHasta512YRechazaFueraDeCotas() {
        World world = terrainWorld();

        LogicalBlock high = world.addBlock(10, World.MAX_BUILD_BLOCK_Y, 10);
        assertNotNull(high);
        assertEquals(1, world.blockCount());

        assertThrows(IllegalArgumentException.class,
                () -> world.addBlock(10, World.MAX_BUILD_BLOCK_Y + 1, 10),
                "Por encima de la cota de construccion no se admite nada");
        assertThrows(IllegalArgumentException.class,
                () -> world.addBlock(10, World.MIN_BLOCK_Y - 1, 10),
                "Por debajo del fondo del mundo tampoco");

        assertEquals(0, world.materialCode(10, World.MAX_BUILD_BLOCK_Y + 1, 10));
        assertEquals(0, world.materialCode(10, World.MIN_BLOCK_Y - 1, 10));
    }

    @Test
    void unBloqueMaterializadoTienePrioridadSobreLaFuente() {
        World world = terrainWorld();
        int x = 40;
        int z = 40;
        int top = TerrainGenerator.surfaceTop(x, z);

        // Una edicion: se vacia el bloque de superficie del terreno generado.
        LogicalBlock edited = world.addBlock(x, top, z);
        edited.grid().fillAll(0);
        assertEquals(0, world.materialCode(x, top, z), "El bloque registrado manda sobre la fuente");
        assertFalse(world.isSolidBlock(x, top, z));
        // El resto de la columna sigue siendo terreno generado.
        assertTrue(world.isSolidBlock(x, top - 1, z));

        // Y con forma propia (no uniforme) tampoco materializa el vecino.
        edited.grid().setMaterial(0, 0, 0, MaterialRegistry.RED);
        assertEquals(-1, world.materialCode(x, top, z));
    }

    @Test
    void laColisionFrenaalJugadorSobreRocaQueNoExisteComoBloque() {
        DemoScene scene = new DemoScene();
        World world = scene.world();
        assertEquals(9, world.blockCount(), "Solo los bloques de demostracion estan materializados");

        Player player = new Player(world);
        Vector3f spawn = scene.playerSpawn();
        player.setPosition(spawn.x, spawn.y + 5.0, spawn.z);
        assertFalse(player.isInsideSolid(), "El jugador no puede aparecer dentro de la roca");

        Input input = new Input();
        for (int i = 0; i < 60; i++) {
            player.update(Player.STEP_SECONDS, input, 0.0f);
        }
        assertTrue(player.onGround(), "El jugador debe quedar apoyado en la superficie");

        int[] column = DemoScene.findSpawnColumn();
        int top = TerrainGenerator.surfaceTop(column[0], column[1]);
        assertEquals(top + 1.0 + Player.EYE_HEIGHT, player.eyeY(), 1e-6,
                "Los pies deben quedar sobre la cara superior del bloque de superficie");
    }

    @Test
    void elSpawnEsSecoYAireLibreYLosBloquesDemoQuedanPorEncimaDelRelieve() {
        DemoScene scene = new DemoScene();
        World world = scene.world();

        int[] column = DemoScene.findSpawnColumn();
        assertTrue(TerrainGenerator.surfaceTop(column[0], column[1])
                > TerrainGenerator.WATER_LEVEL_BLOCK_Y, "El spawn no puede estar inundado");

        Player player = new Player(world);
        Vector3f spawn = scene.playerSpawn();
        player.setPosition(spawn.x, spawn.y, spawn.z);
        assertFalse(player.isInsideSolid(), "El spawn debe estar en aire libre");

        // Los bloques de demostracion se apoyan por encima del punto mas alto de su
        // propia huella: nunca dentro de la montana.
        int highest = TerrainGenerator.maxSurfaceTop(0, 5, 0, 5);
        assertTrue(DemoScene.DEMO_BLOCK_Y > highest, "Los bloques demo deben quedar sobre el terreno");
        assertNotNull(world.getBlock(0, DemoScene.DEMO_BLOCK_Y, 0));
        assertNotNull(world.getBlock(4, DemoScene.DEMO_BLOCK_Y, 4));
    }
}
