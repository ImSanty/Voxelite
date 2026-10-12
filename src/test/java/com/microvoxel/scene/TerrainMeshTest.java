package com.microvoxel.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.mesh.LineData;
import com.microvoxel.mesh.MeshData;
import com.microvoxel.mesh.MicroVoxelMeshBuilder;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Verifica el camino de meshing que usa {@link com.microvoxel.render.Renderer}:
 * una malla combinada por chunk para el terreno y una malla por bloque suelto
 * para la escena de demostracion.
 *
 * <p>Como cada bloque del terreno es macizo y uniforme, el generador debe
 * fusionar cada cara expuesta en un unico quad de 1x1, de modo que un terreno de
 * 64x64 bloques (4.096 voxeles por bloque) siga produciendo una cantidad de
 * geometria contenida en lugar de millones de micro-caras.</p>
 */
class TerrainMeshTest {

    @Test
    void elTerrenoSeMallaPorChunksConGeometriaComprimida() {
        DemoScene scene = new DemoScene();
        World world = scene.world();

        assertEquals(TerrainBuilder.CHUNKS_X * TerrainBuilder.CHUNKS_Z, world.chunks().size());

        long start = System.nanoTime();
        long faces = 0;
        for (Chunk chunk : world.chunks()) {
            MeshData mesh = MicroVoxelMeshBuilder.buildChunkMesh(world, chunk);
            faces += mesh.vertexCount() / 4;
        }
        for (LogicalBlock block : world.blocks()) {
            if (!world.isInChunk(block)) {
                MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);
                faces += mesh.vertexCount() / 4;
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;

        long storedVoxels = world.totalStoredMicroVoxels();
        long occupiedVoxels = world.totalOccupiedMicroVoxels();
        System.out.println("[TerrainMeshTest] chunks=" + world.chunks().size()
                + " bloques=" + world.blockCount()
                + " voxelesAlmacenados=" + storedVoxels
                + " voxelesOcupados=" + occupiedVoxels
                + " caras=" + faces
                + " ms=" + millis);

        assertTrue(faces > 0, "El terreno debe producir geometria");
        // Con fusion de caras, el terreno completo ronda las decenas de miles de
        // caras. Sin fusion serian millones (256 micro-caras por cada cara de
        // bloque). El umbral detecta una regresion del meshing fusionado.
        assertTrue(faces < 500_000, "El meshing fusionado debe mantener la geometria compacta: " + faces);
        // Cada bloque aporta exactamente 4.096 voxeles almacenados.
        assertEquals(world.blockCount() * 4096L, storedVoxels);
    }

    @Test
    void laRejillaDeDepuracionDelTerrenoSeGeneraDeFormaCompacta() {
        World world = new World(MaterialRegistry.createDemo(), 16);
        TerrainBuilder.build(world);

        LineData.Builder builder = new LineData.Builder();
        for (LogicalBlock block : world.blocks()) {
            MicroVoxelMeshBuilder.appendCellBorders(world, block, builder);
        }
        LineData data = builder.build();
        System.out.println("[TerrainMeshTest] lineas_rejilla_F2=" + data.lineCount());

        // La superficie del terreno (4.096 columnas) debe aportar su rejilla: si
        // el camino rapido de bloques uniformes fallase, el terreno no dibujaria
        // ninguna linea. Como minimo hay una cara superior por columna.
        assertTrue(data.lineCount() > 100_000, "La rejilla F2 del terreno es sospechosamente pequena: " + data.lineCount());
        // Sin el camino rapido serian millones de segmentos (4 por micro-cara).
        assertTrue(data.lineCount() < 2_000_000,
                "La rejilla de depuracion es demasiado grande: " + data.lineCount());
    }
}
