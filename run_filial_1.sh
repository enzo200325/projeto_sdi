#!/bin/bash

# Sobe a filial na porta 9876

cd "$(dirname "$0")"

if [ ! -d "target/classes" ]; then
  echo "Projeto não compilado. Execute ./compile.sh primeiro."
  exit 1
fi

DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
CLASSPATH="target/classes:${DEPENDENCIES}"

SEED_PORT=$1

if [ -z "$SEED_PORT" ]; then
  echo "Iniciando Filial (porta 9876) - Sem seed..."
  java -cp "$CLASSPATH" servers.FilialServidorPublisher 9876
else
  echo "Iniciando Filial (porta 9876) - Seed: porta $SEED_PORT"
  java -cp "$CLASSPATH" servers.FilialServidorPublisher 9876 "http://127.0.0.1:${SEED_PORT}/filial"
fi
