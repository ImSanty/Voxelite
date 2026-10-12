#version 330 core

in vec3 vNormal;
in vec3 vColor;
in vec3 vWorldPos;

// Direccion (normalizada) desde la superficie HACIA la luz.
uniform vec3 uLightDirection;
// Luz ambiental base: evita que las caras opuestas queden completamente negras.
uniform float uAmbient;
// Intensidad de la componente direccional (Lambert).
uniform float uLightIntensity;
// Posicion de la camara, solo para la atenuacion por distancia.
uniform vec3 uCameraPos;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDirection);

    float diffuse = max(dot(n, l), 0.0);
    // Termino hemisferico suave: las caras que miran hacia arriba reciben algo
    // mas de luz, lo que ayuda a percibir el volumen de cavidades y tuneles.
    float sky = n.y * 0.5 + 0.5;

    float light = uAmbient + uLightIntensity * (diffuse * 0.85 + sky * 0.15);

    vec3 color = vColor * light;

    // Atenuacion por distancia muy leve para dar sensacion de profundidad.
    float dist = length(vWorldPos - uCameraPos);
    float fog = clamp(1.0 - dist / 80.0, 0.45, 1.0);

    fragColor = vec4(color * fog, 1.0);
}
