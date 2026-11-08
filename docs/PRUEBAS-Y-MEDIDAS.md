# Pruebas y medidas

En Bedrock, probar era encender un servidor BDS, mandar órdenes por `cmd.txt`, leer el log y contar líneas
(`../Bastion/tools/lab/`). Aquí hay tres niveles, del más rápido al más real.

## 1. Pruebas de lógica (JUnit)

- La lógica pura (rutas, grupos, moral, postura, oleadas...) va en clases sin dependencias del juego.
- Pruebas JUnit 5 en `core/src/test/kotlin`, que corren con `./gradlew :core:test` en segundos (y dentro de
  `./gradlew build`). Entre ellas, una que lee y comprueba los mapas reales del mod.
- Al portar un `*Logic.ts` de Bedrock, se portan también sus pruebas (`../Bastion/tests/*.test.ts`): son la
  garantía de que la traducción hace lo mismo.

## 2. Pruebas dentro del juego (GameTest)

GameTest arranca un servidor sin pantalla, construye una estructura pequeña para cada prueba, la ejecuta y dice si
pasó o falló. Es el sustituto del laboratorio de Bedrock, pero repetible y automático.

- **Configuración:** con Fabric Loom, en `build.gradle.kts`, bloque `fabricApi { configureTests { ... } }`. Las clases
  de prueba se registran en el punto de entrada `fabric-gametest` de `fabric.mod.json`. Se puede pedir un informe en
  archivo (propiedad `fabric-api.gametest.report-file`). Los detalles, en la documentación de Fabric de la versión
  fijada: https://docs.fabricmc.net/develop/ (sección de pruebas automáticas).
- **Ojo con la versión:** la API cambió (en la 26.2 se usa `GameTestHelper` y `succeed()`; antes, `TestContext` y
  `complete()`). Copiar ejemplos solo de la versión fijada.
- **Estructuras:** se guardan en el proyecto (`.nbt` o el formato de la versión) y se crean en el juego con el bloque
  de estructuras o con la herramienta de pruebas.

### Pruebas previstas (y su equivalente en Bedrock)

| Prueba | Qué comprueba | Equivalente en Bedrock |
|---|---|---|
| `asaltoConEscalera` | El invasor coloca su escalera, sube y ataca arriba en ≤ 30 s | `scriptevent bastion:lab ladder` (allí el director ponía la escalera y subía a la unidad) |
| `asaltoPicando` | Pica la madera con grieta y pasa en ≤ 40 s | Batalla de referencia, contando bloques rotos |
| `escaleraDerribada` | Si rompen la escalera, el invasor cae | — |
| `cruceEnBarco` | 6 invasores cruzan un lago y desembarcan en ≤ 40 s | Balsas (`raftLogic`) |
| `grupoNoHaceBolita` | Dos grupos contra dos grupos se reparten el frente | `tools/lab/field.ts` (peleas en campo abierto) |
| `repliegueMoral` | Un grupo castigado se repliega al patio | Prueba de la moral en Stonehill |
| `rutaEnTerrenoIrregular` | Llega a un objetivo en una ladera con salientes | `tools/lab/climb.ts` |

**Regla:** una prueba de comportamiento se ejecuta varias veces (por ejemplo 5) y se exige que pase todas. Una sola
pasada engaña: en Bedrock, una prueba contaminada dio por bueno un arreglo que no funcionaba.

- **Cómo (26.3):** `tools/gametest-5.sh` lanza `runGameTest` 5 veces y falla si una sola pasada falla. El lanzador
  de Fabric no repite aunque la anotación diga `maxAttempts = 5, requiredSuccesses = 5` (se deja puesta: documenta
  la intención y vale para `/test` dentro del juego).
- Cada prueba superada escribe `[bastion] Prueba <nombre>: bien en N ticks (...)`. Los registros de las 5 pasadas
  quedan en `build/gametest-runs/`.
- **La misma escena se monta en el juego** con `/bastion escena <variante>` (código compartido en
  `scene/AssaultScene.kt`): así Limbers ve a velocidad normal lo que la GameTest mide acelerado.

## 3. Partidas en el servidor de desarrollo

- `./gradlew runServer`, con un mundo de mapa convertido en `run/`.
- Órdenes por la consola o por RCON (`enable-rcon=true`, `rcon.password`, `rcon.port` en `server.properties`), y
  lectura del log. Es el mismo flujo que con BDS, y Claude puede hacerlo desde una sesión.
- Para mirar, Limbers entra con su cliente (`runClient` o Prism con el `.jar`) al servidor local.
- **Registro:** cada director escribe líneas con un prefijo fijo (`[bastion] Grupos: …`, `[bastion] Escalera: …`),
  como en Bedrock. Así se puede contar sin abrir el juego.

## Medir el rendimiento

### Banco de la batalla grande

El equivalente a `../Bastion/tools/lab/cost.mjs`: un comando del mod (por ejemplo `/bastion bench battle 200 200`)
o una GameTest larga que haga lo mismo:

1. Monta la misma batalla.
2. Espera a que se estabilice.
3. Mide dos minutos de ticks/s y milisegundos por tick (media y peor).
4. Cuenta las unidades vivas al principio y al final.
5. Escribe una línea en un archivo de resultados con la versión y la fecha.

**Referencia de Bedrock:** unas 400 unidades en Old Fallen Castle iban a 7,5 ticks/s en la v0.1.24 y a 2-4 en la
v0.1.55.

### Spark

- `/spark profiler start --timeout 120`: graba dos minutos y da un enlace con el desglose por método, también de las
  clases del juego (rutas, colisiones, `Goal`).
- `/spark tps` y `/spark health`: ticks/s, milisegundos por tick, memoria.
- **Mirar el desglose antes de optimizar:** en Bedrock se perdió un día por no tenerlo.

### Dibujo (FPS)

- Medir en el PC de Limbers (Arch Linux) con Sodium y EntityCulling, mirando la batalla de 400.
- Si los FPS caen, opciones: modelos más simples a distancia, menos partículas o menos unidades visibles a la vez.

### Cómo apuntar una medida

Siempre con: **fecha, versión del mod, versión de Minecraft, mapa, número de unidades, cómo se midió y el
resultado**. Se apunta en [ROADMAP.md](ROADMAP.md) o en un documento de resultados si crece. Las cifras sin contexto
no sirven para comparar después.
