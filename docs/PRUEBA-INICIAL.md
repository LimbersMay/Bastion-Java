# Prueba inicial

**Objetivo:** comprobar con hechos, en **1 o 2 sesiones**, que en Java se puede hacer lo que en Bedrock nos costó
meses. Se ataca primero lo más difícil. Si sale bien, se migra el resto con confianza; si no, se sabe pronto y se
decide con datos.

No se porta nada de Bedrock todavía: esto es una maqueta. El código se puede tirar.

## Escena A: el asalto con escalera (realismo)

Una plataforma de prueba (GameTest o un mundo plano):

- un **muro de piedra de 8 bloques de alto** con una franja de **tablones de madera** de 2 de ancho en la base;
- un **invasor** a 15 bloques del muro, del lado de fuera;
- un **defensor** aliado arriba, en el paso de ronda.

Lo que debe pasar, sin teletransportes ni bloques puestos por el juego:

1. **Ruta con acciones.** El invasor calcula una ruta que incluye «subir por una escalera que pondré aquí» o
   «picar este bloque». Elige la que cueste menos, como hace el planificador de Bedrock (`routeLogic.ts`).
2. **Lleva la escalera.** Se ve en su mano (`ItemStack` de escaleras en la mano o equipo).
3. **La coloca él.** Junto al muro, mirando al bloque, hace el gesto (`swing`), suena y aparece un tramo. Sube un
   tramo y coloca el siguiente hasta arriba. Si se queda sin escaleras, no hace trampa: busca otra opción.
4. **Sube por ella** con el movimiento normal del juego.
5. **Arriba, ataca al defensor** con `setTarget`, no con un «toque».
6. Variante **picar**: sin escaleras en la mano, pica la franja de madera:
   - golpes visibles;
   - la grieta avanzando por fases (`level.destroyBlockProgress`);
   - partículas y sonido;
   - el tiempo depende de la dureza del bloque;
   - al acabar, el bloque se rompe y pasa por el hueco.
7. Variante **defensa:** el defensor rompe la escalera mientras el invasor sube, y el invasor cae.

**Criterios de éxito:**

| Prueba (GameTest) | Éxito |
|---|---|
| `asaltoConEscalera` | El invasor llega arriba en ≤ 30 s y el defensor recibe daño, en 5 de 5 ejecuciones |
| `asaltoPicando` | El invasor abre hueco en la madera y pasa al otro lado en ≤ 40 s, 5 de 5 |
| `escaleraDerribada` | Si se rompe la escalera con el invasor en ella, cae; no queda flotando ni se teletransporta |
| Sin trampas | En el log no hay teletransportes ni bloques puestos por el director: todo lo pone el invasor |

Y **Limbers lo ve en su PC** (`./gradlew runClient` o el `.jar` en Prism): ¿se siente real?

## Escena B: el agua (realismo)

- Un lago de 20 bloques entre el punto de aparición y la orilla del castillo.
- 6 invasores suben a **un barco de verdad**, o a una balsa propia de varias plazas si el barco del juego (2
  plazas) se queda corto.
- Uno «rema»: el código dirige el barco hacia la orilla elegida, con los remos animados.
- Al llegar, bajan y siguen su ruta.

**Éxito:** los 6 cruzan y desembarcan en ≤ 40 s, 5 de 5, sin empujones ni teletransportes.

Esta escena puede quedarse para la segunda sesión si la A se alarga.

## Escena C: 400 unidades (rendimiento)

En un terreno abierto, con un castillo sencillo o el mundo de Stonehill convertido con Chunker:

1. **200 invasores contra 200 aliados.**
2. Los invasores van a la puerta con un **campo de flujo** compartido (ver [ARQUITECTURA.md](ARQUITECTURA.md)), no
   400 rutas.
3. Elección de enemigo con una **rejilla espacial**, cada pocos ticks y por grupos.
4. Medir con Spark **dos minutos**:
   - ticks/s;
   - milisegundos por tick (media y peor);
   - el desglose de lo que gasta (nuestro director, rutas, colisiones, otras entidades).
5. Medir también los **FPS** en el PC de Limbers con Sodium y EntityCulling.

**Referencias de Bedrock**, en la misma batalla de unas 400 unidades en un PC: 7,5 ticks/s en la v0.1.24 y 2-4 en
la v0.1.55.

**Éxito:**

- 20 ticks/s estables, con el peor tick por debajo de 50 ms.
- Ningún método nuestro por encima del 30 % del tick.
- FPS jugables en el PC de Limbers.

Si no se llega, el desglose de Spark dice qué atacar. Eso ya es un éxito frente a Bedrock, donde había que
adivinar.

## Pasos

1. Elegir y fijar la versión (ver [STACK.md](STACK.md), «Versiones») y anotarla en [ROADMAP.md](ROADMAP.md).
2. Crear el proyecto con la plantilla oficial (Kotlin) y comprobar que `./gradlew build`, `runClient` y
   `runServer` funcionan.
3. Configurar GameTest y escribir una prueba vacía que pase («el mundo existe»). Así el bucle de pruebas funciona
   antes de la IA.
4. Escena A, en este orden:
   1. entidad invasora (de momento con el modelo humanoide del juego y una skin de Bedrock);
   2. un `Goal` de «picar bloque» con grieta y sonido;
   3. un `Goal` de «colocar escalera y subir»;
   4. un buscador de rutas mínimo que decida entre picar, escalar o rodear;
   5. sus GameTest.
5. Escena C: 400 entidades, campo de flujo y rejilla. Medición con Spark.
6. Escena B, si da tiempo.
7. Apuntar resultados (cifras, qué fue fácil, qué no) en [ROADMAP.md](ROADMAP.md) y decidir con Limbers.

## Qué se decide al final

| Resultado | Decisión |
|---|---|
| A y C salen bien | Migrar: fase 1 del [ROADMAP.md](ROADMAP.md) (portar la lógica y los mapas) |
| A bien, C justo | Migrar igualmente, con el rendimiento como prioridad de la fase 1 (Spark dice dónde) |
| A sale mal | Revisar por qué. Si es por no conocer Fabric, otra sesión. Si es un límite del juego, valorarlo con Limbers |

## Notas para quien la haga

- **Picar:** el código del zombi que rompe puertas (`BreakDoorGoal`) es el modelo para la grieta por fases y el
  sonido. El tiempo por bloque sale de la dureza (`getDestroySpeed`) y del arma.
- **Escaleras:**
  - una escalera de mano necesita un bloque macizo detrás (en Bedrock se aprendió que no se apoya en losas ni
    trampillas: `holdsLadder`);
  - las criaturas suben por ellas si se mueven contra ellas (`onClimbable`), pero el buscador de rutas del juego
    no las planifica: de eso se encarga nuestro buscador o nuestro `Goal`.
- **Ver las rutas:** el juego tiene un dibujado de depuración de rutas, apagado en el juego normal. Activarlo en
  desarrollo con un Mixin o con el paquete de depuración ahorra horas.
- **Barcos:** en el juego base solo los dirige quien lo controla (un jugador). Hay dos caminos: una entidad barco
  propia que se mueve sola, o dirigir el barco del juego desde el `tick` del remero con un Mixin. Elegir el más
  simple que se vea bien.
- **No hacer trampas para que pase la prueba.** Si algo solo sale teletransportando, se anota como fallo y se
  pregunta a Limbers.
