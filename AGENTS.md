# Reglas de trabajo en Bastion (Java)

Se suman a las globales de Limbers (`~/.claude/CLAUDE.md`): sin subagentes, sin commits ni push sin su OK, sin
atribución a Claude, nunca commit en `main` ni con `git add -A` o `git add .`, y respuestas en español.

## Con Limbers

- No es experto en UI/UX ni en modding de Java. Explica en palabras llanas, da opciones con una recomendación y
  decide él.
- Lo que busca es **realismo**: que la invasión se sienta real. Ante dos soluciones, prefiere la que hace que las
  unidades actúen por sí mismas (colocar, picar, remar) frente a la que el juego hace por ellas (teletransportar,
  poner bloques mágicamente, empujar). Si no hay más remedio que hacer trampa, se dice y se pregunta.
- Juega en **Arch Linux**. Cualquier instrucción de instalación o de ejecución es para Linux. Desarrollar en Windows
  también vale: este repo se creó desde Windows.

## Al empezar una sesión

1. Lee [docs/ROADMAP.md](docs/ROADMAP.md), «Dónde nos quedamos».
2. Si vas a tocar IA o rutas, lee [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) y lo que corresponda de
   [docs/PORTAR-DESDE-BEDROCK.md](docs/PORTAR-DESDE-BEDROCK.md).
3. Comprueba la versión fijada de Minecraft, Fabric y las librerías (`gradle.properties`, cuando exista). **No
   subas de versión sin preguntar:** cada subida puede romper Mixins y APIs.

## Al terminar una sesión

- Actualiza «Dónde nos quedamos» en [docs/ROADMAP.md](docs/ROADMAP.md): fecha, qué se hizo, qué se midió y qué
  sigue.
- Lo que se mida (ticks/s, ms por tick, unidades) se apunta con fecha, versión y cómo se midió, como en el proyecto
  de Bedrock.
- Propón el commit (archivos y mensaje) y espera el OK. Formato: `<type>(<scope>): <subject>`, cuerpo y pie.

## Código

- **Kotlin**, con la lógica de juego **separada del juego**: el cálculo (rutas, grupos, moral...) va en clases sin
  dependencias de Minecraft, con pruebas JUnit rápidas. El «pegamento» con el juego va aparte. Es lo que mejor
  funcionó en Bedrock (`*Logic.ts`).
- Comentarios y documentación en **español**. Nombres de código en inglés, como en el proyecto de Bedrock.
- **Mixins solo cuando no haya otra vía:** primero la API de Fabric o subclases (`Goal`, `PathNavigation`,
  `NodeEvaluator` propios). Cada Mixin, con un comentario de por qué hace falta.
- Nada de teletransportes para mover tropas, salvo como último recurso registrado en el log. En Bedrock eran la
  norma, y aquí son la excepción.

## Pruebas y medidas

- Cada comportamiento nuevo, con su **GameTest**: una estructura pequeña y un criterio de éxito («el invasor llega
  arriba en 30 s»). Ver [docs/PRUEBAS-Y-MEDIDAS.md](docs/PRUEBAS-Y-MEDIDAS.md).
- Cada cambio que pueda pesar, medido con el banco de rendimiento antes y después.
- `./gradlew build` y las pruebas deben pasar antes de proponer un commit.

## Referencias

- Proyecto de Bedrock: `../Bastion` (solo lectura salvo que Limbers pida algo allí).
- Lo aprendido en Bedrock: `../Bastion/docs/IA.md`. Investigación de rutas y escalada:
  `../Bastion/docs/refs/rutas-y-escalada.md`.
