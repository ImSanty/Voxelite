package com.microvoxel.material;

import org.joml.Vector3f;

/**
 * Material de color solido aplicable a un microvoxel.
 *
 * <p>No se utilizan texturas: el color viaja al shader como atributo de vertice
 * y se modula con la iluminacion direccional. El campo {@code response} permite
 * un ajuste minimo de la respuesta a la luz (por ejemplo, un material mas mate
 * o mas brillante) sin introducir un modelo PBR completo.</p>
 *
 * @param id       identificador unico; {@code 0} esta reservado para el aire (vacio)
 * @param name     nombre descriptivo para la depuracion
 * @param color    color RGB solido en el rango [0,1]
 * @param response multiplicador de la componente difusa (1.0 = comportamiento normal)
 * @param solid    {@code true} si el material detiene al jugador; el aire no es solido
 *                 y el agua tampoco (se puede atravesar)
 */
public record VoxelMaterial(int id, String name, Vector3f color, float response, boolean solid) {

    /** Identificador reservado para el aire. Ningun material puede usarlo. */
    public static final int AIR_ID = 0;

    public VoxelMaterial {
        if (id == AIR_ID) {
            throw new IllegalArgumentException("El id 0 esta reservado para el aire");
        }
        if (id < 0) {
            throw new IllegalArgumentException("El id de material debe ser >= 0");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El material necesita un nombre");
        }
        // Copia defensiva: el color es inmutable a efectos del motor.
        color = new Vector3f(color);
    }

    public float red() {
        return color.x;
    }

    public float green() {
        return color.y;
    }

    public float blue() {
        return color.z;
    }

    /**
     * Material solido por defecto.
     *
     * <p>Es el equivalente de {@code Tile.isSolid()} del original: cada material
     * declara si frena al jugador. Solo el agua lo desactiva en la escena de
     * demostracion.</p>
     */
    public VoxelMaterial(int id, String name, Vector3f color, float response) {
        this(id, name, color, response, true);
    }
}
