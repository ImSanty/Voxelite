package com.microvoxel.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.material.VoxelMaterial;
import com.microvoxel.phys.AABB;

/**
 * Mundo de la escena: agrupa bloques logicos y resuelve consultas de ocupacion
 * en coordenadas globales de microvoxel.
 *
 * <p>Esta clase es la autoridad para responder "que hay en esta celda global".
 * El generador de malla la usa para consultar los vecinos que caen fuera del
 * bloque actual, lo que resuelve de forma uniforme el culling de caras internas,
 * las fronteras entre bloques adyacentes y los bordes del mundo.</p>
 *
 * <p>La resolucion de microvoxels es una propiedad del mundo y se aplica a todos
 * los bloques.</p>
 *
 * <p><b>Bloques materializados y bloques de fuente.</b> El mundo combina dos
 * origenes de informacion, en este orden de prioridad:</p>
 *
 * <ol>
 *   <li>Los {@link LogicalBlock} registrados: tienen forma propia (macizos,
 *       perforados, con cavidades o editados). Son la unica cosa que se guarda en
 *       memoria bloque a bloque.</li>
 *   <li>La {@link BlockSource} opcional: terreno generado que <b>no</b> existe
 *       como objetos y que el mundo deduce por columna. Sus bloques son macizos y
 *       de un unico material.</li>
 * </ol>
 *
 * <p>Todas las consultas de ocupacion (meshing y colision incluidas) pasan por
 * {@link #materialCode(int, int, int)}, de modo que una misma respuesta sirve para
 * dibujar, para chocar y para calcular caras: no hay dos caminos que puedan
 * discrepar.</p>
 */
public final class World {

    /**
     * Cota inferior del mundo, en coordenadas de bloque. Coincide con el fondo del
     * terreno generado.
     */
    public static final int MIN_BLOCK_Y = -128;

    /**
     * Cota superior <b>de construccion</b>: por encima de ella el mundo no admite
     * bloques. El terreno generado se queda bastante mas abajo (su techo de relieve
     * es {@code TerrainGenerator.MAX_SURFACE_BLOCK_Y}), de modo que queda aire de
     * sobra para construir.
     */
    public static final int MAX_BUILD_BLOCK_Y = 512;

    private final Map<BlockPosition, LogicalBlock> blocks = new LinkedHashMap<>();

    /**
     * Terreno generado por debajo de los bloques registrados, o {@code null} si el
     * mundo solo tiene bloques materializados (los mundos de las pruebas, por
     * ejemplo).
     */
    private BlockSource blockSource;
    /**
     * Vista lineal de los bloques, en orden de alta. El mapa mantiene la busqueda
     * por posicion; esta lista evita recorrer la tabla hash en los bucles que se
     * ejecutan cada fotograma (render, generacion de malla y estadisticas), donde
     * iterar decenas de miles de entradas era el coste dominante.
     */
    private final List<LogicalBlock> blockList = new ArrayList<>();
    /**
     * Bloques que no pertenecen a ningun chunk (los de demostracion, que se dibujan
     * con malla propia). El renderizador recorre cada fotograma los bloques sueltos
     * para ver si alguno esta sucio: con la lista completa habria que examinar los
     * mas de 120.000 bloques del terreno para encontrar esos pocos.
     */
    private final List<LogicalBlock> looseBlocks = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    /** Bloques que pertenecen a algun chunk (por identidad, no por equals). */
    private final Set<LogicalBlock> chunkedBlocks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final MaterialRegistry materials;

    /**
     * Suma de celdas de todos los bloques registrados, mantenida al dar de alta y
     * de baja bloques. La resolucion de un bloque es inmutable, por lo que este
     * total solo cambia cuando cambia el conjunto de bloques.
     */
    private long storedMicroVoxels;

    private final int resolutionX;
    private final int resolutionY;
    private final int resolutionZ;

    public World(MaterialRegistry materials, int resolutionX, int resolutionY, int resolutionZ) {
        if (materials == null) {
            throw new IllegalArgumentException("El mundo necesita un registro de materiales");
        }
        this.materials = materials;
        this.resolutionX = resolutionX;
        this.resolutionY = resolutionY;
        this.resolutionZ = resolutionZ;
    }

    public World(MaterialRegistry materials, int resolution) {
        this(materials, resolution, resolution, resolution);
    }

    public MaterialRegistry materials() {
        return materials;
    }

    public int resolutionX() {
        return resolutionX;
    }

    public int resolutionY() {
        return resolutionY;
    }

    public int resolutionZ() {
        return resolutionZ;
    }

    // ------------------------------------------------------------------
    // Registro de bloques.
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Fuente de bloques no materializados (terreno generado).
    // ------------------------------------------------------------------

    /** Instala la fuente de bloques del terreno generado. */
    public void setBlockSource(BlockSource source) {
        this.blockSource = source;
    }

    public BlockSource blockSource() {
        return blockSource;
    }

    /** Crea y registra un bloque vacio en una posicion. */
    public LogicalBlock addBlock(int x, int y, int z) {
        return addBlock(new BlockPosition(x, y, z));
    }

    public LogicalBlock addBlock(BlockPosition position) {
        checkBounds(position);
        if (blocks.containsKey(position)) {
            throw new IllegalArgumentException("Ya existe un bloque en " + position);
        }
        LogicalBlock block = new LogicalBlock(position, resolutionX, resolutionY, resolutionZ);
        blocks.put(position, block);
        blockList.add(block);
        looseBlocks.add(block);
        storedMicroVoxels += (long) resolutionX * resolutionY * resolutionZ;
        return block;
    }

    /** Registra un bloque ya construido, validando su resolucion. */
    public void addBlock(LogicalBlock block) {
        if (block.resX() != resolutionX || block.resY() != resolutionY || block.resZ() != resolutionZ) {
            throw new IllegalArgumentException(
                    "El bloque " + block.position() + " tiene resolucion "
                            + block.resX() + "x" + block.resY() + "x" + block.resZ()
                            + " y el mundo usa " + resolutionX + "x" + resolutionY + "x" + resolutionZ);
        }
        checkBounds(block.position());
        if (blocks.containsKey(block.position())) {
            throw new IllegalArgumentException("Ya existe un bloque en " + block.position());
        }
        blocks.put(block.position(), block);
        blockList.add(block);
        looseBlocks.add(block);
        storedMicroVoxels += (long) resolutionX * resolutionY * resolutionZ;
    }

    // ------------------------------------------------------------------
    // Chunks: agrupaciones de bloques para meshing combinado.
    // ------------------------------------------------------------------

    /**
     * Registra un chunk cuyos bloques ya estan dados de alta en el mundo (bien
     * por {@link #addBlock} o por {@link #addBlock(LogicalBlock)}).
     */
    public void addChunk(Chunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("El mundo no admite chunks nulos");
        }
        chunks.add(chunk);
        for (LogicalBlock block : chunk.blocks()) {
            if (blocks.get(block.position()) != block) {
                throw new IllegalArgumentException(
                        "El bloque " + block.position() + " del chunk no esta registrado en el mundo");
            }
            chunkedBlocks.add(block);
            looseBlocks.remove(block);
        }
    }

    public List<Chunk> chunks() {
        return Collections.unmodifiableList(chunks);
    }

    /** {@code true} si el bloque pertenece a algun chunk (se dibuja combinado). */
    public boolean isInChunk(LogicalBlock block) {
        return chunkedBlocks.contains(block);
    }

    public LogicalBlock getBlock(BlockPosition position) {
        return blocks.get(position);
    }

    public LogicalBlock getBlock(int x, int y, int z) {
        return blocks.get(new BlockPosition(x, y, z));
    }

    public boolean hasBlock(BlockPosition position) {
        return blocks.containsKey(position);
    }

    public Collection<LogicalBlock> blocks() {
        return Collections.unmodifiableList(blockList);
    }

    /**
     * Bloques que no pertenecen a ningun chunk y por tanto se dibujan con malla
     * propia. Es una lista pequena (los bloques de demostracion), pensada para los
     * bucles que se ejecutan cada fotograma.
     */
    public Collection<LogicalBlock> looseBlocks() {
        return Collections.unmodifiableList(looseBlocks);
    }

    /** Numero de bloques <b>materializados</b> (los que ocupan memoria). */
    public int blockCount() {
        return blocks.size();
    }

    /**
     * Comprueba que una posicion cae dentro de las cotas del mundo.
     *
     * @throws IllegalArgumentException si la altura queda fuera de
     *         {@code [MIN_BLOCK_Y, MAX_BUILD_BLOCK_Y]}
     */
    private static void checkBounds(BlockPosition position) {
        if (position.y() < MIN_BLOCK_Y || position.y() > MAX_BUILD_BLOCK_Y) {
            throw new IllegalArgumentException(
                    "Altura fuera del mundo: y = " + position.y()
                            + " (el mundo va de y = " + MIN_BLOCK_Y
                            + " a y = " + MAX_BUILD_BLOCK_Y + ")");
        }
    }

    public void removeBlock(BlockPosition position) {
        LogicalBlock removed = blocks.remove(position);
        if (removed != null) {
            chunkedBlocks.remove(removed);
            blockList.remove(removed);
            looseBlocks.remove(removed);
            storedMicroVoxels -= (long) removed.resX() * removed.resY() * removed.resZ();
        }
    }

    // ------------------------------------------------------------------
    // Consultas globales de microvoxels.
    // ------------------------------------------------------------------

    /**
     * Material de una coordenada global de microvoxel.
     * Devuelve {@code 0} (vacio) si no hay bloque en esa posicion: el exterior
     * del mundo se considera aire, tal y como exige el culling de bordes.
     */
    public int getMaterialGlobal(int gx, int gy, int gz) {
        BlockPosition bp = BlockPosition.ofGlobalMicroVoxel(gx, gy, gz, resolutionX, resolutionY, resolutionZ);
        LogicalBlock block = blocks.get(bp);
        if (block != null) {
            return block.getMaterialGlobal(gx, gy, gz);
        }
        // Bloque no materializado: la fuente responde por bloques completos, asi
        // que su material vale para cualquier celda de este.
        return blockSource != null ? materialCodeFromSource(bp) : 0;
    }

    public boolean isOccupiedGlobal(int gx, int gy, int gz) {
        return getMaterialGlobal(gx, gy, gz) != 0;
    }

    /**
     * Numero total de microvoxels almacenados <b>en memoria</b> (celdas de todas
     * las grillas materializadas). Coste constante: el total se mantiene al
     * registrar y retirar bloques.
     */
    public long totalStoredMicroVoxels() {
        return storedMicroVoxels;
    }

    /**
     * Material de un bloque, sin materializarlo.
     *
     * <p>Es <b>la</b> consulta de bloques del mundo: la usan el generador de malla,
     * la colision y las estadisticas, de modo que no puede haber dos respuestas
     * distintas sobre si un bloque existe.</p>
     *
     * @return {@code 0} si el bloque es aire, el identificador de material si es
     *         macizo y uniforme, y {@code -1} si es un bloque materializado con
     *         forma propia (perforado, con cavidades o editado)
     */
    public int materialCode(int blockX, int blockY, int blockZ) {
        if (!blocks.isEmpty()) {
            LogicalBlock block = blocks.get(new BlockPosition(blockX, blockY, blockZ));
            if (block != null) {
                return block.uniformMaterial();
            }
        }
        return blockSource != null ? materialCodeFromSource(blockX, blockY, blockZ) : 0;
    }

    /** {@code true} si el bloque existe y frena al jugador. */
    public boolean isSolidBlock(int blockX, int blockY, int blockZ) {
        return isSolidMaterial(materialCode(blockX, blockY, blockZ));
    }

    private int materialCodeFromSource(int blockX, int blockY, int blockZ) {
        if (blockY < MIN_BLOCK_Y || blockY > MAX_BUILD_BLOCK_Y) {
            return 0;
        }
        return blockSource.materialAt(blockX, blockY, blockZ);
    }

    private int materialCodeFromSource(BlockPosition position) {
        return materialCodeFromSource(position.x(), position.y(), position.z());
    }

    /** Numero total de microvoxels ocupados. */
    public long totalOccupiedMicroVoxels() {
        long total = 0;
        for (LogicalBlock b : blockList) {
            total += b.countOccupied();
        }
        return total;
    }

    /** {@code true} si el material frena al jugador ({@code false} para aire y agua). */
    public boolean isSolidMaterial(int materialId) {
        if (materialId == 0) {
            return false;
        }
        VoxelMaterial material = materials.getOrNull(materialId);
        return material != null && material.solid();
    }

    // ------------------------------------------------------------------
    // Colision: cubos solidos que solapan una region.
    // ------------------------------------------------------------------

    /**
     * Cubos solidos que solapan la region dada.
     *
     * <p>
     * Adaptacion de {@code Level.getCubes} del sistema de colision de rd-161348.
     * El original devolvia una caja por tile solido; aqui la unidad de ocupacion
     * es el microvoxel, asi que:
     * </p>
     *
     * <ul>
     *   <li>Un bloque macizo y uniforme de material solido (el caso de todo el
     *       terreno, materializado o no) aporta <b>un unico cubo</b> de una unidad:
     *       es equivalente al barrido y evita generar 4.096 cajas de 1/16. La
     *       consulta pasa por {@link #materialCode(int, int, int)}, de modo que el
     *       jugador choca contra la roca generada aunque no exista como objeto.</li>
     *   <li>Un bloque con forma (tuneles, cavidades, jaulas, escaleras de la
     *       escena) aporta <b>una caja de 1/16 por celda ocupada</b> dentro de la
     *       region, de modo que la colision respeta la subdivison real.</li>
     *   <li>El aire y los materiales no solidos (agua) no aportan nada.</li>
     * </ul>
     *
     * <p>
     * Solo se recorren los bloques que la region puede tocar, asi que el coste
     * depende del tamano del volumen barrido y no del mundo entero. A diferencia
     * del original no se anade un margen de un tile: el rango derivado de
     * {@code floor}/{@code ceil} ya cubre toda celda que solape la region.
     * </p>
     *
     * @param region volumen a consultar (normalmente la caja del jugador ya
     *               expandida por su movimiento)
     * @return cajas estaticas contra las que recortar el movimiento
     */
    public List<AABB> solidCubes(AABB region) {
        List<AABB> cubes = new ArrayList<>();

        int minBx = (int) Math.floor(region.minX);
        int maxBx = (int) Math.ceil(region.maxX) - 1;
        int minBy = (int) Math.floor(region.minY);
        int maxBy = (int) Math.ceil(region.maxY) - 1;
        int minBz = (int) Math.floor(region.minZ);
        int maxBz = (int) Math.ceil(region.maxZ) - 1;

        for (int bz = minBz; bz <= maxBz; bz++) {
            for (int by = minBy; by <= maxBy; by++) {
                for (int bx = minBx; bx <= maxBx; bx++) {
                    int code = materialCode(bx, by, bz);
                    if (code > 0) {
                        if (isSolidMaterial(code)) {
                            cubes.add(new AABB(bx, by, bz, bx + 1, by + 1, bz + 1));
                        }
                    } else if (code < 0) {
                        appendMicroCubes(blocks.get(new BlockPosition(bx, by, bz)), region, cubes);
                    }
                }
            }
        }
        return cubes;
    }

    /** Anade una caja de 1/res por cada celda solida del bloque que solape la region. */
    private void appendMicroCubes(LogicalBlock block, AABB region, List<AABB> cubes) {
        int bx = block.position().x();
        int by = block.position().y();
        int bz = block.position().z();

        int resX = block.resX();
        int resY = block.resY();
        int resZ = block.resZ();

        int minLx = Math.max(0, (int) Math.floor((region.minX - bx) * resX));
        int maxLx = Math.min(resX - 1, (int) Math.ceil((region.maxX - bx) * resX) - 1);
        int minLy = Math.max(0, (int) Math.floor((region.minY - by) * resY));
        int maxLy = Math.min(resY - 1, (int) Math.ceil((region.maxY - by) * resY) - 1);
        int minLz = Math.max(0, (int) Math.floor((region.minZ - bz) * resZ));
        int maxLz = Math.min(resZ - 1, (int) Math.ceil((region.maxZ - bz) * resZ) - 1);

        for (int lz = minLz; lz <= maxLz; lz++) {
            for (int ly = minLy; ly <= maxLy; ly++) {
                for (int lx = minLx; lx <= maxLx; lx++) {
                    if (!isSolidMaterial(block.getMaterial(lx, ly, lz))) {
                        continue;
                    }
                    cubes.add(new AABB(
                            bx + (double) lx / resX, by + (double) ly / resY, bz + (double) lz / resZ,
                            bx + (double) (lx + 1) / resX, by + (double) (ly + 1) / resY,
                            bz + (double) (lz + 1) / resZ));
                }
            }
        }
    }

    /** Centroide de todos los bloques, util para orientar la camara inicial. */
    public org.joml.Vector3f center() {
        if (blocks.isEmpty()) {
            return new org.joml.Vector3f();
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (LogicalBlock b : blockList) {
            minX = Math.min(minX, b.minWorldX());
            minY = Math.min(minY, b.minWorldY());
            minZ = Math.min(minZ, b.minWorldZ());
            maxX = Math.max(maxX, b.maxWorldX());
            maxY = Math.max(maxY, b.maxWorldY());
            maxZ = Math.max(maxZ, b.maxWorldZ());
        }
        return new org.joml.Vector3f(
                (minX + maxX) * 0.5f,
                (minY + maxY) * 0.5f,
                (minZ + maxZ) * 0.5f);
    }
}
