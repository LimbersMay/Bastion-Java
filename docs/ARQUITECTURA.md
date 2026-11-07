# Arquitectura prevista

Es una propuesta: se confirma o se corrige con la [prueba inicial](PRUEBA-INICIAL.md). Recoge lo aprendido en
Bedrock (`../Bastion/docs/IA.md`) y la investigación de rutas a gran escala (`../Bastion/docs/refs/rutas-y-escalada.md`).

## Principios

1. **Las unidades actúan por sí mismas.** Picar, colocar, escalar, remar y empujar son acciones de la unidad, con
   animación, sonido y tiempo, nunca efectos del director.
2. **Lógica separada del juego.** Rutas, grupos, moral, postura y oleadas son clases Kotlin puras con pruebas JUnit.
   El juego solo les da el mundo (bloques, unidades) y aplica lo que deciden. Es lo que mejor funcionó en Bedrock.
3. **Pensar en grupos, no en individuos.** Las decisiones caras (adónde ir, a quién atacar) se toman por grupo y se
   reparten. Así se escala a cientos de unidades.
4. **Presupuesto por tick.** Cada director tiene unos milisegundos por tick. Lo que no cabe se reparte entre ticks
   (por turnos), nunca congela el servidor.
5. **Todo medible.** Contadores en el log (rutas calculadas, bloques picados, escaleras puestas, atascos) y Spark.

## Capas

```
Partida (oleadas, fases, victoria/derrota)         ← porta wavePlan, siegeManager
  └─ Directores (bandos)                           ← squadLogic, moraleLogic, stanceLogic, rescueLogic, unreachableLogic
       ├─ Grupos: objetivo, formación, reserva
       └─ Navegación compartida: campos de flujo, rutas con acciones, mapa de costes
            └─ Unidades: Goals (moverse, picar, colocar, escalar, remar, pelear, disparar)
                 └─ Animación y sonido (GeckoLib)
```

## Navegación: rutas con acciones

En Bedrock, `routeLogic.ts` ya planificaba con acciones: caminar, saltar, caer, nadar, picar dentro de la zona de
asedio y poner escaleras de hasta 24 bloques. Pero el juego no seguía esa ruta y había que llevar a la unidad a
mano. En Java la ruta **es** lo que sigue la unidad.

- **Nodo:** una casilla donde cabe una unidad de 2 de alto. Cada transición lleva una **acción** y un **coste**:

| Acción | Coste (orientativo) | Quién la ejecuta |
|---|---|---|
| Caminar, saltar 1, caer ≤ 3 | Distancia | Movimiento normal |
| Nadar | Distancia × 2 | Navegación anfibia |
| Embarcar o cruzar en barco | Fijo + distancia | `Goal` de remar |
| Picar bloque | Según dureza y arma | `Goal` de picar (grieta y tiempo reales) |
| Colocar escalera y subir | Por tramo, y solo si lleva escaleras | `Goal` de escalar |
| Romper puerta | Según material | `Goal` de picar |

- **Dos caminos técnicos, a decidir en la prueba inicial:**
  1. **Ampliar el del juego:** un `NodeEvaluator` y una `PathNavigation` propios. El buscador A* del juego devuelve
     un `Path` y la unidad lo sigue; los nodos especiales (picar, escalar) se marcan y un `Goal` actúa al llegar.
  2. **Buscador propio** (el de Bedrock portado) que devuelve tramos. Los tramos de caminar se pasan a la
     navegación del juego con `moveTo`, y los especiales los ejecutan `Goal`.

  El 2 aprovecha más el código de Bedrock y sus pruebas. El 1 se integra mejor. Recomendación: empezar por el 2.
- **Límites:** búsquedas acotadas en nodos y repartidas entre ticks. En Bedrock el 73 % de las búsquedas agotaban
  su límite: hay que medir y ajustarlo con datos.
- **Ver las rutas:** el dibujado de depuración de rutas del juego, activado en desarrollo, enseña la ruta de cada
  unidad en el mundo.

## Muchas unidades: campos de flujo

Con 400 unidades no se calculan 400 rutas.

- **Campo de flujo:** desde cada destino común (la brecha, la puerta, el patio, un grupo enemigo) se calcula
  **una vez** la distancia de cada casilla al destino, y cada unidad solo mira la flecha de su casilla. Una unidad
  más casi no cuesta. Las acciones (picar, escalar) entran como costes en el cálculo.
- **Recalcular por zonas:** cuando se rompe o se pone un bloque, solo se recalcula alrededor, no el mapa entero.
- **Dos niveles:** un plan grueso por zonas o lugares (qué lado del castillo, qué puerta; encaja con los «lugares»
  de la Fase 4 de Bedrock) y rutas finas solo dentro de la zona.
- Las rutas individuales con acciones se reservan para lo especial: el grupo que sube por una escalera, el
  zapador, el inalcanzable.

## Elegir enemigo: rejilla y grupos

- **Rejilla espacial** (casillas de 8 o 16 bloques) con las unidades de cada bando, actualizada cada tick. Buscar
  al enemigo más cercano mira 9 casillas, no todas las entidades. Es lo contrario de `reselect_targets`.
- **El grupo elige; la unidad ejecuta.** El director de grupos (`squadLogic`, contra la «bolita») asigna un
  enemigo por grupo, repartido en el frente, y cada miembro hace `setTarget` al enemigo más cercano de ese reparto.
- **Frecuencias:** grupos cada 20 ticks, unidades cada 5-10, todo repartido por turnos.

## Unidades

- **Entidades propias** (subclases de `PathfinderMob` o `Monster`) con:
  - atributos de su ficha (vida, daño, velocidad: lo que hoy es `scripts/units/catalog.ts`);
  - sus `Goal` según el rol (cuerpo a cuerpo, arquero, mago, zapador).
- **Modelos:** al principio, el modelo humanoide del juego con las skins de Bedrock (64x64). Después, GeckoLib
  para animaciones propias (cargar escalera, remar, empujar ariete).
- **Equipo visible:** armas, escaleras en la mano, antorchas.
- **Nivel de detalle:** las unidades lejanas al jugador actúan con menos frecuencia y animaciones simples.

## Directores (lo que se porta de Bedrock)

| Director | Qué hace | Origen en Bedrock |
|---|---|---|
| Partida | Fases, oleadas, vidas, victoria y derrota | `siegeManager.ts`, `wavePlan.ts` |
| Grupos | Grupos de hasta 8, un enemigo por grupo, reserva | `squadLogic.ts` |
| Moral | Repliegue al patio y a la torre | `moraleLogic.ts` |
| Postura | Aguantar en desventaja, defender el puesto | `stanceLogic.ts`, `postLogic.ts` |
| Rescate | Cuerno: los aliados cercanos acuden al jugador rodeado; escolta | `rescueLogic.ts` |
| Inalcanzables | Si nadie llega a una unidad: ruta, guía, bajar a pelear | `unreachableLogic.ts` (en Java debería hacer falta mucho menos) |
| Muros | Qué bloques forman el muro y cuándo hay brecha | `wallLogic.ts` |
| Intrusos | Monstruos del juego dentro del castillo | `intruderLogic.ts` |

Los parches de Bedrock (marcadores, toque, balsas, rescate de encajados, abandono por atasco) **no se portan**. Si
en Java vuelve a hacer falta algo parecido, primero se busca la causa con el depurador.

## Más adelante: lo que Bedrock no permitía

- **Unidades aéreas:** la navegación de vuelo del juego (`FlyingPathNavigation`) se puede leer y ampliar.
  Campos de flujo en 3D, o rutas directas con evitación.
- **Unidades acuáticas:** la navegación de agua (`WaterBoundPathNavigation`), y barcos y balsas de varias plazas.
- **Arietes y torres de asedio:** entidades propias con física, empujadas por unidades (animación de empujar). El
  ariete daña bloques al chocar.
- **Dragones:** entidad de varias piezas (como `EnderDragon` con sus `EnderDragonPart`), con fases (sobrevolar,
  quemar, posarse). Su código sirve de modelo.
- **Civiles que huyen y lugares (Fase 4):** encajan en el plan grueso por zonas.

## Datos

- **Fichas de unidad:** se pasan a Kotlin (un `object` o un JSON de datos del mod) con las cifras de Bedrock. El
  generador de JSON de Bedrock no hace falta: en Java la ficha se lee al crear la entidad.
- **Mapas:** cada mapa declara sus puntos, como en `scripts/maps/*.ts`: aparición, rutas de subida, puestos,
  muros, patio, torre y orillas. Al convertir el mundo con Chunker las coordenadas se mantienen; hay que
  comprobarlo con el primer mapa.
