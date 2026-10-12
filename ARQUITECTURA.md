# Micro Voxel Engine — Documentación de Arquitectura y Construcción

Motor de voxels 3D en **Java 21 + LWJGL 3 + OpenGL 3.3 Core**, donde cada **bloque lógico** contiene una grilla tridimensional de **microvoxels** (16×16×16 por defecto). La forma real de un bloque no es un cubo fijo: viene determinada por la **distribución de ocupación** de sus microvoxels.

```
Mundo  →  bloques lógicos  →  microvoxels  →  geometría renderizable
```

---

## 1. Sistema de Construcción

### 1.1 Gradle

| Elemento | Detalle |
|---|---|
| **Build tool** | Gradle 9.6.1 (wrapper incluido, no requiere instalación) |
| **Archivo principal** | `build.gradle` |
| **Configuración de proyecto** | `settings.gradle` (nombre: `micro-voxel-engine`) |
| **Propiedades** | `gradle.properties` (encoding `windows-1252`, JVM flags) |

### 1.2 Comandos

```bash
./gradlew build          # Compilar todo (incluye tests)
./gradlew run            # Ejecutar la aplicación (abre ventana 3D)
./gradlew test           # Ejecutar las 84 pruebas unitarias
./gradlew clean          # Limpiar artefactos
./gradlew dependencies   # Ver árbol de dependencias
```

En Windows: `gradlew.bat` funciona igual.

### 1.3 Dependencias

| Dependencia | Versión | Uso |
|---|---|---|
| `org.lwjgl:lwjgl` | 3.3.6 | Motor gráfico principal (OpenGL, GLFW) |
| `org.lwjgl:lwjgl-glfw` | 3.3.6 | Creación de ventanas y manejo de input |
| `org.lwjgl:lwjgl-opengl` | 3.3.6 |_bindings_ de OpenGL 3.3 Core |
| `org.lwjgl:lwjgl-stb` | 3.3.6 | Fuente vectorial embebida (`stb_easy_font`) para debug overlay |
| `org.lwjgl:lwjgl-assimp` | 3.3.6 | (declarada, no usada en v0.1) |
| `org.joml:joml` | 1.10.8 | Matemáticas vectoriales/matriciales |
| `org.junit.jupiter:junit-jupiter` | 5.12.2 | Framework de pruebas unitarias |

**BOM**: `org.lwjgl:lwjgl-bom` gestiona versiones cruzadas.

**Nativos**: LWJGL descarga automáticamente los binarios nativos según el SO y arquitectura (Windows x86_64, macOS ARM64, Linux x86_64).

### 1.4 Configuración Java

- Compilación con `--release 21`
- Encoding: `windows-1252` (necesario para rutas con caracteres no ASCII en Windows)
- Source/target compatibility: 21

---

## 2. Estructura del Proyecto

```
src/main/java/com/microvoxel/
  Main.java                          # Punto de entrada
  engine/
    Engine.java                      # Bucle principal del motor
    Time.java                        # Gestión de tiempo y delta
  window/
    Window.java                      # Creación de ventana GLFW + OpenGL context
  input/
    Input.java                       # Manejo de teclado y ratón
  camera/
    Camera.java                      # Cámara en primera persona (yaw/pitch)
  world/
    World.java                       # Registro de bloques, consulta global
    Chunk.java                       # Agrupación de bloques para meshing
    LogicalBlock.java                # Posición + MicroVoxelGrid + dirty flag
    MicroVoxelGrid.java              # byte[] compacto: 0=aire, >0=material
    BlockPosition.java               # Utilidades de coordenadas (local↔global)
  phys/
    AABB.java                        # Caja AABB con clip de colisión por eje
    Player.java                      # Jugador: gravedad, salto, colisión, interpolación
  material/
    VoxelMaterial.java               # Identificador, nombre, color RGB, parámetro de luz
    MaterialRegistry.java            # Colección de materiales predefinidos
  scene/
    DemoScene.java                   # Escena de demostración (10 formas + terreno)
    ShapeBuilder.java                # Utilidades para construir formas en grillas
    TerrainBuilder.java              # Terreno fijo determinista (8×8 chunks)
  mesh/
    MicroVoxelMeshBuilder.java       # Genera mallas: examina vecinos, emite caras visibles
    MeshData.java                    # Estructura de datos de malla (vertices, normales, colores)
    LineData.java                    # Datos para líneas de depuración
  render/
    Renderer.java                    # Estado OpenGL, iluminación, draw calls
    Mesh.java                        # VAO + VBO + EBO por bloque lógico
    ShaderProgram.java               # Compilación/enlazado de shaders, cache de uniforms
    DebugRenderer.java               # Bordes de celdas (F2) y cajas de bloque (F3)
  debug/
    DebugOverlay.java                # Estadísticas FPS/bloques/voxels con stb_easy_font

src/main/resources/shaders/
  voxel.vert                         # Vertex shader: posición + normal + color → clip space
  voxel.frag                         # Fragment shader: Lambert + sky term + distance fog
  debug.vert                         # Vertex shader de depuración (solo posición)
  debug.frag                         # Fragment shader de depuración (color uniforme)

src/test/java/com/microvoxel/
  world/   MicroVoxelGridTest.java  BlockPositionTest.java  WorldTest.java  WorldCollisionTest.java
  phys/    AABBTest.java            PlayerTest.java
  scene/   ShapeBuilderTest.java    TerrainBuilderTest.java  TerrainMeshTest.java
  mesh/    MicroVoxelMeshBuilderTest.java
  camera/  CameraTest.java
```

---

## 3. Arquitectura del Motor

### 3.1 Jerarquía de Datos Volumétricos

```
World
  └── Bloques lógicos (registrados)
        └── Chunks (agrupación para meshing)
              └── LogicalBlock
                    └── MicroVoxelGrid (byte[])
                          └── Microvoxels individuales
```

**`MicroVoxelGrid`**: Almacenamiento compacto en un único `byte[]`. Valor `0` = aire, `>0` = identificador de material. Índice inequívoco: `index = x + sizeX * (y + sizeY * z)`. La resolución es un parámetro del constructor, nunca fijada a 16.

**`LogicalBlock`**: Contiene posición, grilla interna, límites en unidades de mundo, métodos de consulta/edición de microvoxels y una marca de reconstrucción (*dirty*).

**`World`**: Registro de bloques y autoridad para responder qué hay en una coordenada global de microvoxel. Unifica el culling de caras internas, fronteras entre bloques y bordes del mundo (exterior = aire).

### 3.2 Sistema de Coordenadas

1. **Bloque lógico** (`BlockPosition`): celda unitaria `[x, x+1)`
2. **Local de microvoxel**: `(lx, ly, lz)` en `[0, resolución)`
3. **Global de microvoxel**: `gx = bx * resoluciónX + lx`
4. **Mundo flotante**: unidades usadas por cámara y renderizador

Las conversiones usan `Math.floorDiv` / `Math.floorMod`, garantizando que la última celda de un bloque y la primera del bloque vecino compartan exactamente la misma frontera.

### 3.3 Generación de Geometría

**`MicroVoxelMeshBuilder`**: Para cada microvoxel ocupado se examinan sus seis vecinos en coordenadas globales. Se emite una cara **solo si el vecino está vacío**. Si está ocupado, la cara se omite aunque los materiales sean distintos.

Resultado:
- No se generan caras internas entre celdas ocupadas
- Bloques adyacentes no duplican caras de frontera
- Bloque vacío no produce geometría

Formato de vértice intercalado (9 floats/vertice): posición(3) + normal(3) + color(3). Los vértices se duplican por cara (24 por hexaedro aislado) para tener normales planas. Orden antihorario visto desde fuera.

### 3.4 Renderizado

| Componente | Responsabilidad |
|---|---|
| **`ShaderProgram`** | Compilación/enlazado con informes de error, caché de uniforms |
| **`Mesh`** | VAO + VBO + EBO. Una malla por bloque lógico; se reconstruye solo cuando el bloque está *dirty* |
| **`Renderer`** | Estado fijo de OpenGL, iluminación Lambert con término ambiental y hemisférico, atenuación por distancia, polygon offset para debug |
| **`DebugRenderer`** | Bordes de celda (F2) y cajas de bloque (F3) |
| **`DebugOverlay`** | Estadísticas con `stb_easy_font`, proyección ortográfica |

**Iluminación**: Dirección de luz uniforme, término Lambertiano (`max(dot(n, l), 0.0)`), término hemisférico suave (`n.y * 0.5 + 0.5`), atenuación por distancia (`clamp(1.0 - dist/80.0, 0.45, 1.0)`).

### 3.5 Colisión

Sistema extraído de **rd-161348** (versión temprana de Minecraft) y adaptado a la jerarquía de microvoxels:

**`AABB`**: Resuelve la geometría del barrido con aritmética en `double`. `expand()` convierte un desplazamiento en volumen barrido. `clipXCollide`/`clipYCollide`/`clipZCollide` recortan contra un cubo.

**`World.solidCubes`**: Devuelve los cubos sólidos que solapan una región. Un bloque macizo aporta **un solo cubo** de una unidad; un bloque con forma aporta **una caja de 1/16 por microvoxel ocupado**. Aire y materiales no sólidos (agua) no aportan nada.

**`Player`**: Adapta `Entity.move` y `Player.onTick`. Resuelve el desplazamiento eje por eje (primero Y, después X, luego Z). Mantiene las constantes del original (gravedad 0.08, salto 0.5, aceleración 0.1 en suelo / 0.02 en aire, rozamiento 0.91/0.98/0.7) e integra a **paso fijo de 20 Hz**.

**Interpolación**: La física avanza a 20 Hz pero se dibuja la interpolación lineal entre la posición anterior y la actual, en función del tiempo pendiente. Sin esto, la cámara se congelaría 40 de cada 60 fotogramas a 60 FPS.

### 3.6 Materiales

**`VoxelMaterial`**: Identificador entero, nombre descriptivo, color RGB sólido, parámetro de respuesta a la luz, bandera `solid`.

**`MaterialRegistry`**: Colección de materiales predefinidos: STONE, DIRT, GRASS, SAND, WATER, WOOD, RED, GREEN, YELLOW, GOLD, LIGHT_STONE, GLASS_CYAN. Los colores viven aquí, no en cada celda.

---

## 4. Escena de Demonstración

### 4.1 Terreno Fijo

- **8×8 chunks** (64 chunks de 16×16×16 bloques)
- **128×128 bloques** de ancho × **16 bloques** de alto
- Generado desde `y = 0` hacia arriba
- Relieve determinista: ruido de valor bilineal con dos octavas y semillas fijas
- Materiales por profundidad: césped (superficie), tierra (0-3 bloques bajo), piedra (>3), arena (orillas sumergidas), agua (valles bajo nivel 4)

### 4.2 Bloques de Demostración

Diez formas colocadas en una cuadrícula 3×3 sobre el terreno:

| # | Forma | Descripción |
|---|---|---|
| 1 | **Sólido** | Cubo completamente ocupado |
| 2 | **Perforado** | Tunel Z + tunel X cruzado |
| 3 | **Jaula** | Paredes finas con aberturas centrales |
| 4 | **Hueco** | Cavidad interior con acceso superior |
| 5 | **Escalonado** | Escalera de 8 niveles |
| 6 | **Arco** | Muro con abertura semicircular |
| 7 | **Irregular** | Base + protuberancias + voladizo + bloque flotante |
| 8 | **Multimaterial** | Patrón diagonal 3D con 3 materiales |
| 9 | **Componentes separados** | 5 regiones desconectadas |
| 10 | *(implícito)* | Bloque base de la escena |

### 4.3 Jugador

- Aparece de pie sobre la superficie sólida de la columna del spawn
- Posición deducida dinámicamente del mundo (altura del bloque sólido más alto)
- Ojos a 1.62 unidades sobre los pies
- Mira al centro de los bloques de demostración

---

## 5. Pruebas Unitarias

**84 pruebas** ejecutadas con `./gradlew test`:

| Categoría | Pruebas | Qué cubre |
|---|---|---|
| **Datos volumétricos** | MicroVoxelGridTest | Grilla vacía, ocupar/vaciar, recuperar material, límites, unicidad de índices, resolución no fija |
| **Coordenadas** | BlockPositionTest | Frontera entre bloques, coordenadas negativas, local↔global |
| **Mundo** | WorldTest | Registro/retiro de bloques, totales de ocupación, chunks |
| **Colisión (mundo)** | WorldCollisionTest | solidCubes: un cubo por bloque macizo, 1/16 por microvoxel, sin agua/aire |
| **AABB** | AABBTest | Volumen barrido, clip en 3 ejes, contacto exacto, movimiento, copia |
| **Jugador** | PlayerTest | Caída, muro, suelo a 1/16, salto, entrada relativa, rozamiento, paso fijo, interpolación, unstuck |
| **Formas** | ShapeBuilderTest | Cajas, túneles, cavidades, escaleras, arco, componentes separados (flood fill), materiales mixtos |
| **Terreno** | TerrainBuilderTest | Determinismo, 64 chunks, cubos macizos, materiales, relieve, meshing |
| **Geometría** | MicroVoxelMeshBuilderTest | 0 caras vacío, 6 caras microvoxel, adyacentes, bloque sólido, cavidad, túnel, frontera, normales |
| **Cámara** | CameraTest | Objetivo inicial, orientación, sentido ratón, pitch límite, visibilidad de bloques demo |

---

## 6. Shaders

### 6.1 Voxel Shader (`voxel.vert` / `voxel.frag`)

**Vertex**: Transforma posición por `uModel * uView * uProjection`, calcula normal con `mat3(uModel)`, propaga color y posición mundial.

**Fragment**: Iluminación Lambert (`max(dot(n, l), 0.0)`), término hemisférico (`n.y * 0.5 + 0.5`), atenuación por distancia (`clamp(1.0 - dist/80.0, 0.45, 1.0)`).

### 6.2 Debug Shader (`debug.vert` / `debug.frag`)

**Vertex**: Solo posición, con escala uniforme para texto.

**Fragment**: Color uniforme pasado como `uColor`.

---

## 7. Controles

| Tecla | Acción |
|---|---|
| `W` / `S` | Avanzar / retroceder |
| `A` / `D` | Desplazarse izquierda / derecha |
| `Espacio` | Saltar (solo en suelo) / Subir (modo vuelo) |
| `Shift` | Bajar (modo vuelo) |
| Ratón | Orientar cámara (primera persona) |
| `F1` | Mostrar/ocultar estadísticas |
| `F2` | Bordes de microvoxels visibles |
| `F3` | Cajas de bloques lógicos |
| `F4` | Modo sin colisión (vuelo libre) |
| `R` | Reaparecer sobre el terreno |
| `Tab` | Capturar/liberar cursor |
| `Escape` | Cerrar aplicación |

---

## 8. Decisiones y Limitaciones (v0.1)

- **Una malla por chunk**: terreno = 1 malla combinada por chunk (16 draw calls vs miles)
- **Culling en coordenadas globales**: fronteras resueltas a través del `World`
- **Libro de mallas de coste acotado**: solo bloques sueltos se revisan por fotograma (0.45 ms vs 3.18 ms)
- **Sin sincronización vertical** (`SWAP_INTERVAL = 0`): sin tope de FPS
- **Materiales opacos**: cristal cian se dibuja opaco, sin transparencias
- **Colisión paso fijo 20 Hz**: máximo 5 pasos por fotograma, movimiento interpolado
- **Sin suelo fuera del terreno**: caída indefinida fuera de los 128×128 bloques
- **Colisión "un solape no empuja"**: si el jugador aparece dentro de un bloque, puede salir caminando
- **Terreno fijo y determinista**: nunca cambia entre ejecuciones
- **No implementado**: mundos infinitos, romper/colocar bloques, inventario, guardado, multijugador, LOD, greedy meshing, sombras dinámicas

---

## 9. Créditos y Recursos

- LWJGL: https://www.lwjgl.org/
- OpenGL 3.3 Core: https://registry.khronos.org/OpenGL/
- JOML: https://joml-ci.github.io/JOML/
- Gradle: https://docs.gradle.org/current/userguide/building_java_projects.html
- JUnit 5: https://junit.org/junit5/
- Colisión: adaptada de **rd-161348** (código original de Minecraft en `example/rd-161348/`)
