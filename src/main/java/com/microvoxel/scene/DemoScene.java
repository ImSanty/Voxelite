package com.microvoxel.scene;

import org.joml.Vector3f;

import com.microvoxel.material.MaterialRegistry;
import com.microvoxel.phys.Player;
import com.microvoxel.world.LogicalBlock;
import com.microvoxel.world.World;

/**
 * Escena de demostracion del motor.
 *
 * <p>Construye por completo mediante codigo, sin modelos externos, un pequeno
 * mundo de bloques logicos cuyas formas se definen vaciando y rellenando
 * microvoxels. Cada bloque ilustra una capacidad distinta del modelo
 * volumetrico:</p>
 *
 * <ol>
 *   <li>Solido: un bloque convencional completamente ocupado.</li>
 *   <li>Perforado: un tunel que lo atraviesa por completo.</li>
 *   <li>Hueco: una cavidad interior con un acceso superior.</li>
 *   <li>Escalonado: niveles construidos por capas de microvoxels.</li>
 *   <li>Irregular: una forma asimetrica con voladizos y muescas.</li>
 *   <li>Multimaterial: tres materiales combinados en las tres dimensiones.</li>
 *   <li>Componentes separados: cinco regiones desconectadas entre si.</li>
 *   <li>Jaula: paredes finas perforadas que dejan ver el interior.</li>
 *   <li>Arco: un muro con una abertura semicircular tallada.</li>
 * </ol>
 *
 * <p>Los bloques se disponen en una cuadricula 3x3 con una unidad de separacion
 * para poder observarlos comodamente desde la posicion inicial de la camara.</p>
 *
 * <p>Ademas, la escena incorpora un terreno generado por {@link TerrainGenerator}:
 * 8x8 chunks de 16x16 columnas, de {@value TerrainGenerator#MIN_BLOCK_Y} a
 * {@value TerrainGenerator#MAX_SURFACE_BLOCK_Y} en Y. Ese terreno <b>no existe
 * como bloques</b> (se deduce por columna), de modo que los unicos bloques
 * materializados son los de demostracion, que se colocan por encima del relieve
 * local en {@code y = }{@value #DEMO_BLOCK_Y}.</p>
 */
public final class DemoScene {

    /** Resolucion por defecto de los bloques de la escena. */
    public static final int DEFAULT_RESOLUTION = 16;

    /**
     * Capa de bloque en la que se colocan los bloques de demostracion: una unidad
     * por encima del punto mas alto del terreno bajo su huella.
     *
     * <p>Se deduce del relieve, nunca se fija a mano: si la superficie subiera, los
     * bloques de demostracion quedarian enterrados dentro de la montana.</p>
     */
    public static final int DEMO_BLOCK_Y = TerrainGenerator.maxSurfaceTop(0, 5, 0, 5) + 1;

    /**
     * Columna preferida para la aparicion del jugador. Debe ser una columna de
     * cesped por encima del nivel del agua, a media distancia de los bloques demo;
     * si el relieve deja esa columna inundada, se busca la primera columna valida
     * alrededor ({@link #findSpawnColumn()}).
     */
    public static final int SPAWN_BLOCK_X = 16;
    public static final int SPAWN_BLOCK_Z = 16;

    private final MaterialRegistry materials;
    private final World world;

    public DemoScene() {
        this(DEFAULT_RESOLUTION);
    }

    public DemoScene(int resolution) {
        this.materials = MaterialRegistry.createDemo();
        this.world = new World(materials, resolution);
        build();
    }

    private void build() {
        // Terreno por columnas de 8x8 chunks desde y = -128 hasta el relieve, sin
        // materializar un solo bloque. Los bloques de demostracion, encima.
        TerrainGenerator.build(world);

        // Fila z = 0.
        solidBlock(0, DEMO_BLOCK_Y, 0);
        tunnelBlock(2, DEMO_BLOCK_Y, 0);
        cageBlock(4, DEMO_BLOCK_Y, 0);

        // Fila z = 2.
        cavityBlock(0, DEMO_BLOCK_Y, 2);
        stairsBlock(2, DEMO_BLOCK_Y, 2);
        archBlock(4, DEMO_BLOCK_Y, 2);

        // Fila z = 4.
        asymmetricBlock(0, DEMO_BLOCK_Y, 4);
        mixedMaterialsBlock(2, DEMO_BLOCK_Y, 4);
        disconnectedBlock(4, DEMO_BLOCK_Y, 4);
    }

    private LogicalBlock solidBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.solid(block.grid(), MaterialRegistry.STONE);
        return block;
    }

    private LogicalBlock tunnelBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.solid(block.grid(), MaterialRegistry.LIGHT_STONE);
        // Tunel de seccion 4x4 que atraviesa el bloque a lo largo de Z.
        ShapeBuilder.tunnelAlongZ(block.grid(), 4, MaterialRegistry.LIGHT_STONE);
        // Un segundo tunel perpendicular (X) crea un cruce visible desde arriba.
        ShapeBuilder.tunnelAlongX(block.grid(), 3);
        return block;
    }

    private LogicalBlock cavityBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.openCavity(block.grid(), 2, MaterialRegistry.DIRT);
        return block;
    }

    private LogicalBlock stairsBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.stairs(block.grid(), 8, 2, MaterialRegistry.WOOD);
        return block;
    }

    private LogicalBlock archBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.arch(block.grid(), MaterialRegistry.LIGHT_STONE);
        return block;
    }

    private LogicalBlock asymmetricBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.asymmetric(block.grid(), MaterialRegistry.RED);
        return block;
    }

    private LogicalBlock mixedMaterialsBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.mixedMaterials(block.grid(), MaterialRegistry.RED,
                MaterialRegistry.GREEN, MaterialRegistry.YELLOW);
        return block;
    }

    private LogicalBlock disconnectedBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.disconnectedComponents(block.grid(), MaterialRegistry.GOLD,
                MaterialRegistry.DIRT, MaterialRegistry.STONE);
        return block;
    }

    private LogicalBlock cageBlock(int x, int y, int z) {
        LogicalBlock block = world.addBlock(x, y, z);
        ShapeBuilder.cage(block.grid(), MaterialRegistry.GLASS_CYAN);
        return block;
    }

    public World world() {
        return world;
    }

    public MaterialRegistry materials() {
        return materials;
    }

    /**
     * Separacion con la que aparece el jugador sobre la superficie, en unidades
     * de mundo (media capa de microvoxels).
     *
     * <p>No es decorativa: la superficie del mundo es {@code float} y la caja del
     * jugador es {@code double}, asi que apoyarla exactamente en la cara del bloque
     * deja el error de redondeo a merced del azar (la caja puede quedar unas
     * decimas de millonésima dentro del bloque). Con esta holgura la gravedad lo
     * asienta en el primer paso y la posicion inicial siempre es libre.</p>
     */
    public static final float SPAWN_CLEARANCE = 1.0f / 32.0f;

    /**
     * Posicion inicial del jugador: de pie sobre la superficie solida de la
     * columna del spawn, con los ojos a la altura del jugador.
     *
     * <p>La altura no esta fijada a mano: se deduce del mundo, columna a columna,
     * buscando el bloque mas alto de material solido. Asi el spawn sigue siendo
     * valido si el relieve cambia, y el agua nunca se toma como suelo.</p>
     */
    public Vector3f playerSpawn() {
        int[] column = findSpawnColumn();
        float surface = highestSolidSurface(column[0], column[1]);
        float eye = (float) (surface + (double) Player.EYE_HEIGHT + SPAWN_CLEARANCE);
        return new Vector3f(column[0] + 0.5f, eye, column[1] + 0.5f);
    }

    /**
     * Columna de aparicion: la preferida si su superficie esta por encima del agua,
     * o la primera columna valida alrededor, en anillos crecientes.
     *
     * <p>Es determinista y no depende de mirar el mundo: la decision sale del
     * relieve, de modo que el spawn sigue siendo valido si el terreno cambia.</p>
     */
    public static int[] findSpawnColumn() {
        for (int radius = 0; radius < 32; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue; // solo el anillo
                    }
                    int x = SPAWN_BLOCK_X + dx;
                    int z = SPAWN_BLOCK_Z + dz;
                    if (!TerrainGenerator.insideFootprint(x, z)) {
                        continue;
                    }
                    if (TerrainGenerator.surfaceTop(x, z) > TerrainGenerator.WATER_LEVEL_BLOCK_Y + 1) {
                        return new int[] { x, z };
                    }
                }
            }
        }
        return new int[] { SPAWN_BLOCK_X, SPAWN_BLOCK_Z };
    }

    /** Punto al que mira el jugador al arrancar: el centro de los bloques de demostracion. */
    public Vector3f playerLookTarget() {
        return new Vector3f(2.5f, DEMO_BLOCK_Y + 0.5f, 2.5f);
    }

    /**
     * Altura (en unidades de mundo) de la cara superior del bloque solido mas
     * alto de una columna, o {@code TerrainGenerator.MIN_BLOCK_Y} si no hay.
     *
     * <p>Se consulta al mundo, no a los bloques: asi sirve igual para el terreno
     * generado (que no existe como objetos) que para los bloques materializados.</p>
     */
    private float highestSolidSurface(int blockX, int blockZ) {
        // Se busca de arriba abajo hasta el fondo del mundo: el terreno puede
        // empezar por debajo de cero.
        for (int by = TerrainGenerator.MAX_SURFACE_BLOCK_Y; by >= TerrainGenerator.MIN_BLOCK_Y; by--) {
            int material = world.materialCode(blockX, by, blockZ);
            if (material > 0 && world.isSolidMaterial(material)) {
                return by + 1.0f;
            }
        }
        return TerrainGenerator.MIN_BLOCK_Y;
    }
}
