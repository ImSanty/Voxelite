package com.microvoxel.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.microvoxel.input.Input;
import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.phys.Player;
import com.microvoxel.world.BlockPosition;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Verifica el terreno fijo de 8x8 chunks (64 chunks de 16x16x16 bloques).
 *
 * <p>El terreno debe ser <b>determinista</b> (mismo relieve en cada ejecucion),
 * estar organizado en chunks de 16x16x16 bloques, ocupar alturas no negativas
 * (de {@code y = 0} hacia arriba) y estar formado por bloques macizos de
 * 16x16x16 voxeles de un unico material (un "bloque de Minecraft"), combinando
 * cesped, tierra, piedra, arena y agua.</p>
 */
class TerrainBuilderTest {

    private static World world16() {
        return new World(MaterialRegistry.createDemo(), 16);
    }

    @Test
    void elTerrenoEsFijoYDeterminista() {
        World a = world16();
        TerrainBuilder.build(a);
        World b = world16();
        TerrainBuilder.build(b);

        assertEquals(a.blockCount(), b.blockCount());
        assertEquals(a.chunks().size(), b.chunks().size());
        for (LogicalBlock blockA : a.blocks()) {
            LogicalBlock blockB = b.getBlock(blockA.position());
            assertNotNull(blockB, "El segundo terreno debe reproducir el mismo relieve");
            assertArrayEquals(blockA.grid().rawCells(), blockB.grid().rawCells(),
                    "Los microvoxels del bloque " + blockA.position() + " deben coincidir");
        }
    }

    @Test
    void elTerrenoEsUnaCuadriculaDe8x8ChunksDe16x16x16Bloques() {
        World world = world16();
        TerrainBuilder.build(world);

        int expectedChunks = TerrainBuilder.CHUNKS_X * TerrainBuilder.CHUNKS_Y * TerrainBuilder.CHUNKS_Z;
        assertEquals(64, expectedChunks);
        assertEquals(expectedChunks, world.chunks().size(), "Debe haber 8x8x1 = 64 chunks");

        long maxBlocksPerChunk = (long) Chunk.BLOCKS_X * Chunk.BLOCKS_Y * Chunk.BLOCKS_Z;
        for (Chunk chunk : world.chunks()) {
            assertTrue(chunk.chunkX() >= 0 && chunk.chunkX() < TerrainBuilder.CHUNKS_X);
            assertTrue(chunk.chunkY() >= 0 && chunk.chunkY() < TerrainBuilder.CHUNKS_Y);
            assertTrue(chunk.chunkZ() >= 0 && chunk.chunkZ() < TerrainBuilder.CHUNKS_Z);
            assertTrue(chunk.blockCount() <= maxBlocksPerChunk,
                    "Un chunk no puede exceder 16x16x16 = 4096 bloques");
        }

        for (LogicalBlock block : world.blocks()) {
            BlockPosition p = block.position();
            assertTrue(p.x() >= 0 && p.x() < TerrainBuilder.BLOCKS_X,
                    "X fuera del terreno: " + p.x());
            assertTrue(p.z() >= 0 && p.z() < TerrainBuilder.BLOCKS_Z,
                    "Z fuera del terreno: " + p.z());
            assertTrue(p.y() >= TerrainBuilder.BASE_BLOCK_Y
                    && p.y() < TerrainBuilder.BASE_BLOCK_Y + TerrainBuilder.LAYERS,
                    "Y fuera del terreno: " + p.y());
        }
    }

    @Test
    void cadaBloqueDelTerrenoEsUnCuboMacizoDe16x16x16VoxelesDeUnSoloMaterial() {
        World world = world16();
        TerrainBuilder.build(world);

        for (LogicalBlock block : world.blocks()) {
            int uniform = block.uniformMaterial();
            assertTrue(uniform > 0,
                    "El bloque " + block.position() + " no es macizo y uniforme: " + uniform);
            assertEquals(block.resX() * block.resY() * block.resZ(), block.countOccupied(),
                    "El bloque " + block.position() + " no esta completamente ocupado");
            assertNotNull(world.materials().getOrNull(uniform));
        }
    }

    @Test
    void elTerrenoUsaVariosTiposDeBloque() {
        World world = world16();
        TerrainBuilder.build(world);

        Set<Integer> used = usedMaterials(world);
        assertTrue(used.contains(MaterialRegistry.GRASS), "Debe haber cesped en la superficie");
        assertTrue(used.contains(MaterialRegistry.DIRT), "Debe haber tierra bajo el cesped");
        assertTrue(used.contains(MaterialRegistry.STONE), "Debe haber piedra en el interior");
        assertTrue(used.contains(MaterialRegistry.SAND), "Debe haber arena en las orillas");
        assertTrue(used.contains(MaterialRegistry.WATER), "Debe haber agua en los valles");
    }

    @Test
    void elTerrenoEmpiezaEnCeroYQuedaBajoLosBloquesDeDemostracion() {
        World world = world16();
        TerrainBuilder.build(world);

        boolean hayNivelCero = false;
        for (LogicalBlock block : world.blocks()) {
            int baseGmy = block.position().y() * block.resY();
            byte[] cells = block.grid().rawCells();
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] == 0) {
                    continue;
                }
                int ly = (i / block.resX()) % block.resY();
                int gmy = baseGmy + ly;
                assertTrue(gmy >= 0, "El terreno no puede tener y negativo: gmy=" + gmy);
                assertTrue(gmy < DemoScene.DEMO_BLOCK_Y * block.resY(),
                        "El terreno no puede invadir la capa de los bloques demo: gmy=" + gmy);
            }
            if (block.position().y() == TerrainBuilder.BASE_BLOCK_Y) {
                hayNivelCero = true;
            }
        }
        assertTrue(hayNivelCero, "El terreno debe empezar en y = 0");
    }

    @Test
    void elRelieveTieneAlturasVariables() {
        World world = world16();
        TerrainBuilder.build(world);

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (LogicalBlock block : world.blocks()) {
            int baseGmy = block.position().y() * block.resY();
            byte[] cells = block.grid().rawCells();
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] == 0) {
                    continue;
                }
                int ly = (i / block.resX()) % block.resY();
                int gmy = baseGmy + ly;
                min = Math.min(min, gmy);
                max = Math.max(max, gmy);
            }
        }
        // Al menos varios bloques de diferencia de altura (16 microvoxels cada uno).
        assertTrue(max - min >= 3 * 16, "El terreno debe tener relieve, no ser plano: " + (max - min));
    }

    @Test
    void laEscenaDeDemostracionColocaLosBloquesDemoSobreElTerreno() {
        DemoScene scene = new DemoScene();
        World world = scene.world();

        // Los bloques de demostracion se generan en la capa superior al terreno.
        assertNotNull(world.getBlock(0, DemoScene.DEMO_BLOCK_Y, 0));
        assertNotNull(world.getBlock(4, DemoScene.DEMO_BLOCK_Y, 4));
        // El terreno ocupa 8x8 = 64 chunks, todos por debajo de los bloques demo.
        assertEquals(64, world.chunks().size());
        boolean hasTerrain = false;
        for (LogicalBlock block : world.blocks()) {
            assertTrue(block.position().y() == DemoScene.DEMO_BLOCK_Y || block.position().y() < TerrainBuilder.LAYERS,
                    "Bloque inesperado fuera del terreno y de la capa demo: " + block.position());
            if (block.position().y() < TerrainBuilder.LAYERS) {
                hasTerrain = true;
            }
        }
        assertTrue(hasTerrain, "La escena debe contener terreno bajo los bloques de demostracion");
    }

    @Test
    void elJugadorApareceDePieSobreElTerrenoYLaColisionLoSostiene() {
        DemoScene scene = new DemoScene();
        World world = scene.world();

        Player player = new Player(world);
        Vector3f spawn = scene.playerSpawn();
        player.setPosition(spawn.x, spawn.y, spawn.z);

        // El spawn no puede quedar dentro de ningun bloque solido.
        assertFalse(player.isInsideSolid(), "El jugador no puede aparecer dentro de un bloque");

        // Y tras unos cuantos pasos queda asentado justo sobre la cara superior del
        // bloque solido mas alto de la columna, recalculado aqui desde el mundo.
        Input input = new Input();
        for (int i = 0; i < 40; i++) {
            player.update(Player.STEP_SECONDS, input, 0.0f);
        }
        assertTrue(player.onGround(), "El jugador debe quedar apoyado en la superficie");
        float surface = 0.0f;
        for (int by = DemoScene.DEMO_BLOCK_Y - 1; by >= 0; by--) {
            LogicalBlock block = world.getBlock(DemoScene.SPAWN_BLOCK_X, by, DemoScene.SPAWN_BLOCK_Z);
            if (block == null) {
                continue;
            }
            int material = block.uniformMaterial();
            if (material > 0 && world.isSolidMaterial(material)) {
                surface = by + 1.0f;
                break;
            }
        }
        assertTrue(surface > 0.0f, "Debe haber terreno solido bajo el spawn");
        assertEquals(surface + Player.EYE_HEIGHT, player.eyeY(), 1e-6,
                "El jugador debe asentarse justo sobre la superficie, sin hundirse");
    }

    private static Set<Integer> usedMaterials(World world) {
        Set<Integer> used = new HashSet<>();
        for (LogicalBlock block : world.blocks()) {
            for (byte c : block.grid().rawCells()) {
                int id = c & 0xFF;
                if (id != 0) {
                    used.add(id);
                }
            }
        }
        return used;
    }
}
