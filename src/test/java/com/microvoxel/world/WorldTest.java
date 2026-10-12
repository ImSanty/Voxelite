package com.microvoxel.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.microvoxel.material.MaterialRegistry;

/** Verifica los totales incrementales del mundo y su vista lineal de bloques. */
class WorldTest {

    @Test
    void losTotalesSeMantienenAlRegistrarYRetirarBloques() {
        World world = new World(MaterialRegistry.createDemo(), 16);

        LogicalBlock primero = world.addBlock(0, 0, 0);
        primero.grid().fillAll(MaterialRegistry.STONE);

        LogicalBlock segundo = world.addBlock(1, 0, 0);
        segundo.grid().fillAll(MaterialRegistry.GRASS);

        assertEquals(2, world.blockCount());
        assertEquals(2, world.blocks().size());
        assertEquals(2L * 16 * 16 * 16, world.totalStoredMicroVoxels());
        assertEquals(2L * 16 * 16 * 16, world.totalOccupiedMicroVoxels());

        // Una edicion posterior dentro de un bloque se refleja de inmediato: la
        // ocupacion se lee del bloque, no de una copia del mundo.
        segundo.grid().clearBox(0, 0, 0, 15, 15, 15);
        assertEquals(16L * 16 * 16, world.totalOccupiedMicroVoxels());
        assertEquals(2L * 16 * 16 * 16, world.totalStoredMicroVoxels());

        world.removeBlock(new BlockPosition(1, 0, 0));
        assertEquals(1, world.blockCount());
        assertEquals(1, world.blocks().size());
        assertTrue(world.blocks().contains(primero));
        assertEquals(16L * 16 * 16, world.totalStoredMicroVoxels());
        assertEquals(16L * 16 * 16, world.totalOccupiedMicroVoxels());

        world.removeBlock(new BlockPosition(9, 9, 9));
        assertEquals(1, world.blockCount());
        assertEquals(16L * 16 * 16, world.totalStoredMicroVoxels());
    }

    @Test
    void losBloquesSueltosSonLosQueNoPertenecenANingunChunk() {
        World world = new World(MaterialRegistry.createDemo(), 16);

        // Los bloques nuevos empiezan sueltos (se dibujan con malla propia).
        LogicalBlock suelto = world.addBlock(0, 0, 0);
        LogicalBlock enChunkA = world.addBlock(1, 0, 0);
        LogicalBlock enChunkB = world.addBlock(2, 0, 0);
        assertEquals(3, world.looseBlocks().size());

        // Al registrarlos en un chunk dejan de estar sueltos.
        Chunk chunk = new Chunk(0, 0, 0);
        chunk.add(enChunkA);
        chunk.add(enChunkB);
        world.addChunk(chunk);

        List<LogicalBlock> sueltos = new ArrayList<>(world.looseBlocks());
        assertEquals(1, sueltos.size());
        assertTrue(sueltos.contains(suelto));

        // Retirar el bloque suelto tambien lo saca de la lista lineal.
        world.removeBlock(new BlockPosition(0, 0, 0));
        assertEquals(0, world.looseBlocks().size());
        assertEquals(2, world.blockCount());
    }
}
