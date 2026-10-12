package com.microvoxel.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import com.microvoxel.camera.Camera;
import com.microvoxel.mesh.MicroVoxelMeshBuilder;
import com.microvoxel.scene.DemoScene;
import com.microvoxel.scene.TerrainGenerator;
import com.microvoxel.world.Chunk;
import com.microvoxel.world.World;

/**
 * Verifica el descarte por frustum del renderizador.
 *
 * <p>El descarte solo puede <b>ahorrar</b> dibujos: un error suyo se ve enseguida
 * como terreno que desaparece. Esta prueba recorre las 16.384 columnas del terreno
 * desde varias camaras y exige que, si la superficie de una columna cae dentro de
 * la pantalla, el chunk que la contiene no pueda quedar descartado.</p>
 */
class FrustumCullingTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    @Test
    void ningunChunkConTerrenoVisibleQuedaDescartado() {
        DemoScene scene = new DemoScene();
        World world = scene.world();
        // El descarte usa la banda de cada chunk, que se calcula al mallarla.
        for (Chunk chunk : world.chunks()) {
            MicroVoxelMeshBuilder.buildChunkMesh(world, chunk);
        }

        float aspect = (float) WIDTH / HEIGHT;
        Camera[] cameras = {
                camara(scene.playerSpawn(), scene.playerLookTarget()),
                camara(new Vector3f(64.0f, 190.0f, 200.0f), new Vector3f(64.0f, 20.0f, 40.0f)),
                camara(new Vector3f(10.0f, 30.0f, 110.0f), new Vector3f(64.0f, 55.0f, 30.0f)),
                camara(new Vector3f(-40.0f, 40.0f, -40.0f), new Vector3f(60.0f, 10.0f, 60.0f)),
                camara(new Vector3f(120.0f, 5.0f, 120.0f), new Vector3f(-20.0f, 60.0f, -20.0f)),
        };

        int checkedVisible = 0;
        for (Camera camera : cameras) {
            Matrix4f viewProjection = new Matrix4f(camera.projectionMatrix(aspect))
                    .mul(camera.viewMatrix());
            FrustumIntersection frustum = new FrustumIntersection(viewProjection);

            for (int z = 0; z < TerrainGenerator.BLOCKS_Z; z++) {
                for (int x = 0; x < TerrainGenerator.BLOCKS_X; x++) {
                    if (!puntoEnPantalla(viewProjection, x, TerrainGenerator.surfaceTop(x, z), z)) {
                        continue;
                    }
                    checkedVisible++;
                    Chunk chunk = chunkAt(world, x, z);
                    assertTrue(frustum.testAab(
                                    chunk.minWorldX(), chunk.minWorldY(), chunk.minWorldZ(),
                                    chunk.maxWorldX(), chunk.maxWorldY(), chunk.maxWorldZ()),
                            "El chunk de la columna " + x + "," + z + " tiene superficie en pantalla"
                                    + " pero el frustum lo descarta (camara "
                                    + camera.position() + ")");
                }
            }
        }

        assertTrue(checkedVisible > 5_000,
                "Las camaras deberian ver bastantes columnas: " + checkedVisible);
    }

    private static boolean puntoEnPantalla(Matrix4f viewProjection, int blockX, int blockY, int blockZ) {
        Vector4f clip = viewProjection.transform(
                new Vector4f(blockX + 0.5f, blockY + 1.0f, blockZ + 0.5f, 1.0f));
        if (clip.w <= 0.0f) {
            return false; // detras de la camara
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float ndcZ = clip.z / clip.w;
        return Math.abs(ndcX) <= 1.0f && Math.abs(ndcY) <= 1.0f && ndcZ >= -1.0f && ndcZ <= 1.0f;
    }

    private static Chunk chunkAt(World world, int blockX, int blockZ) {
        Chunk found = null;
        for (Chunk chunk : world.chunks()) {
            if (blockX / Chunk.BLOCKS_X == chunk.chunkX() && blockZ / Chunk.BLOCKS_Z == chunk.chunkZ()) {
                found = chunk;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("Sin chunk para la columna " + blockX + "," + blockZ);
        }
        return found;
    }

    private static Camera camara(Vector3f position, Vector3f target) {
        Camera camera = new Camera();
        camera.setPosition(position);
        camera.lookAt(target);
        return camera;
    }
}
