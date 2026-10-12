package com.microvoxel.material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

/**
 * Registro central de materiales.
 *
 * <p>Los microvoxels solo almacenan un identificador entero; las propiedades
 * (nombre y color) se resuelven aqui. Esto evita duplicar las mismas propiedades
 * en cada celda, tal y como exige la seccion de rendimiento del enunciado.</p>
 *
 * <p>Es mutable durante la construccion de la escena, pero una vez poblado se
 * consulta de forma intensiva desde el generador de malla y el renderizador.</p>
 */
public final class MaterialRegistry {

    private final Map<Integer, VoxelMaterial> materials = new LinkedHashMap<>();

    /** Registra un material y devuelve su identificador. */
    public int register(VoxelMaterial material) {
        if (materials.containsKey(material.id())) {
            throw new IllegalArgumentException("Ya existe un material con id " + material.id());
        }
        materials.put(material.id(), material);
        return material.id();
    }

    /** Registra un material a partir de sus componentes y devuelve el id. */
    public int register(String name, float r, float g, float b) {
        return register(name, r, g, b, 1.0f);
    }

    public int register(String name, float r, float g, float b, float response) {
        int id = nextId();
        register(new VoxelMaterial(id, name, new Vector3f(r, g, b), response));
        return id;
    }

    private int nextId() {
        int id = 1;
        while (materials.containsKey(id)) {
            id++;
        }
        return id;
    }

    /**
     * Devuelve el material asociado a un identificador.
     *
     * @throws IllegalArgumentException si el id no esta registrado
     */
    public VoxelMaterial get(int id) {
        VoxelMaterial m = materials.get(id);
        if (m == null) {
            throw new IllegalArgumentException("Material no registrado: " + id);
        }
        return m;
    }

    public VoxelMaterial getOrNull(int id) {
        return materials.get(id);
    }

    public boolean contains(int id) {
        return materials.containsKey(id);
    }

    public Collection<VoxelMaterial> all() {
        return materials.values();
    }

    public int size() {
        return materials.size();
    }

    // ------------------------------------------------------------------
    // Materiales de demostracion.
    // ------------------------------------------------------------------

    public static final int STONE = 1;
    public static final int LIGHT_STONE = 2;
    public static final int DIRT = 3;
    public static final int GLASS_CYAN = 4;
    public static final int RED = 5;
    public static final int GREEN = 6;
    public static final int YELLOW = 7;
    public static final int GOLD = 8;
    public static final int WOOD = 9;

    // Materiales del terreno de demostracion (8x8 chunks).
    public static final int GRASS = 10;
    public static final int SAND = 11;
    public static final int WATER = 12;

    /** Construye el registro de materiales por defecto de la escena de demostracion. */
    public static MaterialRegistry createDemo() {
        MaterialRegistry r = new MaterialRegistry();
        r.register(new VoxelMaterial(STONE, "Piedra gris", new Vector3f(0.55f, 0.56f, 0.60f), 1.0f));
        r.register(new VoxelMaterial(LIGHT_STONE, "Piedra clara", new Vector3f(0.78f, 0.79f, 0.82f), 1.0f));
        r.register(new VoxelMaterial(DIRT, "Tierra marron", new Vector3f(0.45f, 0.31f, 0.19f), 1.0f));
        r.register(new VoxelMaterial(GLASS_CYAN, "Cristal cian", new Vector3f(0.25f, 0.82f, 0.92f), 1.15f));
        r.register(new VoxelMaterial(RED, "Rojo", new Vector3f(0.86f, 0.24f, 0.22f), 1.0f));
        r.register(new VoxelMaterial(GREEN, "Verde", new Vector3f(0.30f, 0.72f, 0.34f), 1.0f));
        r.register(new VoxelMaterial(YELLOW, "Amarillo", new Vector3f(0.92f, 0.80f, 0.25f), 1.0f));
        r.register(new VoxelMaterial(GOLD, "Oro", new Vector3f(0.95f, 0.66f, 0.16f), 1.2f));
        r.register(new VoxelMaterial(WOOD, "Madera", new Vector3f(0.62f, 0.44f, 0.24f), 0.9f));
        // Materiales del terreno: cesped en la superficie, arena en las orillas,
        // agua en los valles. Se apoyan en DIRT y STONE ya registrados.
        r.register(new VoxelMaterial(GRASS, "Cesped verde", new Vector3f(0.34f, 0.62f, 0.28f), 1.0f));
        r.register(new VoxelMaterial(SAND, "Arena", new Vector3f(0.85f, 0.79f, 0.55f), 1.0f));
        // El agua no es solida: el jugador la atraviesa (no bloquea el movimiento).
        r.register(new VoxelMaterial(WATER, "Agua azul", new Vector3f(0.20f, 0.46f, 0.82f), 1.10f, false));
        return r;
    }

    /** Nombres registrados, util para la interfaz de depuracion. */
    public List<String> names() {
        List<String> out = new ArrayList<>(materials.size());
        for (VoxelMaterial m : materials.values()) {
            out.add(m.id() + ":" + m.name());
        }
        return out;
    }
}
