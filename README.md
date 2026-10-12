# Micro Voxel Engine v0.1

Motor de voxels 3D en **Java 21 + LWJGL 3 + OpenGL 3.3 Core**, donde cada
**bloque lógico** contiene una grilla tridimensional de **microvoxels**
(16×16×16 por defecto). La forma real de un bloque no es un cubo fijo: viene
determinada por la **distribución de ocupación** de sus microvoxels.

Jerarquía conceptual:

```
Mundo  →  bloques lógicos  →  microvoxels  →  geometría renderizable
```

Un bloque lógico ocupa **una unidad de mundo** por eje, con independencia de su
resolución interna. Con resolución 16, cada microvoxel mide 1/16 de unidad.

La escena de demostración construye cada forma escribiendo y vaciando celdas
(cavidades, túneles, arcos, escaleras, componentes separados, materiales
combinados) y demuestra que el motor no asume bloques sólidos.

Además, la misma escena incluye un **terreno de 8×8 chunks** (128×128 columnas
de bloques) que va **de `y = -128` a `y = 64`** y que **no existe como bloques**:
solo se guarda la altura de cada columna (64 KB) y la forma se deduce de ella.
Cada bloque del terreno es un **cubo macizo de 16×16×16 microvoxels de un único
material**, al estilo de un bloque de Minecraft, y su relieve es **determinista**
(ruido de valor con semilla constante): césped en la superficie, tierra debajo,
piedra hasta el fondo, arena en las orillas y agua en los lagos de los valles.
Por encima de `y = 64` el mundo sigue siendo aire disponible **hasta `y = 512`**,
la cota de construcción. Los bloques de demostración se colocan justo encima del
relieve local.

El jugador aparece **de pie sobre ese terreno** y se mueve con gravedad, salto y
**colisión por microvoxel** contra los bloques sólidos: el sistema de colisión se
extrajo de **rd-161348** (la versión temprana de Minecraft que está en `example/`)
y se adaptó a la jerarquía de microvoxels del motor (ver *Colisión*).

---

## Requisitos

- **JDK 21 o superior** (el proyecto compila con `--release 21`). Probado con
  Temurin 21 y 25.
- **GPU y controladores con OpenGL 3.3 Core** o superior.
- **Gradle no es necesario instalarlo**: se incluye el *wrapper*.
- Sistema operativo de desarrollo: Windows / macOS / Linux. Los binarios nativos
  de LWJGL se seleccionan automáticamente según el SO y la arquitectura (ver
  `build.gradle`).

### Tecnologías y versiones (fijas, sin rangos dinámicos)

| Componente | Versión |
|---|---|
| Java | 21 (`options.release = 21`) |
| Gradle (wrapper) | 9.6.1 |
| LWJGL | 3.3.6 (`lwjgl`, `lwjgl-glfw`, `lwjgl-opengl`, `lwjgl-stb`) |
| JOML | 1.10.8 |
| JUnit Jupiter | 5.12.2 |
| OpenGL | 3.3 Core Profile |
| GLSL | 3.30 Core |

LWJGL-STB se usa **solo** para el tipo de letra vectorial embebido de la
superposición de estadísticas (`stb_easy_font`), que **no requiere ningún archivo
de fuente ni textura**. No se usan texturas para los materiales, ni modelos 3D
externos.

---

## Cómo compilar, probar y ejecutar

Desde la raíz del proyecto:

```bash
# Compilar
./gradlew build

# Ejecutar la aplicación (abre la ventana 3D)
./gradlew run

# Ejecutar las pruebas unitarias
./gradlew test

# Limpiar artefactos
./gradlew clean
```

En Windows también funciona `gradlew.bat`. La primera ejecución descarga la
distribución de Gradle y las dependencias desde Maven Central.

### Nota sobre VS Code / IDEs

Dependencias como `org.lwjgl.stb` **si estan declaradas** en `build.gradle`
(`implementation 'org.lwjgl:lwjgl-stb'`, versionada por el BOM `lwjgl-bom`). Si
VS Code muestra `The import org.lwjgl.stb cannot be resolved`, no falta ninguna
dependencia de Gradle: es el *language server* de Java el que no tiene el
classpath importado. Para arreglarlo:

1. Instala las extensiones recomendadas (VS Code las sugiere al abrir el
   proyecto): **Extension Pack for Java** (`vscjava.vscode-java-pack`) y
   **Gradle for Java** (`vscjava.vscode-gradle`).
2. Abre la **raiz del proyecto** (la carpeta con `build.gradle`) como carpeta de
   trabajo.
3. Ejecuta **Java: Clean Java Language Server Workspace** desde la paleta de
   comandos y recarga la ventana. Tambien puedes pulsar el boton de refresco en
   la vista **Gradle** (icono de Gradle en la barra lateral).

El archivo `.vscode/settings.json` del repositorio ya activa la importacion
automatica del classpath de Gradle y su reimportacion al cambiar `build.gradle`.
Como comprobacion de que Gradle si resuelve la dependencia:

```bash
./gradlew dependencies --configuration compileClasspath | grep lwjgl-stb
# -> org.lwjgl:lwjgl-stb -> 3.3.6
```

### Nota de entorno (rutas de usuario con caracteres no ASCII)

Gradle pasa el classpath de los procesos *worker* (los que ejecutan los tests)
mediante un archivo `@argumentos`. La JVM lo **escribe** con el `file.encoding`
del daemon pero lo **lee** con la codificación nativa del sistema
(`sun.jnu.encoding`). Si la ruta del usuario contiene caracteres no ASCII
(p. ej. `C:\Users\SantiAgüero`) y ambos no coinciden, los tests fallan con
`Could not find or load main class worker...GradleWorkerMain`. Por eso
`gradle.properties` fija `-Dfile.encoding=windows-1252`, que coincide con la
codificación nativa en Windows. Si tu `sun.jnu.encoding` es distinto, ajústalo
(consulta `java -XshowSettings:properties -version`). En sistemas con el *home*
exclusivamente ASCII esa línea no es necesaria.

---

## Controles

| Tecla | Acción |
|---|---|
| `W` / `S` | Avanzar / retroceder |
| `A` / `D` | Desplazarse a la izquierda / derecha |
| `Espacio` | Saltar (solo con los pies en el suelo) |
| `Espacio` / `Shift` | Subir / bajar (solo en modo sin colisión) |
| Ratón | Orientar la cámara (primera persona) |
| Rueda del ratón | Subir / bajar la velocidad de desplazamiento (solo en modo sin colisión) |
| `F1` | Mostrar u ocultar las estadísticas |
| `F2` | Bordes de los microvoxels de las caras visibles (radio de 16 bloques) |
| `F3` | Límites (caja) de los bloques lógicos (radio de 16 bloques) |
| `F4` | Modo sin colisión (vuelo libre, para inspeccionar el terreno) |
| `F11` | Alternar ventana / pantalla completa |
| `R` | Reaparecer sobre el terreno |
| `Tab` | Capturar / liberar el cursor |
| `Escape` | Cerrar la aplicación |

El jugador arranca **de pie sobre el terreno** y se mueve con gravedad, salto y
colisión contra los bloques sólidos (ver *Colisión*). En modo sin colisión
(`F4`) el movimiento depende del tiempo transcurrido por fotograma y la cámara
atraviesa la geometría, como antes, para revisar túneles y cavidades desde
dentro.

En ese modo, la **rueda del ratón** sube o baja la velocidad de vuelo: cada
muesca hacia arriba multiplica por `1,25` y hacia abajo divide en la misma
proporción, de modo que el ajuste es fino a poca velocidad y amplio cuando la
cámara ya corre. La velocidad queda acotada entre `0,5` y `200` unidades por
segundo, y en las ruedas de alta resolución (incrementos fraccionarios) el
ajuste es continuo. El valor actual aparece en las estadísticas (`F1`) en la
línea *Player*, que en vuelo libre informa de la velocidad de la cámara. La
rueda no hace nada al caminar con colisión, y se ignora mientras el cursor está
liberado (`Tab`), igual que el giro de la cámara.

`F11` alterna **ventana y pantalla completa**. Al entrar se adopta el modo de
vídeo del monitor sobre el que está la ventana (no siempre el principal, para no
saltar de monitor) y se recuerdan su posición y su tamaño; al salir se restauran
exactamente. El contexto de OpenGL no se recrea, así que el cambio no cuesta
ninguna recarga de mallas ni de recursos. La aplicación arranca en ventana
(1280×720), a diferencia de rd-161348, que arrancaba directamente a pantalla
completa (`FULLSCREEN_MODE = true`).

---

## Arquitectura

### Datos volumétricos

- **`MicroVoxelGrid`**: almacenamiento compacto en un único `byte[]`. `0` = aire,
  `>0` = identificador de material. La ocupación se deduce del identificador, así
  que **no existe un objeto Java por microvoxel**. Índice inequívoco:
  `index = x + sizeX * (y + sizeY * z)`. La resolución es un parámetro del
  constructor, nunca está fijada a 16.
- **`LogicalBlock`**: posición, grilla interna, límites en unidades de mundo,
  consulta/edición de microvoxels y marca de reconstrucción (*dirty*).- **`World`**: registro de bloques y **autoridad** para responder qué hay en una
  coordenada global de microvoxel. Esta es la pieza que unifica el *culling* de
  caras internas, las fronteras entre bloques y los bordes del mundo: el exterior
  del mundo se considera aire. El mundo combina **dos orígenes**, en este orden de
  prioridad: los `LogicalBlock` registrados (con forma propia: demostración o
  editados) y una `BlockSource` opcional (terreno generado que no ocupa memoria).
  Todo pasa por `World.materialCode(x, y, z)`, así que dibujar, chocar y calcular
  caras no pueden discrepar.
- **`BlockSource` / `TerrainGenerator`**: el terreno como **modelo por columnas**.
  `TerrainGenerator` guarda una altura por columna y deduce el estrato de cada
  bloque por profundidad (`materialAt`), responde si una celda es roca enterrada
  (`isHiddenInterior`) y acota la banda vertical que hay que mallar (`faceRange`).
  Describe **2.668.776 bloques** ocupando **menos de 1 MB**.
- **`VoxelMaterial` / `MaterialRegistry`**: identificador, nombre, color RGB
  sólido y un pequeño parámetro de respuesta a la luz. Los colores viven aquí,
  no en cada celda.

### Coordenadas

1. **Bloque lógico** (`BlockPosition`): celda unitaria `[x, x+1)`.
2. **Local de microvoxel**: `(lx, ly, lz)` en `[0, resolución)`.
3. **Global de microvoxel**: `gx = bx * resolucionX + lx`. Identifica cualquier
   celda del mundo.
4. **Mundo flotante**: unidades usadas por la cámara y el renderizador.

Las conversiones usan `Math.floorDiv` / `Math.floorMod`, de modo que la última
celda de un bloque y la primera del bloque vecino comparten exactamente la misma
frontera, sin separación ni solapamiento, también en coordenadas negativas.

### Generación de geometría (`MicroVoxelMeshBuilder`)

Para cada microvoxel ocupado se examinan sus seis vecinos **en coordenadas
globales**. Se emite una cara **solo si el vecino está vacío**; si está ocupado,
la cara se omite aunque los materiales sean distintos. Así:

- no se generan caras internas entre celdas ocupadas,
- los bloques adyacentes no duplican caras de frontera,
- un bloque vacío no produce geometría.

Los vértices se **duplican por cara** (24 por hexaedro aislado) para tener
normales planas. El orden es antihorario visto desde fuera, coherente con el
culling de caras traseras (`GL_BACK` + `GL_CCW`). Formato intercalado
(9 floats/vertice): posición(3) + normal(3) + color(3).

Para el terreno generado el mesher no recorre el mundo entero: pregunta a la
fuente de bloques la **banda vertical** que puede producir caras en la huella del
chunk (`faceRange`) y, dentro de ella, se salta la roca enterrada con
`isHiddenInterior` (un puñado de comparaciones) en lugar de consultar los seis
vecinos de cada celda. Solo las celdas expuestas pagan el *culling* completo, y
los bloques macizos y uniformes se emiten como una cara de 1×1 en lugar de 256
micro-caras.

### Renderizado

- **`ShaderProgram`**: compilación/enlazado con informes de error y caché de
  uniformes.
- **`Mesh`**: VAO + VBO + EBO. Una malla por **unidad de dibujo** (un chunk del
  terreno o un bloque suelto); se reconstruye solo cuando su unidad está marcada
  como *dirty* (nunca se recrean buffers por fotograma).
- **`Renderer`**: estado fijo de OpenGL, los parámetros de la iluminación y el
  pase de fondo del cielo, además de un ligero *polygon offset* para que las
  líneas de depuración no sufran *z-fighting*. Antes de dibujar cada chunk prueba
  su **banda vertical** contra el volumen de la vista (*frustum culling*): una sola
  caja por chunk en lugar de comprobar geometría.
- **`DebugRenderer`**: bordes de celda (`F2`) y cajas de bloque (`F3`).
- **`DebugOverlay`**: estadísticas reales del motor dibujadas con
  `stb_easy_font` y una proyección ortográfica. Sin texturas ni fuentes externas.

Las estadísticas muestran FPS, ms por fotograma, los **bloques materializados**
frente a los **generados** (los que describe el terreno sin existir), el rango
vertical del mundo, la resolución, microvoxels **almacenados**, microvoxels
**ocupados** (distinguiéndolos), vértices (duplicados por cara), triángulos y el
estado del jugador (apoyado o no, modo y velocidad).

### Aspecto visual (shaders)

Todo el aspecto vive en los shaders; el renderizador solo aporta sus parámetros
(`Renderer.LIGHT_DIRECTION`, `AMBIENT`, `LIGHT_INTENSITY`, `SKY_ZENITH`,
`SKY_HORIZON`, `FOG_DENSITY`).

- **Luz cálida contra cielo frío**: la luz ambiental se tiñe de azul (cielo) y la
  direccional de ámbar (sol), y cada cara mezcla ambas según su orientación. Es la
  misma cuenta que antes —Lambert más término hemisférico— con el volumen ganado
  por el contraste de temperatura.
- **Luz de contorno**: un término `pow(1 - dot(normal, vista), 3)` muy tenue que
  solo aparece en caras vistas de canto, para que las siluetas se separen del
  fondo sin dibujar bordes.
- **Cielo con gradiente** (`sky.vert` / `sky.frag`): un único triángulo que cubre
  la pantalla, generado desde `gl_VertexID` (sin buffers) y dibujado sin
  profundidad. La dirección de vista se obtiene desproyectando la esquina del
  plano lejano con la inversa de vista × proyección, y el color sale de un
  gradiente cenit→horizonte. Sustituye el color de limpieza plano, que dejaba
  medio fotograma de un solo tono.
- **Perspectiva aérea**: la niebla es exponencial por distancia y mezcla hacia el
  color del cielo **en esa misma dirección**, con la misma fórmula que el fondo
  (`skyColorFor()` está duplicada en `sky.frag` y `voxel.frag` a propósito: GLSL no
  incluye ficheros). Así el terreno lejano se disuelve en el cielo que tiene
  detrás, sin línea de horizonte artificial ni oscurecimiento.
- **Saturación**: un último `1.08` de saturación perceptual (Rec. 709) para que los
  materiales del catálogo ganen presencia sin tocar sus colores.

Medido con una captura real de 960×540 al arrancar la escena (misma cámara y
misma escena, antes y después):

| Métrica | Antes | Después |
|---|---|---|
| Gradiente del cielo (horizonte − cenit) | +2,2 | **+21,4** |
| Luminancia mediana del fotograma (0-255) | 16 | **74** |
| Colorido medio (max−min por píxel) | 23,1 | **56,7** |
| Contraste del terreno cercano (desv. típica) | 37,8 | 27,7 |
| Píxeles recortados (negro puro o blanco puro) | 0,00 % | 0,00 % |

El contraste cercano baja algo porque la neblina atmosférica se come parte del
rango en la distancia media; el terreno inmediato sigue siendo el elemento de más
contraste de la imagen.

### Colisión (`phys`)

Sistema extraído de **rd-161348** (la versión temprana de Minecraft incluida en
`example/`) y adaptado al mundo de microvoxels:

- **`AABB`**: puerto de `com.mojang.rubydung.phys.AABB`. Resuelve la geometría del
  barrido con el mismo convenio que el original (`this` es la caja estática y el
  argumento la caja que se mueve) y la misma aritmética en `double`, para que el
  contacto y la detección de suelo no dependan del redondeo en `float`.
  `expand` convierte un desplazamiento en el volumen barrido y
  `clipXCollide` / `clipYCollide` / `clipZCollide` lo recortan contra un cubo.
- **`World.solidCubes`**: adaptación de `Level.getCubes`. Devuelve los cubos
  sólidos que solapan una región. Un bloque macizo y uniforme (todo el terreno)
  aporta **un solo cubo** de una unidad; un bloque con forma (túneles, cavidades,
  jaulas, escaleras) aporta **una caja de 1/16 por microvoxel ocupado**, así que
  la colisión respeta la subdivisión real. El aire y los materiales no sólidos
  (agua) no aportan nada.
- **`Player`**: adaptación de `Entity.move` y `Player.onTick`. Resuelve el
  desplazamiento eje por eje (primero Y, después X y por último Z), marca
  `onGround` cuando el descenso se recorta, anula la velocidad en el eje del
  choque y coloca los ojos a 1.62 unidades sobre los pies. Mantiene las
  constantes del original (gravedad 0.08, salto 0.5, aceleración 0.1 en el suelo
  y 0.02 en el aire, rozamiento 0.91/0.98/0.7) y las integra a **paso fijo de
  20 Hz**, como el tick original, para que el movimiento no cambie de sensación
  con los FPS.
- **Dibujo interpolado**: también del original (`prevX/prevY/prevZ` +
  `partialTicks`). La física avanza a 20 Hz, pero la posición que se dibuja es la
  interpolación lineal entre la posición anterior y la actual al último paso, en
  función del tiempo pendiente. Sin ese detalle la cámara solo cambiaría de sitio
  uno de cada tres fotogramas a 60 FPS: medido, **40 de 60 fotogramas no movían
  la posición** y luego daban un salto del triple de la media. Con la
  interpolación la media y el máximo por fotograma coinciden (relación 1.000).
- **`VoxelMaterial.solid`**: equivalente de `Tile.isSolid()`; el agua lo tiene
  desactivado, de modo que el jugador la atraviesa.

El jugador arranca de pie sobre la superficie sólida de la columna del spawn
(altura deducida del mundo, nunca fijada a mano) y mira a los bloques de
demostración. En modo sin colisión (`F4`) la cámara vuela libre y, al volver, el
jugador recupera su posición y se le saca del bloque si había volado dentro.

---

## Estructura del proyecto

```
src/main/java/com/microvoxel/
  Main.java
  engine/    Engine.java  Time.java
  window/    Window.java
  input/     Input.java
  camera/    Camera.java
  world/     World.java  Chunk.java  LogicalBlock.java  MicroVoxelGrid.java  BlockPosition.java  BlockSource.java
  phys/      AABB.java  Player.java
  material/  VoxelMaterial.java  MaterialRegistry.java
  scene/     DemoScene.java  ShapeBuilder.java  TerrainGenerator.java
  mesh/      MicroVoxelMeshBuilder.java  MeshData.java  LineData.java
  render/    Renderer.java  Mesh.java  ShaderProgram.java  DebugRenderer.java
  debug/     DebugOverlay.java
src/main/resources/shaders/
  voxel.vert  voxel.frag  sky.vert  sky.frag  debug.vert  debug.frag
src/test/java/com/microvoxel/
  world/  MicroVoxelGridTest.java  BlockPositionTest.java  WorldTest.java  WorldCollisionTest.java
  phys/   AABBTest.java  PlayerTest.java
  scene/  ShapeBuilderTest.java  TerrainGeneratorTest.java  TerrainMeshTest.java
  mesh/   MicroVoxelMeshBuilderTest.java
  camera/ CameraTest.java
  input/  InputTest.java
  render/ FrustumCullingTest.java
example/  rd-161348/  (codigo original de Minecraft del que se extrajo la colision)
```

---

## Pruebas

`./gradlew test` ejecuta 97 pruebas:

- **Datos volumétricos**: grilla vacía, ocupar/vaciar, recuperar material,
  límites explícitos, unicidad de índices, resolución no fija.
- **Colisión**: `AABB` (volumen barrido, recorte en los tres ejes con los dos
  sentidos de avance, contacto exacto que no cuenta como solape, movimiento y
  copia independiente); `World.solidCubes` (un cubo por bloque macizo, una caja
  de 1/16 por microvoxel ocupado, ni agua ni aire, solo los bloques que solapan
  la región); y `Player` (caída hasta apoyarse, muro que frena el avance, suelo
  a 1/16 respetando la subdivisión, salto solo con los pies en el suelo, entrada
  relativa al giro, frenado por rozamiento, paso fijo independiente del delta del
  fotograma, tope de pasos por fotograma y salida de un bloque con `unstuck`); y
  la interpolación del dibujo (punto medio con medio paso pendiente, movimiento
  continuo a 60 FPS con física a 20 Hz, y teletransporte que no se interpola).
  También se comprueba que el jugador aparece de pie sobre el terreno y que la
  colisión lo sostiene.
- **Ocupación**: el contador incremental de celdas ocupadas coincide con un
  recuento directo sobre el array crudo tras cada tipo de escritura y tras
  20.000 escrituras seudoaleatorias; `isEmpty()` se mantiene coherente; los
  totales del mundo (almacenados y ocupados) se mantienen al dar de alta y de
  baja bloques, y los bloques sueltos (los que no están en ningún chunk) aparecen
  y desaparecen de su lista al registrarlos en un chunk o retirarlos.
- **Coordenadas**: frontera entre bloques, negativos, local↔global.
- **Geometría**: bloque vacío (0 caras), microvoxel único (6 caras), adyacentes
  del mismo y de distinto material (sin cara interna), bloque sólido (solo
  superficie), cavidad, túnel, frontera entre dos bloques (sin duplicar),
  región vacía entre superficies, y coherencia de normales/orden con el culling.
- **Formas**: cajas, túneles, cavidades, escaleras, arco, componentes separados
  (verificados con *flood fill*), materiales mixtos.
- **Cámara**: el objetivo inicial proyecta en el centro de la vista, la
  orientación es coherente, el sentido horizontal del ratón es el correcto
  (mover a la derecha gira la vista a la derecha), el *pitch* se limita y los
  bloques de demostración quedan a la vista en el arranque. También se comprueba
  que la rueda sube y baja la velocidad de la cámara libre en la proporción
  esperada, que se mantiene dentro de los límites y que el **desplazamiento**
  real cambia con ella (no solo el número de velocidad).
- **Entrada**: la rueda acumula las muescas del fotograma (incluidas las
  fraccionarias de las ruedas de alta resolución) y tanto ella como el ratón se
  reinician al cerrar el fotograma.
- **Terreno**: es determinista (dos construcciones describen el mismo relieve) y
  ocupa una cuadrícula de 8×8 = 64 chunks **sin materializar un solo bloque**
  (`blockCount() == 0` y `totalStoredMicroVoxels() == 0`); el relieve vive entre
  `-128` y `64`, hay roca sólida hasta el fondo y aire (o agua) por encima;
  los estratos son césped/tierra/piedra y arena bajo el agua, y el agua no pasa
  del nivel del lago. El mundo **acepta bloques hasta `y = 512`** y rechaza todo
  lo que quede fuera de `[-128, 512]`; un bloque materializado tiene prioridad
  sobre el terreno generado, también cuando tiene forma propia.
- **Colisión contra roca que no existe**: el jugador cae y queda apoyado justo
  sobre la cara superior del bloque de superficie del terreno generado, y el
  descarte por frustum no puede ocultar ninguna columna cuya superficie esté en
  pantalla (se comprueba desde cinco cámaras sobre las 16.384 columnas).
- **Geometría del terreno**: el meshing por banda produce geometría compacta para
  las 64 columnas de chunks (caras fusionadas en lugar de micro-caras) y la
  rejilla de depuración `F2` se acota al radio de la cámara.

---

## Decisiones y limitaciones de esta iteración

- **El terreno no existe como bloques**: es la decisión de fondo de esta
  iteración. En lugar de un `LogicalBlock` (con su grilla de 16³, 4.096 celdas)
  por cada posición sólida, se guarda **una altura por columna** (16.384 `int` =
  **64 KB**) y los estratos se deducen de la profundidad. El terreno describe
  **2.668.776 bloques** y ocuparía **10.900 millones de celdas** (~10,2 GB solo de
  `byte[]`) si se materializara: medido, la escena completa se construye en
  **19 ms** y añade **0,14 MB** al *heap* (los 9 bloques de demostración). Todo lo
  que pregunta por un bloque (dibujo, colisión, estadísticas) pasa por
  `World.materialCode`, así que las dos representaciones no pueden discrepar.
- **Cotas del mundo**: `y = -128` (fondo del terreno) a `y = 512` (cota de
  construcción). El relieve se queda entre `y = 12` y `y = 64`, de modo que hay
  más de 400 capas de aire disponible para construir. Un bloque que se registre en
  el mundo manda sobre el terreno generado, también si tiene forma propia.
- **Una malla por banda de chunk**: el terreno se dibuja como **una malla
  combinada por chunk** (64 unidades de dibujo) y solo los bloques de demostración
  sueltos conservan su malla individual. Mallar las 192 capas del mundo entero
  sería tirar trabajo: el generador declara la **banda vertical** que puede
  producir caras (relieve, paredes y fondo), y el mesher recorre solo esa banda y
  salta con `isHiddenInterior` la roca enterrada. Medido, mallar todo el terreno
  cuesta **105 ms en frío y 68 ms en caliente** (~1 ms por chunk) y produce
  **124.670 caras** para 128×128 columnas. Los bloques macizos y uniformes se
  emiten como **una sola cara por lado expuesto** en lugar de 256 micro-caras, así
  que el tamaño de la geometría no depende de la resolución interna.
- **Culling de caras en coordenadas globales**: las fronteras entre bloques y
  entre chunks siguen resolviéndose a través del `World`, por lo que no se dibujan
  caras internas ni se duplican en las fronteras.
- **Descarte por frustum por banda**: cada chunk prueba su caja (la huella
  horizontal y la banda vertical) contra el volumen de la vista antes de emitir su
  malla. Con una banda de ~50 bloques, mirar hacia abajo dejaría la mitad del
  mundo oculta tras el terreno: el descarte deja de ser un adorno. Una prueba
  recorre 16.384 columnas desde cinco cámaras y comprueba que ninguna columna
  visible puede quedar descartada (un falso positivo del frustum se vería como
  terreno que desaparece).
- **Libro de mallas de coste acotado**: el `World` mantiene aparte la lista de
  bloques que no pertenecen a ningún chunk (los de demostración), así que el
  repaso de mallas sucias por fotograma recorre 64 chunks vacíos y 9 bloques sueltos
  en lugar de buscar los sueltos entre los 2,6 millones de bloques del terreno.
  Medido, ese repaso cuesta **≈12 µs por fotograma** (antes, con el terreno
  materializado, 0,45 ms). Eliminar ese coste exigiría que un bloque sucio
  avisara a su chunk.
- **Sin sincronización vertical** (`Window.SWAP_INTERVAL = 0`): el bucle no tiene
  tope artificial de FPS, a cambio de más gasto de CPU/GPU y posible desgarro de
  imagen. Como el trabajo por fotograma (libro de mallas, estadísticas, texto de
  depuración) se paga en cada fotograma, cuanto más alto sea el contador de FPS
  más caro sale por segundo. La simulación no depende de ello: avanza a 20 Hz y se
  dibuja interpolada. Poniendo el valor a `1` vuelve la sincronización.
- **Pantalla completa (`F11`)**: se usa `glfwSetWindowMonitor` con el **modo de
  vídeo actual** del monitor de la ventana, que es el único mecanismo de GLFW:
  no hay un modo *borderless windowed* alternativo ni selección de resolución ni
  de monitor. Por eso `F11` no cambia la resolución del escritorio, y la ventana
  vuelve a su sitio y tamaño exactos al desactivarlo. La prueba de este camino
  necesita un monitor real, así que la suite (`./gradlew test`) no lo cubre: las
  97 pruebas se ejecutan sin ventana ni contexto de OpenGL.
- **Materiales opacos**: el "cristal" cian se dibuja opaco, como permite el
  enunciado. No hay transparencias por ordenación.
- **El aspecto es puramente de los shaders**: no hay texturas, ni mapas de
  normales, ni oclusión ambiental por vértice (la geometría del mesher no lleva
  ese dato), ni sombras proyectadas, ni reflexiones. El suelo no recibe la sombra
  de los bloques de demostración y el agua no refracta: son las siguientes
  mejoras naturales, todas más costosas que un cambio de shader.
- El modo de depuración `F2` dibuja los **bordes de las caras visibles** (no un
  *wireframe* completo de triángulos), para mostrar la subdivisión sin diagonales
  confusas.
- **Coste de las estadísticas `F1`**: la ocupación se mantiene en un contador
  incremental por bloque (`MicroVoxelGrid`) y los totales del mundo se actualizan
  al registrar/retirar bloques. Sin ellos, dibujar las estadísticas recorrería las
  celdas del terreno en cada fotograma (10.900 millones si estuviera
  materializado). Ahora el camino completo de estadísticas cuesta **≈1,2 µs** y no
  depende del tamaño del terreno.
- **Colisión con paso fijo de 20 Hz**: la física se integra en pasos de 0,05 s
  (como el tick del original) y un fotograma simula como máximo 5 pasos; el
  sobrante se descarta para que un fotograma largo no dispare una espiral de
  simulación. El movimiento es independiente de los FPS y lo que se dibuja va
  interpolado, así que se ve fluido; lo que sí depende de los FPS es la precisión
  de la predicción, no la continuidad.
- **Materiales no sólidos**: solo el agua. No hay natación ni flotabilidad: al
  atravesarla la gravedad sigue actuando y el jugador se hunde hasta el fondo.
- **Sin suelo fuera del terreno**: el mundo es finito (128×128 columnas); si el
  jugador sale por un borde cae indefinidamente. `R` lo devuelve a la superficie.
- **Un solape no empuja**: como en el original, un recorte solo actúa si las cajas
  están separadas en el eje del movimiento. Si el jugador aparece dentro de un
  bloque puede salir caminando, y al volver del modo sin colisión `unstuck` lo
  sube hasta la primera posición libre (hasta 64 unidades).
- El terreno de demostración es **fijo y determinista** (nunca cambia entre
  ejecuciones); no hay generación procedural aleatoria ni mundos infinitos. El
  relieve incluye **una sola capa de superficie por columna**, así que no hay
  cuevas, voladizos ni galerías.
- Los bloques del terreno son **macizos y de un material**: la fuente responde por
  bloques completos y por eso no puede materializarse. Un bloque con forma propia
  (editado, perforado, con cavidades) se registra como `LogicalBlock` y tiene
  prioridad sobre el terreno generado.
- No se implementan (fuera del alcance de v0.1): mundos
  infinitos, romper/colocar bloques desde la interfaz (el mundo **sí** acepta
  bloques hasta `y = 512` por API, y las pruebas lo cubren), inventario, guardado,
  multijugador, LOD, *greedy meshing*, sombras dinámicas ni mallas asíncronas.
- La resolución y las dimensiones del mundo son independientes. Un `Chunk` no es
  un cubo fijo: es una **columna de 16×16 bloques** con una banda vertical propia
  (`bandMinBlockY`-`bandMaxBlockY`), la que necesita su malla. Puede no contener
  ningún bloque (el caso de todo el terreno generado) y aun así dibujar su banda;
  la representación interna de cada bloque (su `MicroVoxelGrid` de 16³) no cambia.

---

## Créditos y recursos consultados

- LWJGL: https://www.lwjgl.org/ · https://www.lwjgl.org/guide
- OpenGL 3.3 Core: https://registry.khronos.org/OpenGL/
- JOML: https://joml-ci.github.io/JOML/
- Gradle: https://docs.gradle.org/current/userguide/building_java_projects.html
- JUnit 5: https://junit.org/junit5/
