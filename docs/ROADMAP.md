# Roadmap de Bastion (Java)

**Empieza aquí en cada sesión.** Dice dónde nos quedamos, qué sigue y en qué fase va la migración. Al terminar una
sesión, actualiza «Dónde nos quedamos».

## Dónde nos quedamos

*Última actualización: 2026-11-07 (segunda sesión). Prueba inicial cerrada: escenas A y C hechas, la batalla de
400 corre a 20 ticks/s también en el castillo de Stonehill convertido de Bedrock, y Limbers la ve fluida en su PC.
Limbers decidió seguir en Java y aprobó las fases nuevas y la estructura. **Lo siguiente: la Fase 2, estructura y
datos** (ver «Fases»). La escena B (barco) pasa a la Fase 6, la del agua.*

- **Fases y estructura decididas por Limbers (2026-11-07).**
  - Las fases se rehacen para que **cada una termine con Stonehill jugable y mejor que antes**. Ver «Fases».
  - Estructura: la lógica pura va a un módulo de Gradle propio, `core`, que no ve Minecraft; los mapas, a archivos
    JSON; las unidades, a una ficha en Kotlin. Ver [ARQUITECTURA.md](ARQUITECTURA.md), «Estructura del código».
  - **De Bedrock se porta solo lo que haga falta, cuando haga falta.** Mucha de su lógica tapaba carencias de
    Bedrock. Antes de portar un módulo se prueba sin él en Java; si el problema no aparece, no se porta. Comprobado
    qué se puede reusar: ver [PORTAR-DESDE-BEDROCK.md](PORTAR-DESDE-BEDROCK.md), «Qué se puede reusar
    (comprobado)».

- **Escena C de la prueba inicial, hecha (2026-11-07).** Commits `eb2872d` (llano) y `fdddc34` (terreno real).
  - **Lógica pura con pruebas:** `logic/flow/FlowField.kt` (campo de flujo: Dijkstra al revés desde la meta, andar,
    subir un escalón, caer hasta 3; 7 pruebas) y `logic/flow/SpatialGrid.kt` (rejilla de 8 bloques para el enemigo
    más cercano; 5 pruebas).
  - **`battle/Battle.kt`:** la batalla y su director. Cada tick rellena las rejillas y asigna enemigo a una décima
    parte de las unidades (cada una revisa su objetivo cada 10 ticks, a 16 bloques). Las unidades de una batalla no
    usan el `NearestAttackableTargetGoal` del juego ni planean rutas propias (`AssaultGoal` se desactiva): los
    invasores siguen el campo (`FlowFieldGoal`) y pelean con `MeleeAttackGoal`; los defensores guardan su puesto
    (radio 14).
  - **`/bastion escena batalla [por bando]`** monta un llano de 111x52 con un muro y una puerta de 6, 200 invasores y
    200 defensores. **`/bastion medir [segundos]`** escribe ticks/s y ms por tick en `run/bastion-bench.txt`.
    Spark va en el entorno de desarrollo (`localRuntime`).
  - **Medido** (2026-11-07, mod 0.0.1, MC 26.3, servidor de desarrollo `runServer` en el PC de Limbers (8 hilos,
    35 GB), JDK 27, sin Lithium; Limbers conectado mirando la batalla; mundo plano; `/spark tps` en plena pelea, con
    las 400 unidades vivas):

    | Medida | Resultado | Bedrock (misma batalla de ~400) |
    |---|---|---|
    | Ticks/s | **20,0** estables | 7,5 (v0.1.24); 2-4 (v0.1.55) |
    | ms por tick (mín/mediana/p95/máx, 10 s) | 8,5 / 11,1 / 16,5 / 30,3 y 8,3 / 12,1 / 17,2 / 18,6 | — |
    | Campo de flujo (5.680 casillas) | 9-71 ms, una vez por batalla | — |
    | Pico al montar la escena (400 entidades y ~45.000 bloques) | 87 ms, una vez | — |

    El criterio era 20 ticks/s con el peor tick por debajo de 50 ms: **cumplido**. La batalla dura unos 45 s (mueren
    los 200 invasores; quedan 68-88 defensores), así que la medida de 2 minutos de `/bastion medir` incluye mucho rato
    de calma: lo representativo es la de Spark en plena pelea. La primera línea de `bastion-bench.txt` tiene la
    media mal (el primer tick se medía desde cero, ya corregido); su p95, 13,5 ms, sí vale.
  - **FPS en el PC de Limbers** (2026-11-07, cliente de desarrollo sin Sodium ni EntityCulling, mirando la batalla
    desde encima de la puerta): 150-200 en reposo, **80-90 en plena pelea**. Jugable; con Sodium debería subir.
  - **Sobre terreno real** (2026-11-07, `/bastion escena batalla-terreno`, mundo normal con semilla `bastion`,
    servidor en `execute positioned 39 67 51 rotated -90 0`, colinas y árboles, sin aplanar; Limbers conectado mirando;
    medido con `/bastion medir 60` y Spark durante la pelea entera):

    | Configuración | Ticks/s | ms por tick (media / p95 / peor) | Movimiento de criaturas | Chunks | Nuestro código |
    |---|---|---|---|---|---|
    | Llano, sin Lithium (Spark en plena pelea) | 20,0 | ~12 / 17 / 30 | — | — | — |
    | Terreno real, sin Lithium ([informe](https://spark.lucko.me/GmezQ3APPn)) | 20,0 | 23,3 / 37,6 / 102 | 25 % del tick | 28 % | 2,4 % |
    | Terreno real, con Lithium ([informe](https://spark.lucko.me/e3RQfad7dd)) | 20,0 | **14,4 / 24,2 / 71** | 13 % | 35 % | 3,1 % |
    | Castillo de Stonehill, con Lithium ([informe](https://spark.lucko.me/qA4X1wCmwO)) | 20,0 | **14,3 / 24,0 / 180** | — | — | ~3 % (0,9 % del total con espera) |

    - **Stonehill** (2026-11-07): `.mcworld` de Bedrock convertido con Chunker 1.21.0 (`--outputFormat JAVA_26_3`,
      4 s) a `run/stonehill` (fuera del repo). Las coordenadas coinciden con las de Bedrock: puerta este en x -283,
      z 206, suelo a 124. Batalla con `execute positioned -199 125 206 rotated 90 0 run bastion escena
      batalla-terreno`: los defensores quedan dentro del castillo y los invasores en el valle del este. Campo de flujo
      de 6756 casillas (caja 111x81x52) en 82 ms. En 60 s solo caen 12 unidades: el asalto avanza despacio.
      Un pico suelto de 180 ms (origen por mirar); la media y el p95, iguales que en terreno normal.

    - Con Lithium el tick baja un 40 %. Ojo: en la primera medida el mundo era nuevo y aún generaba terreno; en la de
      Lithium ya estaba generado. Parte de la mejora puede venir de ahí.
    - **El peor tick sigue por encima de 50 ms** (71 ms): picos sueltos, probablemente de chunks (guardado o carga).
      El p95 está muy por debajo. A vigilar en el mapa del castillo.
    - Lo que pesa es del juego (mover y chocar 400 criaturas con el terreno, los chunks), no nuestro (2-3 %).
    - La meta del campo de flujo era una fila; en terreno real cayó en la orilla de un lago y quedó aislada (32
      casillas). Ahora es toda la franja de los defensores. El agua sigue sin cruzarse (llegará con la escena B).
    - Lithium (servidor) y Sodium (cliente, beta para 26.3) van en el entorno de desarrollo (`localRuntime`).
  - **FPS de Limbers con Sodium** (2026-11-07, batalla de 400 en Stonehill, mirando en vivo): estables entre 150 y
    200 o más en plena pelea, sin tirones por debajo de 60. Sin Sodium, en el llano, eran 80-90.
  - **Queda para las fases siguientes:** el origen del pico de 180 ms en Stonehill; la batalla propia del castillo
    (meta en la puerta, defensores en las murallas; Fase 3); y una batalla más larga o con más unidades para ver
    dónde está el techo (Fase 7).
  - GameTest nueva `campoDeFlujo`: 10 invasores cruzan la puerta siguiendo el campo en ~4,7 s, 5 de 5.

- **Escena A de la prueba inicial, hecha (2026-11-07).** Rama `feature/prueba-inicial`.
  - **Buscador de rutas:** `routeLogic.ts` de Bedrock traducido a Kotlin (`logic/route/RoutePlanner.kt`), con sus
    25 pruebas JUnit (de `routeLogic.test.ts` y `ladderRoute.test.ts`), que pasan todas. Lee el mundo con
    `LevelCells`, que usa las propiedades reales del bloque (forma de colisión, dureza, si tiene inventario) en vez
    de los nombres. El coste de picar es el tiempo real de picar con su herramienta (`Digging`).
  - **Unidades:** `Invader` (hacha, escaleras que se gastan) y `Defender` (espada, con puesto fijo), con la ficha
    de Bedrock y las skins de soldado de Bedrock sobre el modelo de jugador del juego.
  - **`AssaultGoal`:** el invasor ejecuta su ruta él mismo. Camina con la navegación del juego. Pica con golpes,
    grieta por fases, partículas y sonido, y tarda lo mismo que un jugador con esa herramienta (decisión de
    Limbers: tablón con hacha de hierro en 0,5 s; con espada, más lento; piedra con pico, rápido). Pone la escalera tramo a tramo con la escalera en la mano, sube
    empujando contra la pared (la mecánica del juego) y golpea desde ella si tiene a su enemigo a tiro. Si le
    rompen la escalera, cae y vuelve a planear desde el suelo. Ningún teletransporte ni bloque puesto por el juego.
  - **Escena montable en el juego:** `/bastion escena escalera` y `/bastion escena picar` montan delante del
    jugador un patio cerrado con muros de 8 de alto (para que no se pueda rodear), la franja de tablones, el
    invasor y, en la de escalera, el defensor arriba. Limbers la vio en su PC el 2026-11-07 (12 escenas montadas).
  - **Medido** (2026-11-07, mod 0.0.1, Minecraft 26.3, GameTest con `tools/gametest-5.sh`, 5 pasadas):

    | Prueba | Criterio | Resultado (5 de 5) |
    |---|---|---|
    | `asaltoConEscalera` | Arriba en ≤ 30 s y el defensor herido | Arriba en 13,3-13,9 s, 8 tramos, defensor con 38 de 60 |
    | `asaltoPicando` | Al otro lado en ≤ 40 s | 7,4 s, 4 tablones picados (con tiempos del juego; 11,4 s con ×3) |
    | `escaleraDerribada` | Cae, sin flotar ni teletransporte | En el suelo 13 ticks después de romperla |
    | Sin trampas | Ningún salto de más de 1,5 bloques por tick | Cumplido en todas |

  - **Lo que costó**, para la próxima:
    - El juego deja a una criatura con objetivo y mucha vida tirarse desde muy alto (con 60 de vida, 43 bloques):
      el defensor saltaba del muro. `SiegeUnit.getMaxFallDistance` = 3.
    - Una escalera colocada con el invasor pegado a la pared le queda dentro del cuerpo y el tramo de encima le hace
      de techo. Antes de colocar, se centra en la casilla.
    - Trepando, si empuja hacia un punto cercano, en el aire oscila de lado a lado hasta salirse: empuja hacia un
      punto lejano dentro del muro (`PUSH_AHEAD`).
    - El defensor, sin puesto, perseguía al invasor por lo alto de los muros: ahora defiende su puesto
      (`GuardPostGoals`, idea de `postLogic`).
    - **El lanzador de GameTest de Fabric no repite las pruebas** aunque la anotación diga `maxAttempts = 5,
      requiredSuccesses = 5`. Para el «5 de 5»: `tools/gametest-5.sh`.
  - **Para verlas:** `./gradlew runClient`, mundo creativo con trucos y `/bastion escena ...`. También
    `./gradlew runClientPruebas` (el juego con las GameTest cargadas, para `/test run ...`), **sin probar todavía**.
  - **Visto por Limbers (2026-11-07), sin programarlo:** en la escena de escalera, el invasor golpeó al defensor
    arriba y los dos cayeron al patio del otro lado. El invasor volvió a planear desde allí, **picó la pared** para
    llegar hasta el defensor y lo mató. Es la prueba de que planear y actuar por sí mismo da comportamientos que en
    Bedrock habrían pedido parches.
  - **Decidido por Limbers (2026-11-07):** picar con los tiempos del juego, como un jugador; y que el defensor
    pueda tirar al invasor de la escalera se queda.
  - Las skins de Bedrock no tienen licencia de reutilización: valen para desarrollar, no para publicar.

- **Proyecto creado (2026-11-07)** a partir de la plantilla oficial de Fabric para 26.3, en Kotlin (Gradle con
  `build.gradle.kts`). Paquete `dev.limbers.bastion`, id `bastion`.
  - Versión de Minecraft: **26.3**, la que Limbers tiene instalada. Todas las piezas del stack tienen versión para
    ella (Sodium en beta). Ver «Versiones fijadas».
  - `./gradlew build` pasa (2 min 44 s la primera vez, con la descarga del juego). Dentro del `build` se ejecuta
    `runGameTest`, y la GameTest vacía `elMundoExiste` pasa. En el log salen `[bastion] Cargado`, GeckoLib 5.5.7 y
    Kotlin cargados.
  - Gradle y el juego de desarrollo corren con el **JDK 27** del sistema y compilan para Java 25. Ha funcionado sin
    instalar el JDK 25. Si algo falla por eso, `sudo pacman -S jdk25-openjdk`.
  - `runServer` arranca (`Done` en 2,6 s) y `runClient` llega al menú con el mod cargado. Limbers aceptó el EULA
    (`run/eula.txt`, fuera del repositorio); las GameTest lo aceptan en `build.gradle.kts` (`eula = true`).
  - Avisos sin importancia en el cliente de desarrollo: falta `flite` (narrador) y no hay sesión de Realms.
  - La primera vez, Loom puede fallar al descargar un recurso del juego («Unexpected file length»): es la red;
    basta con repetir.
  - Sin Mixins ni `mixins.json`: se añadirán cuando haga falta el primero.
- **Migración decidida por Limbers (2026-11-07).**
  - Jugará en Arch Linux y quiere realismo: invasores que pican muros con animación, ponen ellos mismos las
    escaleras, usan barcos de verdad y encuentran rutas sin rescates.
  - Más adelante: unidades aéreas y acuáticas, arietes y dragones.
  - En Bedrock la navegación es una caja negra y obligaba a parches. Motivos completos en
    [POR-QUE-JAVA.md](POR-QUE-JAVA.md).
- **Stack elegido:** Fabric + Kotlin + GeckoLib + GameTest + Spark, más Lithium, Sodium y EntityCulling para jugar.
  Ver [STACK.md](STACK.md).
- **Proyecto de Bedrock:** `../Bastion` (en el PC de Arch, `../../addons/Bastion`), versión 0.1.55, rama `feature/old-fallen-castle`. Queda como referencia.
  - Tiene **pendiente el commit de su Fase 3** (v0.1.44-0.1.55) cuando Limbers dé el OK.
  - En su v0.1.55 `reselect_targets` vuelve a estar puesto, así que la batalla grande va lenta (2-4 ticks/s), pero
    todo lo demás funciona como antes.
- **Siguiente paso:** la Fase 2, estructura y datos (ver «Fases»).

## Versiones fijadas

| Pieza | Versión | Fecha |
|---|---|---|
| Minecraft | 26.3 | 2026-11-07 |
| Fabric Loader / API / Loom | 0.20.1 / 0.165.2+26.3 / 1.19.4 | 2026-11-07 |
| Kotlin / `fabric-language-kotlin` | 2.4.21 / 1.14.2+kotlin.2.4.21 | 2026-11-07 |
| GeckoLib | 5.5.7 (`com.geckolib:geckolib-fabric-26.3`) | 2026-11-07 |
| Gradle | 9.8.2 (wrapper de la plantilla) | 2026-11-07 |
| JUnit | 5.13.4 | 2026-11-07 |
| JDK | Compila para 25; se ejecuta con el 27 del sistema | 2026-11-07 |

Para jugar en Prism con 26.3 (Modrinth, 2026-11-07): Sodium `mc26.3-0.9.3-beta.3` (beta), Lithium
`mc26.3-0.26.3`, Spark 1.10.187, EntityCulling 1.11.3. La fuente de verdad es `gradle.properties`.

## Fases

Decididas por Limbers el 2026-11-07. Las de Bedrock siguieron el orden en que salían los problemas; aquí la lógica
ya existe, así que se avanza por **rebanadas jugables**: cada fase termina con Stonehill jugable y mejor que antes,
y Limbers lo prueba en su PC antes de cerrarla.

En todas las fases:

- Cada comportamiento nuevo lleva su GameTest, y cada cambio que pueda pesar se mide antes y después.
- **Antes de portar un módulo de Bedrock, se prueba sin él.** Se porta solo si el problema aparece en Java, y solo
  la parte que haga falta. La columna «De Bedrock» dice qué se mira, no qué se copia.
- Tras cada cambio, el juego de Limbers se cierra y se vuelve a abrir con la versión nueva para que lo vea.

| Fase | Qué | De Bedrock |
|---|---|---|
| 0 ✅ | Planificación | — |
| 1 ✅ | Prueba inicial: escenas A y C | `routeLogic`, `ladderLogic` |
| **2** | **Estructura y datos** | `catalog`, `maps/*` |
| 3 | Primera partida jugable en Stonehill | `siegeState`, `wavePlan` |
| 4 | Muros y asedio | `wallLogic` |
| 5 | Combate orgánico | `squad`, `morale`, `stance`, `post`, `rescue`, `intruder` |
| 6 | Agua | — (los barcos de Bedrock no sirven) |
| 7 | Guerra progresiva y gran escala | `hunt` (parte) |
| 8 | Unidades nuevas | — |

### Fase 0: planificación (hecha, 2026-11-07)

- [x] Decidir la migración y el stack.
- [x] Guía completa para retomar: este documento y los demás de `docs/`.

### Fase 1: prueba inicial (hecha, 2026-11-07)

Criterios y escenas en [PRUEBA-INICIAL.md](PRUEBA-INICIAL.md).

- [x] Versiones elegidas y fijadas (26.3).
- [x] Proyecto creado; `build`, `runClient`, `runServer` y GameTest funcionan.
- [x] Escena A: escalera colocada por el invasor, picar con grieta, escalera derribada. GameTest 5 de 5. Limbers la
      vio y le convence.
- [x] Escena C: 400 unidades con campo de flujo y rejilla, a 20 ticks/s en llano, terreno real y Stonehill.
      150-200 FPS en el PC de Limbers con Sodium.
- [x] Limbers lo ve en su PC y decide seguir en Java.
- Escena B (barco): pasa a la Fase 6.

### Fase 2: estructura y datos

El esqueleto sobre el que crece todo. Casi sin juego nuevo, pero al cerrarla la escena A, la batalla de 400 y sus
GameTest funcionan igual que antes, sobre la estructura nueva.

- [ ] Separar la lógica pura en el módulo `core` (Kotlin sin Minecraft, pruebas JUnit) y el mod en `mod`. Mover
      rutas, campo de flujo y rejilla a `core`. Ver [ARQUITECTURA.md](ARQUITECTURA.md), «Estructura del código».
- [ ] Reordenar los paquetes del mod: `unit`, `director`, `game`, `world`, `map`, `debug`.
- [ ] Ficha de unidad en Kotlin con las 7 unidades de Bedrock (vida, golpe, velocidad, arma, skins, bando y rol),
      con la variación de ±10 %. Al principio solo nacen las que ya tienen comportamiento (soldados).
- [ ] Mapas en JSON (`data/bastion/maps/*.json`): formato, lector y comprobaciones (lo que hacía
      `maps/validate.ts`). Stonehill primero, con sus datos de Bedrock.
- [ ] Convertir Old Fallen Castle, The Combat Castle e Ice Castle con Chunker y comprobar sus coordenadas.
- [ ] Copia limpia de cada mundo convertido, para reponerlo entre pruebas.
- [ ] Herramienta para ver rutas y campos de flujo en el juego (depuración).

### Fase 3: primera partida jugable en Stonehill

- [ ] Partida: cuenta atrás, oleadas, descanso, vidas compartidas, victoria y derrota (`siegeState`,
      `wavePlan`).
- [ ] Oleadas desde los puntos de aparición del mapa, con las cifras de Limbers. Refuerzos aliados.
- [ ] Batalla propia del castillo: meta en la puerta, defensores en las murallas y en la puerta.
- [ ] Invasores que siguen el campo de flujo hasta la puerta y pican o ponen escaleras cuando hace falta.
- [ ] Arqueros de los dos bandos: arco, línea de visión y puestos en alto.
- [ ] El pico de 180 ms visto en Stonehill: de dónde sale.

### Fase 4: muros y asedio

- [ ] Muro de la puerta, brecha y zona donde se puede picar (`wallLogic`).
- [ ] Magos invasores contra el muro; pirómano aliado.
- [ ] Escaleras dentro de la batalla grande: el campo de flujo cuenta picar y escalar como costes.
- [ ] Recalcular el campo por zonas cuando se rompe o se pone un bloque.
- [ ] Reparar el muro en el descanso.

### Fase 5: combate orgánico

Lo que en Bedrock era su Fase 3, que allí quedó sin cerrar. Cada punto, solo si en Java hace falta.

- [ ] Grupos con objetivos repartidos y reserva (`squadLogic`), contra la «bolita».
- [ ] Moral: repliegue al patio y a la torre (`moraleLogic`).
- [ ] Postura y puestos de defensa (`stanceLogic`, `postLogic`).
- [ ] Órdenes al ejército con objetos en la mano: retirada, salida y defender.
- [ ] Cuerno de rescate y escolta (`rescueLogic`).
- [ ] Monstruos del juego dentro del castillo (`intruderLogic`).

### Fase 6: agua

- [ ] Escena B: un invasor que embarca, rema y desembarca él mismo.
- [ ] Navegación anfibia y barcos de varias plazas.
- [ ] Old Fallen Castle con su lago: unos cruzan el agua y otros van por tierra.

### Fase 7: guerra progresiva y gran escala

- [ ] «Lugares»: varios puntos que defender por mapa, con retirada entre ellos (pedido por Limbers en Bedrock).
- [ ] Tope de unidades en el campo y reservas; presupuesto por oleada; ritmo por etapas.
- [ ] Nivel de detalle: las unidades lejanas piensan menos.
- [ ] Batallas más largas y con más unidades, para ver el techo. Varios frentes (Ice Castle).

### Fase 8: unidades nuevas

- [ ] Unidades aéreas y acuáticas.
- [ ] Unidad que trepa muros, como las arañas.
- [ ] Arietes y torres de asedio.
- [ ] Dragones (entidad de varias piezas).
- [ ] Civiles que huyen.
- [ ] Modelos y animaciones propias con GeckoLib (golpe, cargar escalera, remar).
- [ ] Arte definitivo y permiso de las skins antes de publicar.

## Decisiones tomadas

- **Migrar a Java con Fabric y Kotlin (Limbers, 2026-11-07).** Motivos en [POR-QUE-JAVA.md](POR-QUE-JAVA.md).
- **Realismo por encima de atajos:** las unidades hacen las cosas (colocar, picar, remar); el juego no las hace por
  ellas. Si no hay otra salida que hacer trampa, se pregunta.
- **Plataforma de juego:** PC con Arch Linux. La tablet ya no es objetivo.
- **Minecraft 26.3 (2026-11-07):** es la que Limbers tiene instalada y todo el stack tiene versión para ella.
- **Buscador de rutas: el de Bedrock portado, por tramos (2026-11-07).** Los tramos a pie van con la navegación
  del juego; picar y escaleras, con `AssaultGoal`. Funcionó en la escena A.
- **Paquete `dev.limbers.bastion`, id `bastion`** (la propuesta de la fase 0), y Gradle en Kotlin
  (`build.gradle.kts`).
- **Las reglas de juego de Bedrock se mantienen** salvo que Limbers diga otra cosa: ver
  [PORTAR-DESDE-BEDROCK.md](PORTAR-DESDE-BEDROCK.md).
- **Seguir en Java (Limbers, 2026-11-07)**, tras ver la prueba inicial: «mucho más realista ... con un esfuerzo
  mucho menor».
- **Fases por rebanadas jugables (Limbers, 2026-11-07):** ver «Fases».
- **Portar de Bedrock solo lo que haga falta (Limbers, 2026-11-07):** primero se prueba sin el módulo; mucha de
  esa lógica parcheaba carencias de Bedrock.
- **Estructura (Limbers, 2026-11-07):** módulo `core` sin Minecraft, mapas en JSON y unidades en una ficha de
  Kotlin. Ver [ARQUITECTURA.md](ARQUITECTURA.md).

## Decisiones abiertas

- **Barcos:** barco del juego dirigido por Mixin, o entidad balsa propia de varias plazas.
- **Modelos:** cuándo pasar del modelo humanoide del juego a modelos propios con GeckoLib.
