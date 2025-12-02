#!/bin/bash

# Script para verificar se os serviços RMI estão registrados
# Uso: ./verificar_rmi.sh [porta]

cd "$(dirname "$0")"

PORTA=${1:-1099}

echo "Verificando serviços RMI na porta $PORTA..."
echo ""

# Verifica se a porta está em uso
if ! lsof -Pi :$PORTA -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "❌ Nenhum processo está escutando na porta $PORTA"
    echo "   Execute ./run_cozinha.sh primeiro para criar o registry RMI"
    exit 1
fi

echo "✓ Porta $PORTA está em uso"
echo ""

# Tenta listar os serviços registrados
if [ -d "target/classes" ]; then
    DEPENDENCIES=$(mvn dependency:build-classpath -DincludeScope=runtime 2>&1 | grep -v "^\[" | grep -v "WARNING" | grep -v "INFO" | tail -1)
    CLASSPATH="target/classes:${DEPENDENCIES}"
else
    CLASSPATH="out/classes"
fi

echo "Tentando listar serviços registrados..."
java -Drmi.port=$PORTA -cp "$CLASSPATH" -c "
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
try {
    Registry registry = LocateRegistry.getRegistry(\"localhost\", $PORTA);
    String[] services = registry.list();
    System.out.println(\"Serviços registrados:\");
    for (String service : services) {
        System.out.println(\"  - \" + service);
    }
    if (services.length == 0) {
        System.out.println(\"  (nenhum serviço registrado)\");
    }
} catch (Exception e) {
    System.err.println(\"Erro: \" + e.getMessage());
    e.printStackTrace();
}
" 2>&1 || echo "Não foi possível listar serviços (pode ser normal se o registry não estiver acessível)"

echo ""
echo "Para testar manualmente, execute:"
echo "  java -Drmi.port=$PORTA -cp \"$CLASSPATH\" ClienteTeste"


