#version 330 core

// Formato de vertice de depuracion: solo posicion (vec3).
layout(location = 0) in vec3 aPosition;

uniform mat4 uProjection;
uniform mat4 uView;
// Escala opcional de la geometria (util para el texto de la superposicion).
uniform float uScale;

void main() {
    gl_Position = uProjection * uView * vec4(aPosition * uScale, 1.0);
}
