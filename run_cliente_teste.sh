#!/bin/bash

# Script para executar o cliente de teste automatizado

cd "$(dirname "$0")"

# Verifica se o projeto foi compilado
if [ -d "target/classes" ]; then
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
    echo "Iniciando cliente de teste..."
    echo "Certifique-se de que todos os servidores estão rodando!"
    echo ""
    java -Djava.rmi.server.hostname=localhost -cp "$CLASSPATH" ClienteTeste
elif [ -d "out/classes" ]; then
    echo "Iniciando cliente de teste..."
    echo "Certifique-se de que todos os servidores estão rodando!"
    echo ""
    java -cp out/classes ClienteTeste
else
    echo "Projeto não compilado. Execute ./compile.sh primeiro"
    exit 1
fi

