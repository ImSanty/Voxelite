package com.microvoxel.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Verifica el algoritmo de culling de caras internas (seccion 13.2 del enunciado).
 *
 * <p>Convencion de conteo usada en estas pruebas: los vertices se <b>duplican por
 * cara</b> (4 por cara) para poder tener normales planas. Por tanto:</p>
 * <ul>
 *   <li>{@code caras = vertexCount / 4}</li>
 *   <li>{@code indices = caras * 6} (dos triangulos por cara)</li>
 *   <li>{@code triangulos = caras * 2}</li>
 * </ul>
 */
class MicroVoxelMeshBuilderTest {

    private static World world16() {
        return new World(MaterialRegistry.createDemo(), 16);
    }

    private static int faces(MeshData mesh) {
        return mesh.vertexCount() / 4;
    }

    @Test
    void unBloqueVacioNoGeneraCaras() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        assertTrue(mesh.isEmpty(), "Un bloque vacio no debe producir geometria");
        assertEquals(0, mesh.indexCount());
        assertEquals(0, mesh.vertexCount());
    }

    @Test
    void unUnicoMicroVoxelGeneraSeisCaras() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.setMaterial(0, 0, 0, MaterialRegistry.STONE);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        assertEquals(6, faces(mesh), "Un cubo aislado tiene exactamente seis caras");
        assertEquals(24, mesh.vertexCount(), "6 caras x 4 vertices duplicados por cara");
        assertEquals(36, mesh.indexCount(), "6 caras x 6 indices de triangulos");
        assertEquals(12, mesh.triangleCount());
    }

    @Test
    void dosMicroVoxelsAdyacentesDelMismoMaterialNoGeneranCaraInterna() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.setMaterial(0, 0, 0, MaterialRegistry.STONE);
        block.setMaterial(1, 0, 0, MaterialRegistry.STONE);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        // 12 caras menos las 2 que se comparten -> 10 caras visibles.
        assertEquals(10, faces(mesh));
    }

    @Test
    void dosMicroVoxelsAdyacentesDeMaterialesDistintosTampocoGeneranCaraInterna() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.setMaterial(0, 0, 0, MaterialRegistry.STONE);
        block.setMaterial(1, 0, 0, MaterialRegistry.DIRT);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        assertEquals(10, faces(mesh),
                "Un cambio de material no crea una cara interna entre celdas ocupadas");
    }

    @Test
    void unBloqueCompletamenteSolidoSoloGeneraLaSuperficieExterior() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.grid().fillAll(MaterialRegistry.STONE);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        // 6 caras x 16 x 16 microvoxels = 1536 caras, ninguna interna.
        assertEquals(1536, faces(mesh));
        assertEquals(6144, mesh.vertexCount());
        assertEquals(9216, mesh.indexCount());
        assertEquals(3072, mesh.triangleCount());
    }

    @Test
    void unaCavidadGeneraLaSuperficieInteriorQueLaDelimita() {
        World world = new World(MaterialRegistry.createDemo(), 5);
        LogicalBlock block = world.addBlock(0, 0, 0);
        com.microvoxel.scene.ShapeBuilder.cavity(block.grid(), 1, MaterialRegistry.STONE);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        // Exterior: 6 x 5 x 5 = 150 caras.
        // Paredes de la cavidad interior: 6 x 3 x 3 = 54 caras.
        assertEquals(150 + 54, faces(mesh));
    }

    @Test
    void unTunelAbiertoAnadeLasParedesInterioresYLasAberturas() {
        World world = new World(MaterialRegistry.createDemo(), 5);
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.grid().fillAll(MaterialRegistry.STONE);
        com.microvoxel.scene.ShapeBuilder.tunnelAlongX(block.grid(), 2);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        // Exterior: 6x5x5 = 150 caras menos las dos aberturas de 2x2 (4 cada una)
        //           -> 142.
        // Paredes del tunel: 4 paredes x (5 de largo x 2 de alto) = 40.
        assertEquals(142 + 40, faces(mesh));
    }

    @Test
    void dosBloquesAdyacentesNoDuplicanLasCarasDeSuFrontera() {
        World world = world16();
        LogicalBlock a = world.addBlock(0, 0, 0);
        LogicalBlock b = world.addBlock(1, 0, 0);
        // La ultima celda de A (global 15) y la primera de B (global 16) comparten cara.
        a.setMaterial(15, 0, 0, MaterialRegistry.STONE);
        b.setMaterial(0, 0, 0, MaterialRegistry.DIRT);

        MeshData worldMesh = MicroVoxelMeshBuilder.buildWorldMesh(world);
        MeshData meshA = MicroVoxelMeshBuilder.buildBlockMesh(world, a);
        MeshData meshB = MicroVoxelMeshBuilder.buildBlockMesh(world, b);

        assertEquals(10, faces(worldMesh), "La frontera compartida se culla en ambos bloques");
        assertEquals(5, faces(meshA), "El bloque A no genera su cara +X contra B");
        assertEquals(5, faces(meshB), "El bloque B no genera su cara -X contra A");
    }

    @Test
    void unaRegionVaciaEntreDosSuperficiesMantieneLasCarasQueLaDelimitan() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.setMaterial(0, 0, 0, MaterialRegistry.STONE);
        block.setMaterial(2, 0, 0, MaterialRegistry.STONE);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        // No se tocan: ambos cubos muestran sus seis caras.
        assertEquals(12, faces(mesh));
    }

    @Test
    void lasNormalesYElOrdenDeLosIndicesSonCoherentesConElCullingTrasero() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.setMaterial(2, 3, 4, MaterialRegistry.GOLD);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);
        float[] v = mesh.vertices();
        int[] idx = mesh.indices();

        for (int f = 0; f < idx.length; f += 3) {
            int i0 = idx[f] * MeshData.FLOATS_PER_VERTEX;
            int i1 = idx[f + 1] * MeshData.FLOATS_PER_VERTEX;
            int i2 = idx[f + 2] * MeshData.FLOATS_PER_VERTEX;

            float ux = v[i1] - v[i0];
            float uy = v[i1 + 1] - v[i0 + 1];
            float uz = v[i1 + 2] - v[i0 + 2];
            float wx = v[i2] - v[i0];
            float wy = v[i2 + 1] - v[i0 + 1];
            float wz = v[i2 + 2] - v[i0 + 2];

            // Producto vectorial (u x w): debe apuntar en la misma direccion que la normal.
            float nx = uy * wz - uz * wy;
            float ny = uz * wx - ux * wz;
            float nz = ux * wy - uy * wx;

            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            assertTrue(len > 1e-6f, "El triangulo no puede ser degenerado");
            nx /= len;
            ny /= len;
            nz /= len;

            assertEquals(v[i0 + 3], nx, 1e-4f, "Normal X coherente con el orden antihorario");
            assertEquals(v[i0 + 4], ny, 1e-4f, "Normal Y coherente con el orden antihorario");
            assertEquals(v[i0 + 5], nz, 1e-4f, "Normal Z coherente con el orden antihorario");
        }
    }

    @Test
    void losColoresDeVerticeProvienenDelMaterial() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.setMaterial(0, 0, 0, MaterialRegistry.RED);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);
        float[] v = mesh.vertices();
        var red = MaterialRegistry.createDemo().get(MaterialRegistry.RED);

        assertEquals(red.red(), v[6], 1e-6f);
        assertEquals(red.green(), v[7], 1e-6f);
        assertEquals(red.blue(), v[8], 1e-6f);
    }

    @Test
    void unMicroVoxelEnElLimiteDelMundoSigueMostrandoSusCarasExteriores() {
        World world = world16();
        LogicalBlock block = world.addBlock(0, 0, 0);
        block.setMaterial(15, 15, 15, MaterialRegistry.STONE);

        MeshData mesh = MicroVoxelMeshBuilder.buildBlockMesh(world, block);

        // El exterior del mundo se considera aire: las seis caras siguen visibles.
        assertEquals(6, faces(mesh));
    }
}
