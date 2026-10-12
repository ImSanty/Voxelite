package com.microvoxel.world;

/**
 * Fuente de bloques <b>no materializados</b> para el mundo.
 *
 * <p>Permite que el terreno exista sin existir como objetos: el mundo pregunta a
 * la fuente que hay en una posicion que no tiene {@link LogicalBlock} y la fuente
 * lo deduce de su modelo (por ejemplo, la altura de la columna y sus estratos).
 * Asi un terreno de 192 capas de alto cuesta lo que su relieve, no 2,4 millones de
 * bloques en memoria.</p>
 *
 * <p>Los bloques de una fuente son siempre <b>macizos y de un unico material</b>:
 * es la unica forma de responder sin materializar. Un bloque que necesite forma
 * propia (perforado, con cavidades, editado) se registra en el mundo como
 * {@link LogicalBlock}, que tiene prioridad sobre la fuente.</p>
 */
public interface BlockSource {

    /**
     * Material del bloque en una posicion, o {@code 0} si es aire.
     *
     * <p>Fuera del volumen que la fuente conoce debe responder {@code 0}: el
     * exterior del mundo es aire y asi el culling de caras sigue siendo correcto en
     * los bordes.</p>
     */
    int materialAt(int blockX, int blockY, int blockZ);

    /**
     * Atajo para celdas que no pueden producir ninguna cara por estar enterradas.
     *
     * <p>El generador de malla recorre el rango vertical de cada chunk y llama a
     * esto antes de consultar los seis vecinos. En un terreno macizo la inmensa
     * mayoria de las celdas son roca interior: con este atajo se saltan con un
     * puñado de comparaciones en lugar de seis consultas. Una fuente que no pueda
     * responder barato debe dejarlo en {@code false} (es correcto, solo mas lento).
     *
     * @return {@code true} si la celda esta rodeada de solido y por tanto no genera
     *         geometria
     */
    default boolean isHiddenInterior(int blockX, int blockY, int blockZ) {
        return false;
    }

    /**
     * Rango vertical {@code [min, max]} (inclusive, en coordenadas de bloque) que
     * puede producir caras en un rectangulo inclusivo de columnas.
     *
     * <p>Es lo que permite que el generador de malla de un chunk recorra solo lo
     * que puede dar geometria en lugar de las 192 capas del mundo. El valor por
     * defecto es el rango completo del mundo: correcto, pero sin ahorro.</p>
     *
     * @return {@code {min, max}} o {@code null} si en ese rectangulo no hay nada
     */
    default int[] faceRange(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        return new int[] { World.MIN_BLOCK_Y, World.MAX_BUILD_BLOCK_Y };
    }
}
