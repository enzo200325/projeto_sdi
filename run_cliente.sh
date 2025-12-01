#!/bin/bash

# Script para executar o cliente MesasCliente
# Este cliente precisa que o RestauranteServer esteja rodando

cd "$(dirname "$0")"

# Verifica se o projeto foi compilado
if [ -d "target/classes" ]; then
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
    echo "Iniciando cliente MesasCliente..."
    echo "Certifique-se de que o RestauranteServer está rodando!"
    java -Djava.rmi.server.hostname=localhost -cp "$CLASSPATH" MesasCliente
elif [ -d "out/classes" ]; then
    echo "Iniciando cliente MesasCliente..."
    echo "Certifique-se de que o RestauranteServer está rodando!"
    java -cp out/classes MesasCliente
else
    echo "Projeto não compilado. Execute ./compile.sh primeiro"
    exit 1
fi

