#version 330 core

in vec3 vNormal;
in vec3 vColor;
in vec3 vWorldPos;
in vec3 vToCamera;

// Direccion (normalizada) desde la superficie HACIA la luz.
uniform vec3 uLightDirection;
// Luz ambiental base: evita que las caras opuestas queden completamente negras.
uniform float uAmbient;
// Intensidad de la componente direccional (Lambert).
uniform float uLightIntensity;
// Posicion de la camara, para la direccion de vista.
uniform vec3 uCameraPos;
// Color del cielo en el cenit y a ras de horizonte (los mismos que en sky.frag).
uniform vec3 uSkyZenith;
uniform vec3 uSkyHorizon;
// Densidad de la niebla atmosferica, en 1 / unidades de mundo.
uniform float uFogDensity;

out vec4 fragColor;

// El sol es calido y el cielo es frio. Repartir la luz entre los dos tintes
// segun la orientacion de la cara da volumen sin anadir ni una luz mas.
const vec3 SUN_TINT = vec3(1.06, 1.00, 0.90);
const vec3 SKY_TINT = vec3(0.80, 0.87, 1.00);
// Luminancia perceptual (Rec. 709), para el ajuste de saturacion final.
const vec3 LUMINANCE = vec3(0.2126, 0.7152, 0.0722);

// Copia exacta de skyColorFor() de sky.frag: es el color del fondo en la misma
// direccion de vista, de modo que la niebla no pueda desentonar con el cielo.
vec3 skyColorFor(vec3 direction) {
    vec3 dir = normalize(direction);
    float horizon = 1.0 - clamp(dir.y, 0.0, 1.0);
    return mix(uSkyZenith, uSkyHorizon, pow(horizon, 3.0));
}

void main() {
    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDirection);
    vec3 viewDir = normalize(vToCamera);

    // Lambert mas termino hemisferico: las caras que miran hacia arriba reciben
    // algo mas de luz, lo que ayuda a percibir el volumen de cavidades y tuneles.
    float diffuse = max(dot(n, l), 0.0);
    float sky = n.y * 0.5 + 0.5;
    float lambert = diffuse * 0.85 + sky * 0.15;

    vec3 light = SKY_TINT * uAmbient + SUN_TINT * (uLightIntensity * lambert);
    vec3 color = vColor * light;

    // Luz de contorno muy tenue: separa las siluetas del fondo sin dibujar
    // bordes. Solo aparece en caras vistas de canto, que es donde hace falta.
    float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0) * 0.08;
    color += SKY_TINT * rim;

    // Perspectiva aerea: la distancia lava el color hacia el del cielo en lugar de
    // oscurecerlo. Al ser exponencial y hacia el color del fondo en esa direccion,
    // el terreno lejano se disuelve en el horizonte.
    float dist = length(vToCamera);
    float fog = 1.0 - exp(-dist * uFogDensity);
    color = mix(color, skyColorFor(vToCamera), fog);

    // Un punto de saturacion: los materiales del catalogo ganan presencia sin
    // tocar sus colores.
    float grey = dot(color, LUMINANCE);
    color = mix(vec3(grey), color, 1.08);

    fragColor = vec4(color, 1.0);
}
