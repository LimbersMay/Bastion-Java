# Qué se porta desde Bedrock

El proyecto de Bedrock está en `../Bastion` (rama `feature/old-fallen-castle`, versión 0.1.55). Este documento dice
qué se aprovecha, qué no y qué reglas de juego ya están decididas. Las fuentes completas siguen allí:

- `../Bastion/docs/ROADMAP.md`: historia de cada versión, qué probó Limbers y qué decidió.
- `../Bastion/docs/IA.md`: toda la investigación de IA y rendimiento, con cifras.
- `../Bastion/docs/refs/rutas-y-escalada.md`: investigación sobre rutas a gran escala y escalada.
- `../Bastion/docs/SKINS.md`: origen y autor de cada skin. **Sin licencia de reutilización:** antes de publicar hay
  que pedir permiso a los autores o cambiarlas.
- `../Bastion/tools/lab/README.md`: el laboratorio, cuyas pruebas pasan aquí a ser GameTest.

## Regla: probar antes de portar (Limbers, 2026-11-07)

Mucha de la lógica de Bedrock se escribió para tapar carencias de Bedrock (su navegación, `reselect_targets`, el
agua). Por eso **nada se porta por adelantado**:

1. Cada módulo se mira en la fase que lo necesita (ver «Fases» en [ROADMAP.md](ROADMAP.md)).
2. Primero se prueba en Java **sin él**, con una GameTest o una escena que reproduzca el problema que resolvía.
3. Si el problema no aparece, no se porta; se apunta en el roadmap que no hizo falta.
4. Si aparece, se porta **solo la parte necesaria**, con sus pruebas traducidas a JUnit.

## Qué se puede reusar (comprobado, 2026-11-07)

Revisado archivo por archivo en `../Bastion` (v0.1.55): son puros los que no importan `@minecraft`.

| Qué | Líneas | ¿Se reusa? |
|---|---|---|
| Lógica pura (`systems/*Logic.ts`, `wavePlan.ts`, `game/siegeState.ts`, `casualties.ts`) | ~2.100 | Sí, según la regla de arriba |
| Datos de mapas (`maps/*.ts`, 4 mapas) y su validador (`maps/validate.ts`) | ~870 | Sí. Las coordenadas de Stonehill coinciden tras Chunker |
| Fichas de unidad (`units/catalog.ts`), `constants.ts`, `strings.ts` | ~430 | Sí (fichas y textos; de las constantes, las que sigan teniendo sentido) |
| Pruebas (`tests/*.test.ts`) | 2.597 | Sí, junto con el módulo que prueban |
| Pegamento con el juego (`siegeManager`, `hunt`, `rescue`, `unreachable`...) | ~4.800 | No. Solo para leer qué hace cada sistema y en qué orden |

Ya portado en la Fase 1: `routeLogic` (`RoutePlanner`, con sus 25 pruebas) y las reglas de `ladderLogic`
(`holdsLadder`, dentro del buscador). `digLogic` lo sustituyen los tiempos de picar del juego (`Digging`).

## Lógica pura: se traduce a Kotlin con sus pruebas

Son módulos sin dependencias del juego (`scripts/systems/*Logic.ts`), con pruebas vitest en `tests/`. Cuando la
regla de arriba dice que hace falta uno, traducirlo casi línea a línea a Kotlin (al módulo `core`), junto con sus
pruebas a JUnit, es lo más seguro: las pruebas dicen si la traducción es correcta.

| Módulo (líneas) | Qué hace | En Java |
|---|---|---|
| `routeLogic.ts` (404) | Buscador de rutas para unidades de 2 de alto: caminar, saltar, caer, nadar, picar en la zona de asedio y escaleras de hasta 24 bloques. Lee el mundo con una función `cellAt`, así que se prueba sin juego | **Base del buscador de rutas con acciones** (ver [ARQUITECTURA.md](ARQUITECTURA.md)). Su lector de casillas, sobre `Level` |
| `ladderLogic.ts` (66) | Dónde cabe una escalera y en qué se apoya (`holdsLadder`: bloque macizo y entero, no losas ni trampillas). Prefiere piedra a madera | Reglas para el `Goal` de colocar escalera |
| `digLogic.ts` (72) | Qué bloque toca picar y cuánto aguanta | `Goal` de picar; la dureza real sale del juego |
| `squadLogic.ts` (277) | Grupos de hasta 8 por cercanía, un enemigo distinto por grupo, como mucho 1,5 grupos por grupo enemigo y reserva de hasta 30 s. Se rinde con un objetivo si en 15 s no se acerca | Director de grupos, igual |
| `moraleLogic.ts` (57) | Un grupo superado y castigado se repliega al patio y luego a la torre | Igual |
| `stanceLogic.ts` (52) | Postura defensiva: con el doble de enemigos que aliados al lado, aguanta en su sitio | Igual |
| `postLogic.ts` (58) | Puestos de defensa: cuerpo a cuerpo al frente o a los lados, tiradores en alto; sin sitio, al patio | Igual |
| `rescueLogic.ts` (102) | Cuerno: si rodean al jugador, acuden aliados; escolta de 4 en la mano | Igual |
| `unreachableLogic.ts` (236) | Casos de unidades a las que nadie llega: ruta a pie, guía, bajar a pelear | Portarlo **solo si hace falta**: con rutas con acciones debería sobrar casi todo |
| `huntLogic.ts` (80) | La caza: a quién perseguir y avance por un rastro | Parte del director de invasores |
| `wallLogic.ts` (157) | Qué bloques forman el muro, qué se lleva cada proyectil y cuándo hay hueco (brecha) | Igual |
| `intruderLogic.ts` (52) | Zona del castillo donde un monstruo del juego es intruso | Igual |
| `wavePlan.ts` (62) | Qué sale en cada oleada, por dónde y cuándo | Igual |
| `raftLogic.ts` (58), `returnLogic.ts` (89) | Balsas en línea recta y regreso del agua con empujones | **No:** en Java, barcos de verdad y navegación anfibia |
| `stallLogic.ts` (44), `stuckLogic.ts` (93) | Abandono a los 45 s sin avances; rescate de encajados | **No de entrada.** Si vuelve a hacer falta, primero buscar la causa con el depurador |

## Pegamento con Bedrock: no se porta

`scripts/systems/*.ts` sin `Logic`, `scripts/game/`, `scripts/main.ts` (unas 4.800 líneas) y el generador de
entidades JSON `tools/units/` (unas 900). Sirven para leer **qué hace cada sistema** y **en qué orden**, no para
copiar.

En especial, no se rehacen: marcadores (`bastion:waypoint`), etiquetas como filtros, `reselect_targets`, el
«toque», las balsas, los empujones, el rescate de encajados y el teletransporte del director a las escaleras.

## Datos: se copian

### Fichas de unidad (`../Bastion/scripts/units/catalog.ts`)

| Unidad | Bando | Rol | Vida | Golpe | Velocidad | Arma | Notas |
|---|---|---|---|---|---|---|---|
| Soldado aliado | Aliado | Cuerpo a cuerpo | 60 | 10 | 0,29 | Espada de hierro | |
| Guerrero aliado | Aliado | Cuerpo a cuerpo | 80 | 13 | 0,26 | Hacha de hierro | No lo empujan (tanque de la brecha) |
| Pirómano aliado | Aliado | Mago | 30 | 2 | 0,24 | Vara de blaze | Bola de fuego como la del Ghast, desde su puesto |
| Arquero aliado | Aliado | Arquero | 30 | 3 | 0,25 | Arco | Flecha de 5, cada 1,5-2 s, a 24 bloques; se aparta si tiene a alguien a menos de 5 |
| Soldado invasor | Invasor | Cuerpo a cuerpo | 40 | 9 | 0,28 | Hacha de hierro | Pica paredes en la zona de asedio |
| Mago invasor | Invasor | Mago | 24 | 2 | 0,24 | Vara de breeze | Ataca el muro hasta la brecha; proyectil arcano que explota |
| Arquero invasor | Invasor | Arquero | 24 | 3 | 0,26 | Arco | Flecha de 4, cada 2-3 s, a 20 bloques; nunca apunta al muro |

- **Variación:** ±10 % de vida al nacer y ±10 % en cada golpe; las cifras de 2-3 quedan fijas.
- **Velocidad:** el valor de Bedrock está en la misma escala que el atributo de velocidad de Java (el zombi va a
  0,23). Comprobar que se siente igual.
- **Familias:** los aliados atacan a los monstruos del juego (después de los invasores), y los monstruos los
  atacan como al gólem de hierro. Los invasores son inmunes al fuego y a sus propios proyectiles; los aliados, al
  fuego (no a las explosiones).
- **Skins:** 64x64, en `../Bastion/resource_packs/bastion/textures/entity/`, varias por unidad y una al azar al
  nacer. Valen para el modelo humanoide de Java. Origen en `docs/SKINS.md`.

### Oleadas y refuerzos (Stonehill, cifras de Limbers)

- **Oleadas:**
  - 15 soldados + 5 magos + 5 arqueros;
  - 34 + 6 + 6;
  - 52 + 8 + 8;
  - 12 de tropa de brecha.
- **Refuerzos aliados:** 30 por oleada, con un mínimo de 12 en el campo. De cada 10: 5 soldados, 2 guerreros, 2
  arqueros y 1 pirómano.
- **Tope de magos:** 1 de cada 4.

### Mapas (`../Bastion/scripts/maps/*.ts` y mundos en `../Bastion/assets/worlds/`)

| Mapa | Mundo | Qué tiene |
|---|---|---|
| **Stonehill Castle** (el principal, el del «rush») | `Stonehill Castle - v1.4.mcworld` (de mcpedl) | Una sola entrada a pie, la puerta este, por un camino empedrado desde el valle del noreste. Muro en el arco de la puerta (x -284..-282, z 201..212, y 125..130). Ruta de subida de 4 hitos. Pirómano en lo alto de la torre de la esquina, arqueros en los dos sitios altos que ven algo. Torre (`keep`) para el repliegue |
| Old Fallen Castle | `Old Fallen Castle - v1.3.mcworld` | Castillo junto a un lago con puente. Muro en la entrada (x -16..-14, y 120..125, z -441..-436). 9 puestos de defensa. Es el mapa de la batalla grande de 400 unidades |
| The Combat Castle | `The Combat Castle fix v1` | El primer mapa |
| Ice Castle | `Ice Castle 1.1.mcworld` | Enorme (54 MB, 290 de alto). Para un mapa grande con varios frentes (Fase 6) |

- **Convertir:** pasar cada mundo a Java con Chunker y comprobar que las coordenadas coinciden. Si coinciden, los
  datos de cada mapa (aparición, hitos, puestos, muros, orillas) se copian tal cual.
- **Cuidado:** en Bedrock, las explosiones rompían madera del mapa y ese daño quedaba en el mundo entre partidas.
  Conviene guardar una copia limpia de cada mundo convertido y reponer los bloques al reiniciar.

## Reglas de juego decididas por Limbers (se mantienen)

- Partida: cuenta atrás, oleadas, descanso, victoria y derrota, con vidas compartidas.
- Órdenes al ejército con objetos en la mano: retirada a los puestos, salida al ataque y defender.
- **Sin teletransporte al caer:** se regresa caminando, para que las peleas ocurran en varios sitios.
- Cruzar por encima de un muro escalonado es válido.
- En el agua solo se apunta a quien nada si uno también está en el agua.
- Duelos entre soldados de 6 a 10,5 s.
- Aliados inmunes al fuego, no a las explosiones.
- Pirómanos escasos: unos 8-10 de cada 80 unidades.
- Los soldados derriban paredes solo en la zona del castillo, ni el puente ni la orilla.
- El muro de la puerta lo rompen sobre todo los magos; los soldados, despacio.
- Red de seguridad contra oleadas colgadas: abandono a los 45 s sin avances. Se mantiene la idea, pero en Java
  debería no hacer falta.
- Moral: repliegue al patio y luego a la torre. Postura defensiva: nadie sale del castillo con la orden de
  defender.
- Cuerno de rescate y escolta de 4.
- Monstruos del juego: los aliados van a por ellos solo dentro del castillo, si atacaron a un jugador hace poco o si
  están muy cerca; siempre después de los invasores.

## Lo que quedó pendiente en Bedrock (pasa a este proyecto)

- **Fase 4, «lugares»:** cada mapa con varios lugares (castillo, aldeas, torres), cada uno con su zona, puestos,
  punto de reunión y guarnición. Tope de unidades por bando con reserva. Ritmo por etapas.
- **Fase 6, más frentes:** escaleras de asalto (aquí son la base), unidades voladoras, varias capas de muro, picos
  y arietes, reparar el muro en el descanso, invasores que cruzan el lago mientras otros van por tierra.
- **Más adelante:** unidades aéreas y acuáticas, una unidad que trepe muros como las arañas, civiles que huyen,
  modelos propios y arte definitivo.

## Lecciones de Bedrock que valen igual aquí

- **Medir antes de decidir:** una sola partida engaña. En Bedrock, una prueba contaminada hizo creer que un arreglo
  funcionaba. Repetir y aislar.
- **El césped no es aire:** comparar nombres de bloque por palabra (`grass`) confundió `grass_block` con hierba.
  Usar listas explícitas o las etiquetas de bloque del juego.
- **Puntos de aparición en suelo abierto:** en sitios estrechos se asfixian unos contra otros.
- **Los tiradores necesitan línea de visión:** en Stonehill, desde el paso de ronda no se veía nada. Calcular la
  vista de cada puesto.
- **El fuego se propaga y destroza mapas de madera.**
