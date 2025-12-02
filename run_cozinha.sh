#!/bin/bash

# Script para executar o servidor da Cozinha
# Este servidor cria o registry RMI na porta especificada (padrão: 1099)
# Uso: ./run_cozinha.sh [porta]
# Exemplo: ./run_cozinha.sh 1098

cd "$(dirname "$0")"

# Porta padrão: 1099, ou pode ser passada como argumento
PORTA=${1:-1099}

# Verifica se o projeto foi compilado
if [ -d "target/classes" ]; then
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
    echo "Iniciando servidor da Cozinha na porta $PORTA..."
    java -Djava.rmi.server.hostname=localhost -Drmi.port=$PORTA -cp "$CLASSPATH" servers.CozinhaServer $PORTA
elif [ -d "out/classes" ]; then
    echo "Iniciando servidor da Cozinha na porta $PORTA..."
    java -Drmi.port=$PORTA -cp out/classes servers.CozinhaServer $PORTA
else
    echo "Projeto não compilado. Execute ./compile.sh primeiro"
    exit 1
fi

