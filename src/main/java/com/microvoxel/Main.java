package com.microvoxel;

import com.microvoxel.engine.Engine;

/**
 * Punto de entrada de la aplicacion.
 *
 * <p>Arranca el motor de voxels. Requiere una GPU y controladores compatibles con
 * OpenGL 3.3 Core; si el contexto no se puede crear, el motor muestra un mensaje
 * de error descriptivo.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        new Engine().run();
    }
}
