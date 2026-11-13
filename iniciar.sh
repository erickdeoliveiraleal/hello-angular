#!/usr/bin/env bash
# Sobe o backend (Spring Boot, porta 8080) e o frontend (Angular, porta 4200)
# no mesmo terminal. Ctrl+C para os dois.
# Precisa de JAVA_HOME apontando para um JDK 17 ou superior (ou java 17+ no PATH).
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d node_modules ]; then
  echo "Instalando as dependências do Angular..."
  npm install
fi

(cd backend && ./mvnw spring-boot:run) &
BACKEND=$!
# Ao sair (Ctrl+C ou erro), derruba também o backend.
trap 'kill $BACKEND 2>/dev/null' EXIT

npx ng serve
