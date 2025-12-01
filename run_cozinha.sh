#!/bin/bash

# Script para executar o servidor da Cozinha
# Este servidor cria o registry RMI na porta 1099

cd "$(dirname "$0")"

# Verifica se o projeto foi compilado
if [ -d "target/classes" ]; then
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
    echo "Iniciando servidor da Cozinha..."
    java -Djava.rmi.server.hostname=localhost -cp "$CLASSPATH" servers.CozinhaServer
elif [ -d "out/classes" ]; then
    echo "Iniciando servidor da Cozinha..."
    java -cp out/classes servers.CozinhaServer
else
    echo "Projeto não compilado. Execute ./compile.sh primeiro"
    exit 1
fi

