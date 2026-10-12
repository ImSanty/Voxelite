package com.microvoxel.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.mesh.LineData;
import com.microvoxel.mesh.MeshData;
import com.microvoxel.mesh.MicroVoxelMeshBuilder;
import com.microvoxel.render.DebugRenderer;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Verifica el meshing del terreno generado por bandas de chunk.
 *
 * <p>El terreno no tiene bloques, asi que la malla se genera recorriendo la banda
 * vertical de cada chunk y preguntando al mundo que hay en cada celda. Lo que hay
 * que demostrar es que la geometria sale <b>compacta</b> (una cara por bloque
 * expuesto, no 256 micro-caras) y <b>sin caras internas</b> (nada dentro de la
 * roca).</p>
 */
class TerrainMeshTest {

    @Test
    void laMallaPorBandaEsCompactaYNoTieneCarasInternas() {
        World world = new World(MaterialRegistry.createDemo(), 16);
        TerrainGenerator.build(world);

        long start = System.nanoTime();
        long faces = 0;
        for (Chunk chunk : world.chunks()) {
            MeshData mesh = MicroVoxelMeshBuilder.buildChunkMesh(world, chunk);
            faces += mesh.vertexCount() / 4;
            assertNoInternalFaces(world, mesh);
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        System.out.println("[TerrainMeshTest] chunks=" + world.chunks().size()
                + " caras=" + faces + " ms=" + millis);

        assertTrue(faces > 10_000, "El terreno debe producir geometria: " + faces);
        // Superficie (16.384 caras), paredes y fondo, mas el agua. Sin fusion serian
        // millones de micro-caras.
        assertTrue(faces < 500_000, "El meshing debe seguir siendo compacto: " + faces);
    }

    /**
     * Ninguna cara puede estar enterrada: el centro de cada quad desplazado media
     * celda en el sentido de su normal debe caer en una celda vacia.
     */
    private static void assertNoInternalFaces(World world, MeshData mesh) {
        float[] vertices = mesh.vertices();
        int count = mesh.vertexCount();
        for (int quad = 0; quad * 4 < count; quad++) {
            int base = quad * 4 * MeshData.FLOATS_PER_VERTEX;
            float px = vertices[base];
            float py = vertices[base + 1];
            float pz = vertices[base + 2];
            float nx = vertices[base + 3];
            float ny = vertices[base + 4];
            float nz = vertices[base + 5];
            int cx = (int) Math.floor(px + nx * 0.5f);
            int cy = (int) Math.floor(py + ny * 0.5f);
            int cz = (int) Math.floor(pz + nz * 0.5f);
            assertTrue(world.materialCode(cx, cy, cz) == 0,
                    "Cara interna en " + cx + "," + cy + "," + cz
                            + " (normal " + nx + "," + ny + "," + nz + ")");
        }
    }

    @Test
    void laBandaDeCadaChunkCubreSuperficieParedesYFondo() {
        World world = new World(MaterialRegistry.createDemo(), 16);
        TerrainGenerator.build(world);

        for (Chunk chunk : world.chunks()) {
            MicroVoxelMeshBuilder.buildChunkMesh(world, chunk);
            int minTop = TerrainGenerator.minSurfaceTop(
                    chunk.chunkX() * Chunk.BLOCKS_X, chunk.chunkX() * Chunk.BLOCKS_X + Chunk.BLOCKS_X - 1,
                    chunk.chunkZ() * Chunk.BLOCKS_Z, chunk.chunkZ() * Chunk.BLOCKS_Z + Chunk.BLOCKS_Z - 1);
            // La celda mas baja que puede producir cara es la que queda justo encima
            // de la pared del vecino mas bajo.
            assertTrue(chunk.bandMinBlockY() <= minTop + 1,
                    "La banda debe empezar por debajo del relieve mas bajo: "
                            + chunk.bandMinBlockY() + " > " + (minTop + 1));
            assertTrue(chunk.bandMaxBlockY() >= TerrainGenerator.WATER_LEVEL_BLOCK_Y,
                    "La banda debe incluir el agua");
            assertTrue(chunk.minWorldY() < chunk.maxWorldY(), "La banda debe tener altura");
        }
    }

    @Test
    void editarUnBloqueExponeSusCarasYRemalllaElChunk() {
        World world = new World(MaterialRegistry.createDemo(), 16);
        TerrainGenerator.build(world);

        int x = 20;
        int z = 20;
        int top = TerrainGenerator.surfaceTop(x, z) - 4; // dentro de la roca
        Chunk chunk = world.chunks().get((z / Chunk.BLOCKS_Z) * TerrainGenerator.CHUNKS_X
                + (x / Chunk.BLOCKS_X));
        MeshData before = MicroVoxelMeshBuilder.buildChunkMesh(world, chunk);

        // Se excava el bloque: pasa a ser aire y sus seis vecinos quedan expuestos.
        LogicalBlock dug = world.addBlock(x, top, z);
        dug.grid().fillAll(0);
        MeshData after = MicroVoxelMeshBuilder.buildChunkMesh(world, chunk);

        assertEquals(before.vertexCount() / 4 + 6, after.vertexCount() / 4,
                "Vaciar un bloque enterrado debe exponer exactamente seis caras nuevas");
        assertNoInternalFaces(world, after);
    }

    @Test
    void losBloquesSueltosNoSeMallanDosVeces() {
        DemoScene scene = new DemoScene();
        World world = scene.world();

        long looseFaces = 0;
        for (LogicalBlock block : world.looseBlocks()) {
            looseFaces += MicroVoxelMeshBuilder.buildBlockMesh(world, block).vertexCount() / 4;
        }
        long chunkFaces = 0;
        for (Chunk chunk : world.chunks()) {
            chunkFaces += MicroVoxelMeshBuilder.buildChunkMesh(world, chunk).vertexCount() / 4;
        }
        assertTrue(looseFaces > 0, "La escena de demostracion debe tener bloques sueltos");

        // Si el meshing por banda los incluyera, cada uno apareceria dos veces: se
        // comprueba que ninguna cara del chunk cae dentro de un bloque suelto.
        for (Chunk chunk : world.chunks()) {
            MeshData mesh = MicroVoxelMeshBuilder.buildChunkMesh(world, chunk);
            float[] vertices = mesh.vertices();
            for (int quad = 0; quad * 4 < mesh.vertexCount(); quad++) {
                int base = quad * 4 * MeshData.FLOATS_PER_VERTEX;
                int bx = (int) Math.floor(vertices[base]);
                int by = (int) Math.floor(vertices[base + 1]);
                int bz = (int) Math.floor(vertices[base + 2]);
                assertNull(world.getBlock(bx, by, bz),
                        "Cara de chunk dentro del bloque suelto " + bx + "," + by + "," + bz);
            }
        }
    }

    @Test
    void laRejillaDeDepuracionSeAcotaAlRadioDeLaCamara() {
        World world = new World(MaterialRegistry.createDemo(), 16);
        TerrainGenerator.build(world);

        LineData.Builder builder = new LineData.Builder();
        int radius = DebugRenderer.DEBUG_RADIUS;
        int x = 40;
        int z = 40;
        MicroVoxelMeshBuilder.appendSurfaceCellBorders(world, x - radius, z - radius,
                x + radius, z + radius, builder);
        LineData data = builder.build();
        System.out.println("[TerrainMeshTest] lineas_F2_radio16=" + data.lineCount());

        assertTrue(data.lineCount() > 1000, "La rejilla de superficie debe generar lineas");
        // 33x33 columnas con 17+17 divisiones cada una: el orden de magnitud esta
        // acotado por el radio, no por el tamaño del mundo.
        assertTrue(data.lineCount() < 100_000, "La rejilla F2 debe estar acotada: " + data.lineCount());
    }

    @Test
    void elTechoDeLaBandaIncluyeLosBloquesMaterializadosAltos() {
        World world = new World(MaterialRegistry.createDemo(), 16);
        TerrainGenerator.build(world);

        Chunk chunk = world.chunks().get(0);
        LogicalBlock high = world.addBlock(2, 300, 2);
        high.grid().fillAll(MaterialRegistry.GOLD);
        chunk.add(high);

        MeshData mesh = MicroVoxelMeshBuilder.buildChunkMesh(world, chunk);
        assertTrue(chunk.bandMaxBlockY() >= 300, "La banda debe subir hasta el bloque editado");
        Vector3f expected = high.center();
        boolean found = false;
        float[] vertices = mesh.vertices();
        for (int i = 0; i < mesh.vertexCount() * MeshData.FLOATS_PER_VERTEX; i += 3) {
            if (Math.abs(vertices[i] - expected.x) < 1.5f
                    && Math.abs(vertices[i + 1] - expected.y) < 1.5f
                    && Math.abs(vertices[i + 2] - expected.z) < 1.5f) {
                found = true;
                break;
            }
        }
        assertTrue(found, "El bloque materializado alto debe estar en la malla de su chunk");
    }
}
