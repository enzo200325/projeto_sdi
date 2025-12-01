#!/bin/bash

# Script para executar o servidor do Restaurante
# Este servidor precisa que o CozinhaServer esteja rodando

cd "$(dirname "$0")"

# Verifica se o projeto foi compilado
if [ -d "target/classes" ]; then
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
    echo "Iniciando servidor do Restaurante..."
    echo "Certifique-se de que o CozinhaServer está rodando!"
    java -Djava.rmi.server.hostname=localhost -cp "$CLASSPATH" servers.RestauranteServer
elif [ -d "out/classes" ]; then
    echo "Iniciando servidor do Restaurante..."
    echo "Certifique-se de que o CozinhaServer está rodando!"
    java -cp out/classes servers.RestauranteServer
else
    echo "Projeto não compilado. Execute ./compile.sh primeiro"
    exit 1
fi

