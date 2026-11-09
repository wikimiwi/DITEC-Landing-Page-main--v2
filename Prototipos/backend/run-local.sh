#!/usr/bin/env bash
# Sobe o backend localmente lendo as variaveis do arquivo .env (nao versionado).
# Uso:  ./run-local.sh            (compila e inicia em http://localhost:8080)
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f .env ]; then
  echo "Arquivo .env nao encontrado. Copie .env.example para .env e preencha DB_PASSWORD e JWT_SECRET." >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
. ./.env
set +a
mvn -q -B -DskipTests package
# roda uma COPIA do JAR: recompilar com o servidor ligado nao quebra a instancia em execucao
mkdir -p target/run && cp target/ditec-assistencia.jar target/run/ditec-assistencia.jar
exec java -jar target/run/ditec-assistencia.jar
