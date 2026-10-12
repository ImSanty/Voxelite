#version 330 core

in vec3 vDirection;

// Color en el cenit (arriba) y a ras del horizonte.
uniform vec3 uSkyZenith;
uniform vec3 uSkyHorizon;

out vec4 fragColor;

// Gradiente vertical del cielo. La misma formula esta en voxel.frag, donde se usa
// como color de la niebla: por eso el terreno lejano se funde exactamente con el
// cielo que tiene detras, sin linea de horizonte artificial. GLSL no tiene
// inclusion de ficheros, asi que las dos copias deben mantenerse iguales.
vec3 skyColorFor(vec3 direction) {
    vec3 dir = normalize(direction);
    // 0 en el cenit, 1 a ras de horizonte. Al elevarlo al cubo, el color del
    // cenit ocupa casi todo el hemisferio y el cambio se concentra cerca del
    // horizonte, que es donde la atmosfera se ve mas densa.
    float horizon = 1.0 - clamp(dir.y, 0.0, 1.0);
    return mix(uSkyZenith, uSkyHorizon, pow(horizon, 3.0));
}

void main() {
    fragColor = vec4(skyColorFor(vDirection), 1.0);
}
