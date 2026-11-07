# Roadmap de Bastion (Java)

**Empieza aquí en cada sesión.** Dice dónde nos quedamos, qué sigue y en qué fase va la migración. Al terminar una
sesión, actualiza «Dónde nos quedamos».

## Dónde nos quedamos

*Última actualización: 2026-11-07 (segunda sesión). Escenas A y C hechas. A: el invasor pica y pone sus escaleras él
mismo (commit `ea3377a`). C: 200 contra 200 a 20 ticks/s, con campo de flujo y rejilla. Falta: los FPS en el PC de
Limbers en un mapa real, el desglose de Spark y la escena B (barco).*

- **Escena C de la prueba inicial, hecha (2026-11-07).** Rama `feature/prueba-inicial`, sin commit todavía.
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

    - Con Lithium el tick baja un 40 %. Ojo: en la primera medida el mundo era nuevo y aún generaba terreno; en la de
      Lithium ya estaba generado. Parte de la mejora puede venir de ahí.
    - **El peor tick sigue por encima de 50 ms** (71 ms): picos sueltos, probablemente de chunks (guardado o carga).
      El p95 está muy por debajo. A vigilar en el mapa del castillo.
    - Lo que pesa es del juego (mover y chocar 400 criaturas con el terreno, los chunks), no nuestro (2-3 %).
    - La meta del campo de flujo era una fila; en terreno real cayó en la orilla de un lago y quedó aislada (32
      casillas). Ahora es toda la franja de los defensores. El agua sigue sin cruzarse (llegará con la escena B).
    - Lithium (servidor) y Sodium (cliente, beta para 26.3) van en el entorno de desarrollo (`localRuntime`).
  - **Falta:** los FPS de Limbers con Sodium; el mapa real del castillo (convertir Stonehill u Old Fallen Castle con
    Chunker: hace falta que Limbers descargue el `.mcworld` de mcpedl); el desglose por método de Spark (`/spark profiler` sube el informe a
    spark.lucko.me: preguntar a Limbers antes de usarlo, o guardarlo en archivo); medir con Lithium; y una batalla más
    larga o con más unidades para ver dónde está el techo.
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
- **Siguiente paso:** la [prueba inicial](PRUEBA-INICIAL.md):
  1. ~~Elegir y fijar versiones.~~ Hecho: 26.3.
  2. ~~Crear el proyecto con la plantilla oficial de Fabric en Kotlin.~~ Hecho: `build`, `runServer`,
     `runClient` y la GameTest vacía funcionan.
  3. ~~Escena A: el invasor que coloca su escalera y pica con grieta, con sus GameTest.~~ Hecha; falta la opinión
     de Limbers.
  4. ~~Escena C: 400 unidades con campo de flujo, medidas con Spark.~~ Hecha: 20 ticks/s; faltan FPS y desglose.
  5. Escena B: el barco, si da tiempo.

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

## Fases de la migración

### Fase 0: planificación (hecha, 2026-11-07)

- [x] Decidir la migración y el stack.
- [x] Guía completa para retomar: este documento y los demás de `docs/`.

### Fase 1: prueba inicial

Criterios y escenas en [PRUEBA-INICIAL.md](PRUEBA-INICIAL.md).

- [x] Versiones elegidas y fijadas (26.3, 2026-11-07).
- [x] Proyecto creado; `build`, `runClient`, `runServer` y una GameTest vacía funcionan (2026-11-07).
- [x] Escena A: escalera colocada por el invasor, picar con grieta, escalera derribada. GameTest 5 de 5
      (2026-11-07). Falta que Limbers diga si se siente real.
- [x] Escena C: 400 unidades, campo de flujo, rejilla; medido (ticks/s y ms por tick, 2026-11-07). Faltan el
      desglose de Spark y los FPS en el PC de Limbers.
- [ ] Escena B: cruce en barco.
- [ ] Limbers lo ve en su PC y decide si se sigue.

### Fase 2: base del juego

Lo que en Bedrock era la «Base del juego» y la Fase 1, ya con acciones reales.

- [ ] Convertir Stonehill con Chunker y comprobar las coordenadas.
- [ ] Portar las fichas de unidad (7 unidades), con skins y variación ±10 %.
- [ ] Portar `wavePlan` y el director de partida: cuenta atrás, oleadas, descanso, vidas, victoria y derrota.
- [ ] Portar `wallLogic`: muro de la puerta, brecha.
- [ ] Buscador de rutas con acciones (a partir de `routeLogic`) y campos de flujo para el grueso del ejército.
- [ ] Órdenes al ejército (retirada a puestos, salida, defender) con `postLogic`.
- [ ] Magos y arqueros: proyectiles, línea de visión y puestos en alto.
- [ ] Banco de la batalla grande (equivalente a `cost.mjs`).

### Fase 3: combate orgánico

Lo que en Bedrock era su Fase 3, que allí quedó sin cerrar.

- [ ] Grupos con objetivos repartidos y reserva (`squadLogic`).
- [ ] Moral: repliegue al patio y a la torre (`moraleLogic`).
- [ ] Postura defensiva (`stanceLogic`).
- [ ] Cuerno de rescate y escolta (`rescueLogic`).
- [ ] Animaciones propias con GeckoLib: golpe, cargar escalera, remar.
- [ ] Barcos y balsas de varias plazas para cruzar el agua.

### Fase 4 en adelante: lo que Bedrock no dejaba

- [ ] «Lugares»: varios puntos que defender por mapa, con retirada entre ellos (pedido por Limbers en Bedrock).
- [ ] Tope de unidades en el campo y reservas; ritmo por etapas.
- [ ] Arietes y torres de asedio.
- [ ] Unidades aéreas y acuáticas.
- [ ] Dragones (entidad de varias piezas).
- [ ] Varias capas de muro, reparar en el descanso, más frentes (Ice Castle).
- [ ] Civiles que huyen.
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

## Decisiones abiertas

- **Barcos:** barco del juego dirigido por Mixin, o entidad balsa propia de varias plazas.
- **Modelos:** cuándo pasar del modelo humanoide del juego a modelos propios con GeckoLib.
