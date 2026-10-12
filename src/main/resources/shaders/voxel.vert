#version 330 core

// Atributos intercalados del formato de vertice del motor (9 floats):
//   posicion (vec3) | normal (vec3) | color (vec3)
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
// Posicion de la camara, para la direccion de vista que usan el contorno y la
// niebla.
uniform vec3 uCameraPos;

out vec3 vNormal;
out vec3 vColor;
out vec3 vWorldPos;
// Direccion desde el fragmento HACIA la camara. Se calcula por vertice porque es
// una funcion lineal de la posicion (la interpolacion es exacta) y asi el
// fragmento se ahorra la resta.
out vec3 vToCamera;

void main() {
    // La geometria ya se genera en coordenadas de mundo; uModel suele ser la
    // identidad, pero se mantiene por flexibilidad y claridad.
    vec4 world = uModel * vec4(aPosition, 1.0);
    vWorldPos = world.xyz;
    // La normal se transforma con la parte lineal de la matriz de modelo.
    // No se usa la inversa traspuesta porque solo hay traslacion/escala uniforme.
    vNormal = mat3(uModel) * aNormal;
    vColor = aColor;
    vToCamera = uCameraPos - world.xyz;
    gl_Position = uProjection * uView * world;
}
