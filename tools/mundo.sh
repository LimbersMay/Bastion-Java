#!/usr/bin/env bash
# Mundos de los mapas: convertir un .mcworld de Bedrock a Java con Chunker y reponer una copia limpia.
#
#   tools/mundo.sh convertir <archivo.mcworld> <id>   convierte y guarda la copia limpia en run/mundos-limpios/<id>
#   tools/mundo.sh reponer <id>                       copia la limpia sobre run/<id> (con el servidor parado)
#   tools/mundo.sh usar <id>                          pone level-name=<id> en run/server.properties
#
# <id> es el del mapa (data/bastion/maps/<id>.json). Los mundos son de terceros: viven en run/, fuera del
# repositorio. Chunker (HiveGamesOSS, versión fijada abajo) se descarga una vez a ~/.cache/bastion y se comprueba
# con su suma SHA-256. Tras convertir, `/bastion mapa <id> comprobar` en el juego dice si las coordenadas coinciden.
set -euo pipefail

CHUNKER_VERSION=1.21.0
CHUNKER_SHA256=63148e587f5ee5a2ca7ebae1079b235c82ae4d1eff8f7a8a8a3398332513dda6
CHUNKER_FORMAT=JAVA_26_3

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/run"
CLEAN="$RUN/mundos-limpios"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/bastion"
JAR="$CACHE/chunker-cli-$CHUNKER_VERSION.jar"

die() { echo "mundo.sh: $*" >&2; exit 1; }

chunker() {
	if [[ ! -f "$JAR" ]]; then
		mkdir -p "$CACHE"
		echo "Descargando Chunker $CHUNKER_VERSION..."
		curl -fL -o "$JAR.part" "https://github.com/HiveGamesOSS/Chunker/releases/download/$CHUNKER_VERSION/chunker-cli-$CHUNKER_VERSION.jar"
		mv "$JAR.part" "$JAR"
	fi
	echo "$CHUNKER_SHA256  $JAR" | sha256sum -c --quiet - || die "la suma de $JAR no coincide; bórralo y vuelve a probar"
}

check_id() { [[ "$1" =~ ^[a-z0-9_-]+$ ]] || die "id no válido «$1» (minúsculas, números, _ y -)"; }

case "${1:-}" in
	convertir)
		[[ $# -eq 3 ]] || die "uso: tools/mundo.sh convertir <archivo.mcworld> <id>"
		src="$2"; id="$3"; check_id "$id"
		[[ -f "$src" ]] || die "no existe $src"
		chunker
		tmp="$(mktemp -d)"
		trap 'rm -rf "$tmp"' EXIT
		unzip -q "$src" -d "$tmp/bedrock"
		[[ -f "$tmp/bedrock/level.dat" ]] || die "$src no parece un mundo de Bedrock (falta level.dat)"
		rm -rf "$CLEAN/$id"
		mkdir -p "$CLEAN"
		java -jar "$JAR" --inputDirectory "$tmp/bedrock" --outputFormat "$CHUNKER_FORMAT" --outputDirectory "$CLEAN/$id" | grep -v '^[0-9.]*%$' || true
		[[ -f "$CLEAN/$id/level.dat" ]] || die "Chunker no dejó un mundo en $CLEAN/$id"
		echo "Copia limpia en $CLEAN/$id. Siguiente: tools/mundo.sh reponer $id && tools/mundo.sh usar $id"
		;;
	reponer)
		[[ $# -eq 2 ]] || die "uso: tools/mundo.sh reponer <id>"
		id="$2"; check_id "$id"
		[[ -d "$CLEAN/$id" ]] || die "no hay copia limpia de $id; primero tools/mundo.sh convertir"
		if pgrep -f -- '-Dfabric.dli.env=server' > /dev/null; then die "el servidor de desarrollo está en marcha: páralo antes (stop)"; fi
		rm -rf "${RUN:?}/$id"
		cp -a "$CLEAN/$id" "$RUN/$id"
		echo "run/$id repuesto desde la copia limpia."
		;;
	usar)
		[[ $# -eq 2 ]] || die "uso: tools/mundo.sh usar <id>"
		id="$2"; check_id "$id"
		[[ -d "$RUN/$id" ]] || die "no existe run/$id"
		sed -i "s/^level-name=.*/level-name=$id/" "$RUN/server.properties"
		echo "El servidor de desarrollo abrirá run/$id."
		;;
	*)
		sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
		exit 1
		;;
esac
