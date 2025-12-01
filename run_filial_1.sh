#!/bin/bash

# Sobe a filial 1 na porta 9876

cd "$(dirname "$0")"

if [ ! -d "target/classes" ]; then
  echo "Projeto não compilado. Execute ./compile.sh primeiro."
  exit 1
fi

DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
CLASSPATH="target/classes:${DEPENDENCIES}"

echo "Iniciando Filial 1 (porta 9876)..."
java -cp "$CLASSPATH" servers.FilialServidorPublisher 9876



