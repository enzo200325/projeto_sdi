#!/bin/bash

# Script para executar o servidor do Mercado (SOAP)

cd "$(dirname "$0")"

# Verifica se o projeto foi compilado
if [ -d "target/classes" ]; then
    # Obtém o classpath das dependências do Maven
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
    echo "Iniciando servidor do Mercado (SOAP)..."
    java -cp "$CLASSPATH" servers.MercadoServidorPublisher
elif [ -d "out/classes" ]; then
    echo "Iniciando servidor do Mercado (SOAP)..."
    echo "⚠️  ATENÇÃO: Executando sem dependências Maven. Pode falhar se JAX-WS não estiver no classpath."
    java -cp out/classes servers.MercadoServidorPublisher
else
    echo "Projeto não compilado. Execute ./compile.sh primeiro"
    exit 1
fi

