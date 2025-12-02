#!/bin/bash

# Script para executar o cliente MesasCliente
# Este cliente precisa que o RestauranteServer esteja rodando
# Uso: ./run_cliente.sh [porta]
# Exemplo: ./run_cliente.sh 1098
# (A porta deve ser a mesma usada no run_cozinha.sh e run_restaurante.sh)

cd "$(dirname "$0")"

# Porta padrão: 1099, ou pode ser passada como argumento
PORTA=${1:-1099}

# Verifica se o projeto foi compilado
if [ -d "target/classes" ]; then
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
    echo "Iniciando cliente MesasCliente..."
    echo "Certifique-se de que o RestauranteServer está rodando na porta $PORTA!"
    java -Djava.rmi.server.hostname=localhost -Drmi.port=$PORTA -cp "$CLASSPATH" MesasCliente
elif [ -d "out/classes" ]; then
    echo "Iniciando cliente MesasCliente..."
    echo "Certifique-se de que o RestauranteServer está rodando na porta $PORTA!"
    java -Drmi.port=$PORTA -cp out/classes MesasCliente
else
    echo "Projeto não compilado. Execute ./compile.sh primeiro"
    exit 1
fi

