package com.microvoxel.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Comprueba que las conversiones de coordenadas sean consistentes, incluso en negativos. */
class BlockPositionTest {

    @Test
    void laUltimaCeldaDeUnBloqueYLaPrimeraDelVecinoCompartenFrontera() {
        int res = 16;
        // Celda global 15 es la ultima del bloque 0; la 16 es la primera del bloque 1.
        assertEquals(new BlockPosition(0, 0, 0),
                BlockPosition.ofGlobalMicroVoxel(15, 0, 0, res, res, res));
        assertEquals(new BlockPosition(1, 0, 0),
                BlockPosition.ofGlobalMicroVoxel(16, 0, 0, res, res, res));
    }

    @Test
    void lasPosicionesNegativasUsanDivisionConSignoMatematico() {
        int res = 16;
        assertEquals(new BlockPosition(-1, -1, -1),
                BlockPosition.ofGlobalMicroVoxel(-1, -1, -1, res, res, res));
        assertEquals(new BlockPosition(-1, 0, 0),
                BlockPosition.ofGlobalMicroVoxel(-16, 0, 0, res, res, res));
        assertEquals(new BlockPosition(-2, 0, 0),
                BlockPosition.ofGlobalMicroVoxel(-17, 0, 0, res, res, res));
    }

    @Test
    void localYGlobalSonInversas() {
        LogicalBlock block = new LogicalBlock(new BlockPosition(2, -3, 1), 16, 16, 16);
        for (int lx = 0; lx < 16; lx++) {
            int gx = block.globalMicroX(lx);
            assertEquals(lx, block.toLocalX(gx));
            assertEquals(new BlockPosition(2, -3, 1),
                    BlockPosition.ofGlobalMicroVoxel(gx, block.globalMicroY(5), block.globalMicroZ(5), 16, 16, 16));
        }
    }

    @Test
    void containsGlobalMicroDetectaPertenencia() {
        LogicalBlock block = new LogicalBlock(new BlockPosition(1, 0, 0), 16, 16, 16);
        assertEquals(true, block.containsGlobalMicro(16, 0, 0));
        assertEquals(true, block.containsGlobalMicro(31, 15, 15));
        assertEquals(false, block.containsGlobalMicro(15, 0, 0));
        assertEquals(false, block.containsGlobalMicro(32, 0, 0));
    }
}
