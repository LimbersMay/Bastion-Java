# Por qué migramos a Java

Decidido por Limbers el **2026-11-07**, tras meses de iterar en Bedrock (versiones 0.1.1 a 0.1.55).

## El problema de fondo

En Bedrock **no se puede decir a una criatura «ve aquí» ni «ataca a ese»**. Solo se describe en JSON a quién
le gusta perseguir (filtros, prioridades, distancias), y el motor decide con un código que no se ve. Los scripts
(`@minecraft/server`) pueden leer el mundo, poner y quitar bloques, teletransportar y dar empujones, pero no
manejan la navegación.

Casi todo lo construido en Bedrock eran formas de empujar esa caja negra. Por eso volvían los mismos problemas:
atascos, rescates, enemigos sin ruta. No se sabía por qué pasaban; se adivinaba y se parcheaba.

Limbers lo resumió así: quiere **realismo**, una invasión que se sienta real, no «rara». Los enemigos no pueden
romper muros porque se atascan, y cuando los rompen no hay animación. Los botes, las rutas y las escaleras se ven
raros, y hay que rescatar a la mayoría.

## Parche por parche: Bedrock frente a Java

| Necesidad | Cómo se hacía en Bedrock (parche) | Cómo se hace en Java con Fabric |
|---|---|---|
| Mandar a una unidad a un punto | Una entidad invisible («marcador», `bastion:waypoint`) a la que la unidad «ataca», y etiquetas que filtran cuál le toca | `mob.navigation.moveTo(x, y, z, velocidad)` o un `Goal` propio |
| Elegir a quién ataca | Filtros JSON con etiquetas, prioridades y 16 entradas por grupo | `mob.setTarget(enemigo)`, decidido por nuestro código |
| Cambiar de objetivo | `reselect_targets: true`, que revisa todo en cada tick y hunde la batalla de 400 a 2-4 ticks/s. Sin él, las unidades no sueltan el objetivo viejo | Una línea, cuando el director lo decida, sin coste extra |
| Que ataque de verdad al llegar | El «toque»: el objetivo le hace 1 de daño para que la unidad reaccione | `setTarget` y listo |
| Rutas | Planificador propio en script (`routeLogic.ts`) y un marcador que se adelanta paso a paso, porque el motor no aceptaba la ruta | Nuestra propia búsqueda de rutas (`NodeEvaluator`/`PathFinder`), o la del juego ampliada |
| Picar muros | El script rompe el bloque cuando decide que toca. Sin grieta ni animación de golpe | Golpe (`swing`), grieta por fases (`destroyBlockProgress`), partículas, sonido y bloque roto, como el zombi que rompe puertas |
| Escaleras | El director las pone «por arte de magia», mete a la unidad y la sube a mano: las criaturas no suben solas. Si se queman, se reponen | El invasor lleva la escalera en la mano y la coloca tramo a tramo mientras sube. Si la rompen, cae |
| Agua | Balsas propias que el script mueve en línea recta, nado, empujones hacia la orilla y varias orillas por mapa | Barcos de verdad (las criaturas montan con `startRiding`) dirigidos por código, con remos, o navegación anfibia |
| Atascos | `stuckLogic`, «encajado», rescates cada 15 s, abandono a los 45 s, director de inalcanzables... por síntomas | Depurador con puntos de parada y la ruta de cada criatura dibujada en el mundo: se ve **por qué** se atasca |
| Coste | Medir a ciegas, comparar versiones viejas, trasplantar componentes. Así se tardó un día en encontrar `reselect_targets` | Spark dice qué función gasta, también dentro del juego |
| Pruebas | Servidor BDS, `cmd.txt`, leer el log y contar líneas a mano | GameTest: estructuras con criterio de éxito, sin pantalla, en `./gradlew` |

### El último ejemplo, que acabó de decidirlo (2026-11-07)

Se encontró por qué la batalla grande había caído de 7,5 a 2-4 ticks/s: `reselect_targets`.

1. Sin él, iba a 19-20 ticks/s.
2. Pero las unidades no soltaban su objetivo cuando el script les cambiaba las etiquetas, y en Stonehill se
   quedaban en el primer hito de la subida.
3. Para «despertarlas» se probó a añadirles un grupo de componentes vacío. No funcionó, y una prueba contaminada
   hizo creer durante un rato que sí.
4. Ese ciclo de suponer, parchear y descubrir que la caja negra hace otra cosa es justo lo que se quiere dejar
   atrás.

Detalle completo en `../Bastion/docs/IA.md`, «Coste en batallas grandes, otra vez».

## Qué se gana

- **Control total de la IA:** objetivos, rutas, comportamientos (`Goal`) y navegación son código nuestro o código
  del juego que se puede leer y cambiar. Desde la línea 26.x Mojang publica Java **sin ofuscar**, con los nombres
  reales.
- **Realismo:** animaciones propias (GeckoLib), acciones de verdad (colocar, picar, remar, empujar un ariete) y
  entidades de varias piezas (como el dragón del End) para dragones y máquinas de asedio.
- **Rendimiento medible:** el coste es nuestro código, y Spark dice dónde está. Las técnicas de juegos de estrategia
  (campos de flujo, IA por grupos, nivel de detalle por distancia) se pueden aplicar.
- **Pruebas de verdad:** GameTest y depurador. El laboratorio de Bedrock (`tools/lab/`) pasa a ser pruebas
  repetibles.
- **Linux:** Java funciona de forma nativa en Arch Linux. Bedrock no tiene cliente oficial para Linux.

## Qué se pierde (aceptado)

- **La tablet y Bedrock en general:** Java no funciona oficialmente en Android. Si algún día hiciera falta, Geyser
  deja entrar a jugadores de Bedrock a un servidor Java, siempre que el contenido del mod se pueda mostrar con
  cosas del juego base (librería Polymer). Hoy no es un objetivo.
- **Repartirlo fácil:** cada jugador necesita Java Edition, Fabric y el mod (un `.jar`), en lugar de un
  `.mcaddon`.
- **Actualizaciones:** los mods de Java se rompen con las versiones de Minecraft. Se fija una versión y solo se
  sube cuando Limbers lo decide.
- **Trabajo ya hecho:** el «pegamento» con Bedrock (unas 4.800 líneas) y el generador de fichas JSON (900) no se
  aprovechan. La lógica pura, las pruebas, las fichas, los mapas y lo aprendido sí: ver
  [PORTAR-DESDE-BEDROCK.md](PORTAR-DESDE-BEDROCK.md).

## Qué no cambia

- El juego: asedios por oleadas, muros que caen, órdenes al ejército, grupos, moral, rescate con cuerno, mapas
  con castillos reales.
- Las decisiones de diseño de Limbers (cifras, reglas, orden de las fases): se mantienen salvo que diga otra cosa.
- La forma de trabajar: medir antes de decidir, lógica pura con pruebas, documentación al día y Limbers decide.

## Riesgos y cómo se controlan

| Riesgo | Control |
|---|---|
| Que en Java también cueste mucho | La [prueba inicial](PRUEBA-INICIAL.md) ataca primero lo más difícil (escalera colocada por el invasor, picar con grieta, 400 unidades medidas) antes de portar nada más |
| Curva de aprendizaje (Gradle, Fabric, Mixins) | Seguir la documentación oficial de Fabric para la versión fijada; poco Mixin y siempre comentado |
| Rendimiento del dibujo con cientos de entidades | Sodium y EntityCulling; modelos simples a distancia; medir en el PC de Limbers desde el principio |
| Versiones que se rompen | Versión fijada en `gradle.properties`; subir solo con OK y con las pruebas en verde |
