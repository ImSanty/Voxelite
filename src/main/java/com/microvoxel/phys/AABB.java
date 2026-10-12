package com.microvoxel.phys;

/**
 * Caja envolvente alineada a los ejes (AABB) con el recorte de movimiento.
 *
 * <p>
 * Puerto del sistema de colision de {@code rd-161348}
 * ({@code com.mojang.rubydung.phys.AABB}). Esta clase no consulta el mundo: solo
 * resuelve la geometria del barrido.
 * </p>
 *
 * <p>
 * <b>Convenio de los recortes</b>, igual que en el original: {@code this} es la
 * caja <i>estatica</i> (un cubo solido del mundo) y el argumento es la caja que
 * se mueve. {@link #clipXCollide}, {@link #clipYCollide} y
 * {@link #clipZCollide} devuelven el desplazamiento corregido para que la caja
 * movil quede apoyada contra esta sin solaparla. {@link Player} los aplica eje
 * por eje (primero Y, despues X y por ultimo Z) recorriendo los cubos solidos
 * que devuelve el mundo.
 * </p>
 *
 * <p>
 * Un recorte solo actua si la caja movil esta <i>separada</i> en el eje del
 * movimiento: si ya se solapan, el recorte no la empuja ni la frena, y desde esa
 * posicion se puede salir caminando.
 * </p>
 *
 * <p>
 * La aritmetica se mantiene en {@code double}, igual que en el original: el
 * recorte y la deteccion de suelo son sensibles al redondeo, y con {@code float}
 * un deslizamiento de decimas de microvoxel bastaba para atravesar un borde.
 * </p>
 *
 * <p>
 * El margen de separacion es {@code 0.0}, como en el original: la caja queda
 * exactamente en contacto con la superficie.
 * </p>
 */
public final class AABB {

    /** Margen que se deja entre las dos cajas al recortar el movimiento. */
    private static final double EPSILON = 0.0;

    public double minX;
    public double minY;
    public double minZ;
    public double maxX;
    public double maxY;
    public double maxZ;

    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /** Copia independiente de esta caja. */
    public AABB copy() {
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Expande la caja en el sentido del desplazamiento: un valor negativo crece
     * hacia el lado minimo y uno positivo hacia el maximo.
     *
     * <p>
     * Es la pieza que convierte un movimiento en el volumen de barrido: el
     * resultado contiene todas las posiciones que ocupara la caja durante el
     * desplazamiento, y es lo que se consulta al mundo para reunir los cubos
     * candidatos.
     * </p>
     *
     * @param x desplazamiento en X
     * @param y desplazamiento en Y
     * @param z desplazamiento en Z
     * @return una caja nueva con el volumen barrido
     */
    public AABB expand(double x, double y, double z) {
        double newMinX = minX;
        double newMinY = minY;
        double newMinZ = minZ;
        double newMaxX = maxX;
        double newMaxY = maxY;
        double newMaxZ = maxZ;

        if (x < 0.0) {
            newMinX += x;
        } else {
            newMaxX += x;
        }

        if (y < 0.0) {
            newMinY += y;
        } else {
            newMaxY += y;
        }

        if (z < 0.0) {
            newMinZ += z;
        } else {
            newMaxZ += z;
        }

        return new AABB(newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ);
    }

    /** Expande la caja por los dos lados; el centro no se mueve. */
    public AABB grow(double x, double y, double z) {
        return new AABB(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
    }

    /**
     * Recorta el desplazamiento de la caja movil en X contra esta caja estatica.
     *
     * @param other caja movil (el jugador)
     * @param x     desplazamiento deseado en X
     * @return el desplazamiento permitido, sin llegar a solapar
     */
    public double clipXCollide(AABB other, double x) {
        // Si las cajas no se solapan en Y o en Z, no pueden chocar en X.
        if (other.maxY <= minY || other.minY >= maxY) {
            return x;
        }
        if (other.maxZ <= minZ || other.minZ >= maxZ) {
            return x;
        }

        // Movimiento positivo: la otra caja esta a la izquierda y limita el avance.
        if (x > 0.0 && other.maxX <= minX) {
            double limit = minX - other.maxX - EPSILON;
            if (limit < x) {
                x = limit;
            }
        }

        // Movimiento negativo: la otra caja esta a la derecha y limita el retroceso.
        if (x < 0.0 && other.minX >= maxX) {
            double limit = maxX - other.minX + EPSILON;
            if (limit > x) {
                x = limit;
            }
        }

        return x;
    }

    /**
     * Recorta el desplazamiento de la caja movil en Y contra esta caja estatica.
     *
     * @param other caja movil (el jugador)
     * @param y     desplazamiento deseado en Y
     * @return el desplazamiento permitido, sin llegar a solapar
     */
    public double clipYCollide(AABB other, double y) {
        if (other.maxX <= minX || other.minX >= maxX) {
            return y;
        }
        if (other.maxZ <= minZ || other.minZ >= maxZ) {
            return y;
        }

        if (y > 0.0 && other.maxY <= minY) {
            double limit = minY - other.maxY - EPSILON;
            if (limit < y) {
                y = limit;
            }
        }

        if (y < 0.0 && other.minY >= maxY) {
            double limit = maxY - other.minY + EPSILON;
            if (limit > y) {
                y = limit;
            }
        }

        return y;
    }

    /**
     * Recorta el desplazamiento de la caja movil en Z contra esta caja estatica.
     *
     * @param other caja movil (el jugador)
     * @param z     desplazamiento deseado en Z
     * @return el desplazamiento permitido, sin llegar a solapar
     */
    public double clipZCollide(AABB other, double z) {
        if (other.maxX <= minX || other.minX >= maxX) {
            return z;
        }
        if (other.maxY <= minY || other.minY >= maxY) {
            return z;
        }

        if (z > 0.0 && other.maxZ <= minZ) {
            double limit = minZ - other.maxZ - EPSILON;
            if (limit < z) {
                z = limit;
            }
        }

        if (z < 0.0 && other.minZ >= maxZ) {
            double limit = maxZ - other.minZ + EPSILON;
            if (limit > z) {
                z = limit;
            }
        }

        return z;
    }

    /** {@code true} si las dos cajas se solapan en los tres ejes. */
    public boolean intersects(AABB other) {
        if (other.maxX <= minX || other.minX >= maxX) {
            return false;
        }
        if (other.maxY <= minY || other.minY >= maxY) {
            return false;
        }
        return other.maxZ > minZ && other.minZ < maxZ;
    }

    /** Desplaza la caja en el sitio. */
    public void move(double x, double y, double z) {
        minX += x;
        minY += y;
        minZ += z;
        maxX += x;
        maxY += y;
        maxZ += z;
    }

    /** Caja nueva desplazada respecto a esta. */
    public AABB offset(double x, double y, double z) {
        return new AABB(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "AABB[%.3f..%.3f, %.3f..%.3f, %.3f..%.3f]",
                minX, maxX, minY, maxY, minZ, maxZ);
    }
}
