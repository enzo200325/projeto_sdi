#!/bin/bash

# Script para compilar o projeto Java
# Usa Maven se disponível, caso contrário tenta compilação manual

cd "$(dirname "$0")"

# Verifica se Maven está disponível
if command -v mvn &> /dev/null; then
    echo "Compilando projeto com Maven..."
    mvn clean compile
    if [ $? -eq 0 ]; then
        echo "Compilação concluída com sucesso!"
        echo "Classes compiladas em: target/classes"
    else
        echo "Erro na compilação com Maven!"
        exit 1
    fi
else
    echo "Maven não encontrado. Tentando compilação manual..."
    echo "⚠️  ATENÇÃO: A compilação manual pode falhar devido a dependências JAX-WS."
    echo "    Instale o Maven ou baixe as dependências JAX-WS manualmente."
    echo ""
    
    # Cria o diretório de classes compiladas
    mkdir -p out/classes

    # Compila todos os arquivos Java
    echo "Compilando projeto..."
    javac -d out/classes -sourcepath src src/**/*.java src/*.java

    if [ $? -eq 0 ]; then
        echo "Compilação concluída com sucesso!"
        echo "Classes compiladas em: out/classes"
    else
        echo "Erro na compilação!"
        echo "Por favor, instale o Maven: brew install maven (macOS) ou apt-get install maven (Linux)"
        exit 1
    fi
fi

