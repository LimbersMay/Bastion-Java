# Bastion (Java)

Juego de asedios para Minecraft Java Edition: defiendes un castillo con tus tropas contra oleadas de invasores que
llegan, pican los muros, ponen escaleras, cruzan el agua y pelean en grupo. Es la continuación de **Bastion para
Bedrock** (`../Bastion`), que se migra a Java para poder hacerlo **realista**.

> **Estado (2026-11-07): fase 1, prueba inicial.** El proyecto (Minecraft 26.3, Fabric, Kotlin) compila y pasa
> una GameTest vacía; todavía no hay IA. Lo siguiente, la escena A de
> [docs/PRUEBA-INICIAL.md](docs/PRUEBA-INICIAL.md).

## Empieza aquí

Si retomas el proyecto en una sesión nueva, lee en este orden:

1. [docs/ROADMAP.md](docs/ROADMAP.md): **dónde nos quedamos**, qué sigue y las fases de la migración.
2. [docs/POR-QUE-JAVA.md](docs/POR-QUE-JAVA.md): por qué se deja Bedrock, qué se gana y qué se pierde. Explica el
   espíritu del proyecto: realismo sin parches.
3. [docs/PRUEBA-INICIAL.md](docs/PRUEBA-INICIAL.md): la primera tarea, con sus criterios de éxito.
4. [docs/STACK.md](docs/STACK.md): herramientas, versiones, instalación en Arch Linux y flujo de trabajo.
5. [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md): cómo se piensa construir la IA (rutas con acciones, campos de
   flujo, director, grupos) para que escale a cientos de unidades.
6. [docs/PRUEBAS-Y-MEDIDAS.md](docs/PRUEBAS-Y-MEDIDAS.md): cómo se prueba (GameTest) y cómo se mide el rendimiento
   (Spark), el sustituto del laboratorio de Bedrock.
7. [docs/PORTAR-DESDE-BEDROCK.md](docs/PORTAR-DESDE-BEDROCK.md): qué se aprovecha del proyecto de Bedrock, módulo a
   módulo, y las reglas de juego y cifras ya decididas por Limbers.

Reglas de trabajo para sesiones (idioma, git, pruebas, documentación): [AGENTS.md](AGENTS.md).

## La idea en una frase

Que una invasión **se sienta real**:

- los invasores llevan escaleras y las colocan ellos;
- pican los muros con golpes y grietas;
- reman en barcos hasta la orilla;
- encuentran su camino sin teletransportes ni rescates.

Y que aguante batallas de cientos de unidades, con unidades aéreas y acuáticas, arietes y dragones más adelante.

## Stack previsto

| Pieza | Para qué |
|---|---|
| Minecraft Java Edition 26.3 | El juego. Desde la línea 26.x Mojang publica el código sin ofuscar: se lee con sus nombres reales |
| Fabric (Loader, API y Loom) | Cargar el mod y modificar el juego por dentro (Mixins) |
| Kotlin (`fabric-language-kotlin`) | El lenguaje. Se parece a TypeScript, lo que facilita portar la lógica de Bedrock |
| GeckoLib + Blockbench | Modelos y animaciones: golpes, cargar escaleras, remar, arietes, dragones |
| GameTest (Fabric API) | Pruebas automáticas dentro del juego, sin pantalla |
| Spark | Perfilador: qué función gasta cada milisegundo |
| Lithium, Sodium, EntityCulling | Rendimiento del servidor y del dibujo |

Detalle y versiones: [docs/STACK.md](docs/STACK.md).

## Dónde se juega

En **Arch Linux (PC)**, con Java nativo. Bedrock no funciona oficialmente en Linux, y la tablet ya no es la
plataforma de juego.

## Relación con el proyecto de Bedrock

- **Carpeta:** `../Bastion` (rama `feature/old-fallen-castle`). Queda **como referencia** y no se sigue
  desarrollando salvo que Limbers lo pida. Su última versión es la 0.1.55.
- **Lo que se lleva:**
  - su lógica pura y sus pruebas, unas 3.000 y 2.600 líneas de TypeScript, que se traducen a Kotlin;
  - las fichas de unidad, los mapas, las skins y los mundos (se convierten con Chunker);
  - todo lo aprendido, en `../Bastion/docs/IA.md` y `../Bastion/docs/ROADMAP.md`.
- **Lo que no se lleva:** los parches contra la navegación de Bedrock. Ver
  [docs/POR-QUE-JAVA.md](docs/POR-QUE-JAVA.md).
