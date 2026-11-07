# Stack, versiones y flujo de trabajo

## Piezas

| Pieza | Qué es | Para qué la usamos |
|---|---|---|
| **Minecraft Java Edition** | El juego | La versión se fija al empezar (ver «Versiones») |
| **Fabric Loader** | Cargador de mods ligero | Carga el mod |
| **Fabric API** | Librería de Fabric | Eventos, registro de entidades e ítems, GameTest, red |
| **Fabric Loom** | Plugin de Gradle | Descarga el juego, prepara el entorno y da las tareas `runClient`, `runServer` y GameTest |
| **Mixins** | Inyectar código en clases del juego | Solo donde la API no llegue (por ejemplo, el buscador de rutas) |
| **Kotlin** + `fabric-language-kotlin` | Lenguaje y adaptador oficial de Fabric | Todo el código del mod |
| **GeckoLib** | Librería de modelos y animaciones | Unidades animadas: golpe, cargar escalera, remar, arietes, dragones |
| **Blockbench** | Editor de modelos (también en Linux) | Modelos y animaciones para GeckoLib. Las skins de 64x64 de Bedrock valen para el modelo humanoide |
| **GameTest** (en Fabric API) | Pruebas dentro del juego | Pruebas automáticas sin pantalla: ver [PRUEBAS-Y-MEDIDAS.md](PRUEBAS-Y-MEDIDAS.md) |
| **JUnit 5** | Pruebas normales de JVM | Lógica pura (rutas, grupos, moral...), la parte portada de los `*Logic.ts` |
| **Spark** | Perfilador (mod) | Qué método gasta cada milisegundo del tick, también los del juego |
| **Lithium** | Optimización del servidor (mod) | Más margen para la IA. Solo para jugar y medir: no es una dependencia del mod |
| **Sodium** | Optimización del dibujo (mod) | Más FPS con cientos de unidades en pantalla |
| **EntityCulling** | Mod | No dibuja entidades que no se ven |
| **Chunker** | Conversor de mundos Bedrock y Java (de Hive Games, código abierto) | Pasar Stonehill, Old Fallen Castle, The Combat Castle e Ice Castle a Java |
| **Prism Launcher** | Lanzador de Minecraft para Linux | Jugar con Fabric y los mods en Arch |

## Versiones

**Política:** se fija **una** versión de Minecraft y de cada dependencia en `gradle.properties` y no se sube sin el
OK de Limbers.

Estado a **2026-11-07**, según la documentación de Fabric y las páginas de los mods (comprobar al empezar):

- La línea actual es la **26.x**:
  - la documentación de Fabric está escrita para la **26.1.2**;
  - Fabric API ya publica para la **26.2**;
  - Sodium soporta la 26.1, 26.1.1 y 26.1.2;
  - GeckoLib (5.5.x) soporta la 26.2, 26.1.2 y 26.1.
- Desde la 26.1, Java Edition **ya no está ofuscado** y se usan los nombres oficiales de Mojang:
  - el plugin de Gradle pasa a llamarse `net.fabricmc.fabric-loom` y no remapea;
  - las dependencias usan `implementation` en lugar de `modImplementation`;
  - las guías anteriores a la 26.1 tienen pasos que ya no aplican.
- Hace falta **Java 25** (JDK) y **Gradle 9.4** o más nuevo.
- **La API de GameTest cambió entre versiones.** En la 26.2 los métodos usan `GameTestHelper` y terminan con
  `succeed()`; en las antiguas usaban `TestContext` y `complete()`. Copia ejemplos solo de la documentación de la
  versión fijada.

**Elegida (2026-11-07): la 26.3.** Para entonces ya existían la 26.3 estable y snapshots de la 26.4, y todas las
piezas tenían versión para la 26.3. Las versiones exactas están en `gradle.properties` y en
[ROADMAP.md](ROADMAP.md). Gradle y el juego de desarrollo funcionan con el JDK 27 del sistema; el mod se compila
para Java 25.

**Cómo se eligió (y cómo elegir en una subida futura):** la versión más nueva en la que existan a la vez Fabric API,
`fabric-language-kotlin`, GeckoLib, Spark, Sodium y Lithium. Hoy apunta a la **26.1.2**, que tiene más mods
publicados; la 26.2 si ya los tiene todos. Se anota la elección y la fecha en [ROADMAP.md](ROADMAP.md).

Dónde comprobarlo:

- Fabric: https://fabricmc.net/develop/ (versiones de Loader, API y Loom) y https://docs.fabricmc.net/develop/
- Kotlin para Fabric: https://modrinth.com/mod/fabric-language-kotlin
- GeckoLib: https://modrinth.com/mod/geckolib y su wiki en GitHub (`bernie-g/geckolib`)
- Spark: https://spark.lucko.me/ ; Lithium y Sodium: https://modrinth.com/mod/lithium , https://modrinth.com/mod/sodium
- Chunker: https://chunker.app/

## Instalación en Arch Linux (para jugar y para desarrollar)

```bash
# JDK 25 (el paquete exacto puede variar: jdk25-openjdk o el que esté en los repos en ese momento)
sudo pacman -S jdk25-openjdk
archlinux-java status            # ver los JDK instalados
sudo archlinux-java set java-25-openjdk

# Lanzador para jugar (AUR o Flatpak)
yay -S prismlauncher             # o: flatpak install flathub org.prismlauncher.PrismLauncher

# Editor y modelos
yay -S intellij-idea-community-edition   # o el IDE que se prefiera; IntelliJ tiene el mejor soporte de Kotlin
yay -S blockbench-bin                    # o Flatpak: net.blockbench.Blockbench
```

En Prism Launcher, para jugar:

1. Nueva instancia con la versión fijada.
2. Fabric Loader, y desde la pestaña de mods: Fabric API, Fabric Language Kotlin, GeckoLib, Sodium, Lithium,
   EntityCulling y Spark.
3. El `.jar` de Bastion en la carpeta `mods`.

## Crear el proyecto (lo primero de la prueba inicial)

1. Generar la plantilla con el generador oficial: https://fabricmc.net/develop/template/
   - nombre del mod `Bastion`, id `bastion`, paquete por ejemplo `dev.limbers.bastion`;
   - **lenguaje Kotlin**, con la versión de Minecraft elegida;
   - con los «data generators» y las pruebas (GameTest) si el generador lo ofrece.
2. Copiar la plantilla en la raíz de este repo. No pisar `README.md`, `AGENTS.md` ni `docs/`.
3. Añadir GeckoLib como dependencia (su wiki indica el repositorio Maven y la coordenada de cada versión).
4. `./gradlew build` y `./gradlew runClient` deben funcionar antes de escribir nada propio.

## Flujo de trabajo diario

| Tarea | Comando o herramienta |
|---|---|
| Compilar y pasar pruebas de lógica | `./gradlew build` (JUnit) |
| Abrir el juego con el mod | `./gradlew runClient` (o la configuración que Loom crea en IntelliJ) |
| Servidor dedicado de desarrollo | `./gradlew runServer` (carpeta `run/`) |
| Pruebas dentro del juego | La tarea de GameTest que configure Loom (`fabricApi { configureTests { ... } }` en `build.gradle`); ver [PRUEBAS-Y-MEDIDAS.md](PRUEBAS-Y-MEDIDAS.md) |
| Depurar | Lanzar `runClient` o `runServer` en modo depuración desde el IDE y poner puntos de parada, también en clases del juego |
| Cambiar código sin reiniciar | Recarga en caliente del IDE (cuerpos de métodos). Con el JetBrains Runtime se pueden cambiar más cosas |
| Mandar órdenes al servidor sin entrar al juego | Consola de `runServer` o RCON (`enable-rcon=true` en `server.properties`). Es el equivalente al `cmd.txt` de Bedrock |
| Medir | Spark: `/spark profiler start` … `/spark profiler stop` (da un enlace con el desglose); `/spark tps`, `/spark health` |

## Cómo trabajará Claude en las sesiones

- Igual que con BDS: el servidor de desarrollo (`runServer`) encendido en segundo plano, órdenes por RCON o por la
  consola y el log leído desde fuera.
- Pero lo principal serán las **GameTest**: cada comportamiento con su prueba, que Claude puede ejecutar sin abrir
  el juego.
- Lo que solo se puede juzgar mirando (que se sienta real) lo ve Limbers en su PC.
