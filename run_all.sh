#!/bin/bash

# Script para iniciar todos os servidores necessários
# Uso: ./run_all.sh [porta]
# Exemplo: ./run_all.sh 1098

cd "$(dirname "$0")"

PORTA=${1:-1099}

echo "=========================================="
echo "  Iniciando todos os servidores"
echo "  Porta RMI: $PORTA"
echo "=========================================="
echo ""

# Verifica se o projeto foi compilado
if [ ! -d "target/classes" ] && [ ! -d "out/classes" ]; then
    echo "❌ Projeto não compilado. Execute ./compile.sh primeiro"
    exit 1
fi

# Função para verificar se uma porta está em uso
check_port() {
    if lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 0  # Porta em uso
    else
        return 1  # Porta livre
    fi
}

# Verifica se a porta já está em uso
if check_port $PORTA; then
    echo "⚠️  ATENÇÃO: Porta $PORTA já está em uso!"
    echo "   Isso pode significar que os servidores já estão rodando."
    echo "   Se quiser continuar mesmo assim, pressione Enter."
    echo "   Para cancelar, pressione Ctrl+C."
    read
fi

# Obtém classpath
if [ -d "target/classes" ]; then
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
else
    CLASSPATH="out/classes"
fi

echo "1️⃣  Iniciando CozinhaServer (cria registry RMI)..."
java -Djava.rmi.server.hostname=localhost -Drmi.port=$PORTA -cp "$CLASSPATH" servers.CozinhaServer $PORTA &
COZINHA_PID=$!
sleep 2

if ! kill -0 $COZINHA_PID 2>/dev/null; then
    echo "❌ Erro ao iniciar CozinhaServer"
    exit 1
fi

echo "   ✓ CozinhaServer iniciado (PID: $COZINHA_PID)"
echo ""

echo "2️⃣  Iniciando MercadoServidorPublisher (SOAP)..."
java -cp "$CLASSPATH" servers.MercadoServidorPublisher &
MERCADO_PID=$!
sleep 2

if ! kill -0 $MERCADO_PID 2>/dev/null; then
    echo "❌ Erro ao iniciar MercadoServidorPublisher"
    kill $COZINHA_PID 2>/dev/null
    exit 1
fi

echo "   ✓ MercadoServidorPublisher iniciado (PID: $MERCADO_PID)"
echo ""

echo "3️⃣  Aguardando 3 segundos para os servidores estabilizarem..."
sleep 3

echo "4️⃣  Iniciando RestauranteServer..."
java -Djava.rmi.server.hostname=localhost -Drmi.port=$PORTA -cp "$CLASSPATH" servers.RestauranteServer &
RESTAURANTE_PID=$!
sleep 2

if ! kill -0 $RESTAURANTE_PID 2>/dev/null; then
    echo "❌ Erro ao iniciar RestauranteServer"
    kill $COZINHA_PID $MERCADO_PID 2>/dev/null
    exit 1
fi

echo "   ✓ RestauranteServer iniciado (PID: $RESTAURANTE_PID)"
echo ""

echo "=========================================="
echo "  ✅ Todos os servidores iniciados!"
echo "=========================================="
echo ""
echo "PIDs dos processos:"
echo "  - CozinhaServer: $COZINHA_PID"
echo "  - MercadoServidorPublisher: $MERCADO_PID"
echo "  - RestauranteServer: $RESTAURANTE_PID"
echo ""
echo "Para parar todos os servidores, execute:"
echo "  kill $COZINHA_PID $MERCADO_PID $RESTAURANTE_PID"
echo ""
echo "Ou pressione Ctrl+C para parar este script (os servidores continuarão rodando)"
echo ""
echo "Agora você pode executar o cliente de teste:"
echo "  ./run_cliente_teste.sh $PORTA"
echo ""

# Aguarda Ctrl+C
trap "echo ''; echo 'Servidores continuarão rodando. Para parar, execute: kill $COZINHA_PID $MERCADO_PID $RESTAURANTE_PID'; exit 0" INT
wait


