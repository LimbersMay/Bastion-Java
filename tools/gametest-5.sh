#!/usr/bin/env bash
# Ejecuta las GameTest varias veces (5 por defecto) y exige que pasen todas.
#
# Por qué: el lanzador de pruebas de Fabric (26.3) no repite las pruebas aunque la anotación diga
# `maxAttempts = 5, requiredSuccesses = 5`; cada una corre una vez. Y una sola pasada engaña (lección de Bedrock).
#
# Uso: tools/gametest-5.sh [veces]
set -euo pipefail
cd "$(dirname "$0")/.."
runs="${1:-5}"
logdir="build/gametest-runs"
mkdir -p "$logdir"
passed=0
for i in $(seq 1 "$runs"); do
	log="$logdir/run-$i.log"
	if ./gradlew runGameTest --console=plain >"$log" 2>&1; then
		passed=$((passed + 1))
		echo "Pasada $i/$runs: bien ($(grep -oE 'All [0-9]+ required tests passed' "$log" || echo 'sin resumen'))"
	else
		echo "Pasada $i/$runs: FALLA"
		grep -E 'failed at' "$log" | sed -E 's/.*\(Minecraft\) /  /' | cut -c1-300 || true
	fi
done
echo "Resultado: $passed de $runs pasadas sin fallos (registros en $logdir)"
[ "$passed" -eq "$runs" ]
