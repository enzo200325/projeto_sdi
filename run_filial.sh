#!/bin/bash

# Script para subir uma filial
# Uso: ./run_filial.sh <porta> [porta_seed]

cd "$(dirname "$0")"

if [ -z "$1" ]; then
  echo "Uso: ./run_filial.sh <porta> [porta_seed]"
  echo ""
  echo "Exemplos:"
  echo "  ./run_filial.sh 9876           # Primeira filial (sem seed)"
  echo "  ./run_filial.sh 9875 9876      # Conecta ao seed na porta 9876"
  echo "  ./run_filial.sh 9874 9876      # Conecta ao seed na porta 9876"
  exit 1
fi

if [ ! -d "target/classes" ]; then
  echo "Projeto não compilado. Execute ./compile.sh primeiro."
  exit 1
fi

DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
CLASSPATH="target/classes:${DEPENDENCIES}"

PORTA=$1
SEED_PORT=$2

if [ -z "$SEED_PORT" ]; then
  echo "Iniciando Filial (porta $PORTA) - Sem seed..."
  java -cp "$CLASSPATH" servers.FilialServidorPublisher "$PORTA"
else
  echo "Iniciando Filial (porta $PORTA) - Seed: porta $SEED_PORT"
  java -cp "$CLASSPATH" servers.FilialServidorPublisher "$PORTA" "http://127.0.0.1:${SEED_PORT}/filial"
fi
