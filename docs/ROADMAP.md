# Roadmap de Bastion (Java)

**Empieza aquí en cada sesión.** Dice dónde nos quedamos, qué sigue y en qué fase va la migración. Al terminar una
sesión, actualiza «Dónde nos quedamos».

## Dónde nos quedamos

*Última actualización: 2026-11-07. Sin código todavía. Repositorio creado con `git init` (rama `main`), sin commits:
Limbers revisa y da el OK.*

- **Migración decidida por Limbers (2026-11-07).**
  - Jugará en Arch Linux y quiere realismo: invasores que pican muros con animación, ponen ellos mismos las
    escaleras, usan barcos de verdad y encuentran rutas sin rescates.
  - Más adelante: unidades aéreas y acuáticas, arietes y dragones.
  - En Bedrock la navegación es una caja negra y obligaba a parches. Motivos completos en
    [POR-QUE-JAVA.md](POR-QUE-JAVA.md).
- **Stack elegido:** Fabric + Kotlin + GeckoLib + GameTest + Spark, más Lithium, Sodium y EntityCulling para jugar.
  Ver [STACK.md](STACK.md).
- **Proyecto de Bedrock:** `../Bastion`, versión 0.1.55, rama `feature/old-fallen-castle`. Queda como referencia.
  - Tiene **pendiente el commit de su Fase 3** (v0.1.44-0.1.55) cuando Limbers dé el OK.
  - En su v0.1.55 `reselect_targets` vuelve a estar puesto, así que la batalla grande va lenta (2-4 ticks/s), pero
    todo lo demás funciona como antes.
- **Siguiente paso:** la [prueba inicial](PRUEBA-INICIAL.md):
  1. Elegir y fijar versiones (anotarlas aquí).
  2. Crear el proyecto con la plantilla oficial de Fabric en Kotlin.
  3. Escena A: el invasor que coloca su escalera y pica con grieta, con sus GameTest.
  4. Escena C: 400 unidades con campo de flujo, medidas con Spark.
  5. Escena B: el barco, si da tiempo.

## Versiones fijadas

| Pieza | Versión | Fecha |
|---|---|---|
| Minecraft | (por elegir: probablemente 26.1.2 o 26.2) | |
| Fabric Loader / API / Loom | | |
| Kotlin / `fabric-language-kotlin` | | |
| GeckoLib | | |
| JDK | 25 | |

## Fases de la migración

### Fase 0: planificación (hecha, 2026-11-07)

- [x] Decidir la migración y el stack.
- [x] Guía completa para retomar: este documento y los demás de `docs/`.

### Fase 1: prueba inicial

Criterios y escenas en [PRUEBA-INICIAL.md](PRUEBA-INICIAL.md).

- [ ] Versiones elegidas y fijadas.
- [ ] Proyecto creado; `build`, `runClient`, `runServer` y una GameTest vacía funcionan.
- [ ] Escena A: escalera colocada por el invasor, picar con grieta, escalera derribada. GameTest 5 de 5.
- [ ] Escena C: 400 unidades, campo de flujo, rejilla; medido con Spark (ticks/s, ms por tick, desglose) y FPS en el
      PC de Limbers.
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
- **Las reglas de juego de Bedrock se mantienen** salvo que Limbers diga otra cosa: ver
  [PORTAR-DESDE-BEDROCK.md](PORTAR-DESDE-BEDROCK.md).

## Decisiones abiertas

- **Versión de Minecraft:** 26.1.2 (más mods publicados) o 26.2 (más nueva). Se decide al empezar la prueba
  inicial.
- **Buscador de rutas:** ampliar el del juego (`NodeEvaluator` propio) o portar el de Bedrock y usarlo por tramos.
  Recomendación: portar el de Bedrock (ver [ARQUITECTURA.md](ARQUITECTURA.md)).
- **Barcos:** barco del juego dirigido por Mixin, o entidad balsa propia de varias plazas.
- **Modelos:** cuándo pasar del modelo humanoide del juego a modelos propios con GeckoLib.
- **Nombre del paquete y del mod:** propuesta `dev.limbers.bastion`, id `bastion`.
