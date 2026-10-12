#version 330 core

// Fondo de cielo: un unico triangulo que cubre la pantalla y se genera desde el
// indice de vertice, asi que no necesita ningun buffer de geometria.
uniform mat4 uInverseViewProjection;
uniform vec3 uCameraPos;

out vec3 vDirection;

void main() {
    // Tres esquinas que cubren de sobra el rango de pantalla: (0,0), (2,0), (0,2)
    // en coordenadas de recorte.
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vec2 ndc = corner * 2.0 - 1.0;

    // Punto del plano lejano correspondiente a la esquina: al restarle la camara
    // queda la direccion de vista de cada pixel, que es lo que define el gradiente.
    vec4 far = uInverseViewProjection * vec4(ndc, 1.0, 1.0);
    vDirection = far.xyz / far.w - uCameraPos;

    // z = 1 lo deja al fondo del rango de profundidad.
    gl_Position = vec4(ndc, 1.0, 1.0);
}
