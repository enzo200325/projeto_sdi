# Projeto SDI - Sistema de Restaurante

Este projeto implementa um sistema distribuído de restaurante usando RMI (Remote Method Invocation) e SOAP Web Services.

## Estrutura do Projeto

- **Servidores:**
  - `CozinhaServer`: Servidor RMI que gerencia a cozinha (porta 1099)
  - `RestauranteServer`: Servidor RMI que gerencia o restaurante
  - `MercadoServidorPublisher`: Servidor SOAP que funciona como interface web para as filiais (porta 9000)
  - `FilialServidorPublisher`: Servidor SOAP que gerencia filiais e executa eleição/consenso (portas 9876, 9875, 9874)

- **Cliente:**
  - `MesasCliente`: Cliente que interage com o restaurante

## Arquitetura

```
Cliente → Restaurante (RMI) → Mercado (SOAP) → Filiais (SOAP)
                                              ↓
                                    [Consenso entre filiais]
```

> **Nota:** Esta branch (`parteFinalEleicao`) implementa:
> - Eleição de líder entre filiais usando algoritmo de anel
> - Consenso entre filiais para decidir qual atende pedidos
> - Mercado funciona como interface web, coordenando com as filiais

## Pré-requisitos

- Java 8 ou superior
- Maven (recomendado) - para gerenciar dependências JAX-WS
  - **Instalar no macOS:** `brew install maven`
  - **Instalar no Linux:** `sudo apt-get install maven` ou `sudo yum install maven`
  - **Verificar instalação:** `mvn --version`
- Terminal/Shell (bash ou zsh)

> **Nota:** O projeto usa JAX-WS (SOAP Web Services), que não está mais incluído no JDK a partir do Java 9. O Maven baixa automaticamente essas dependências. Se não quiser usar Maven, você precisará baixar manualmente os JARs do JAX-WS.

## Como Executar

### 1. Compilar o projeto

O script `compile.sh` detecta automaticamente se você tem Maven instalado:

```bash
chmod +x compile.sh
./compile.sh
```

**Com Maven (recomendado):**
```bash
mvn clean compile
```

**Sem Maven:**
O script tentará compilar manualmente, mas pode falhar devido às dependências JAX-WS necessárias.

### 2. Executar os servidores (em terminais separados)

**Terminal 1 - Servidor da Cozinha:**
```bash
chmod +x run_cozinha.sh
./run_cozinha.sh
```

**Terminal 2 - Servidor do Mercado:**
```bash
chmod +x run_mercado.sh
./run_mercado.sh
```

**Terminal 3 - Servidor do Restaurante:**
```bash
chmod +x run_restaurante.sh
./run_restaurante.sh
```

**Terminal 4 - Servidor de Filiais (opcional, apenas nesta branch):**
```bash
chmod +x run_filial.sh
./run_filial.sh
```

### 3. Executar o cliente

**Terminal 5 - Cliente:**
```bash
chmod +x run_cliente.sh
./run_cliente.sh
```

## Ordem de Execução

⚠️ **IMPORTANTE:** Execute os servidores nesta ordem:

1. Primeiro: `CozinhaServer` (cria o registry RMI)
2. Segundo: `MercadoServidorPublisher` (serviço SOAP)
3. Terceiro: `RestauranteServer` (depende do CozinhaServer e Mercado)
4. Por último: `MesasCliente` (depende do RestauranteServer)

## Alternativa: Execução Manual

**Com Maven:**

```bash
# Compilar
mvn clean compile

# Executar servidores (em terminais separados)
# O classpath inclui automaticamente as dependências
mvn exec:java -Dexec.mainClass="servers.CozinhaServer"
mvn exec:java -Dexec.mainClass="servers.MercadoServidorPublisher"
mvn exec:java -Dexec.mainClass="servers.RestauranteServer"
mvn exec:java -Dexec.mainClass="MesasCliente"
```

**Sem Maven (requer dependências JAX-WS baixadas manualmente):**

```bash
# Compilar
javac -d out/classes -sourcepath src src/**/*.java src/*.java

# Executar servidores (em terminais separados)
# Nota: Você precisará adicionar os JARs do JAX-WS ao classpath
java -cp out/classes:jaxws-api.jar:jaxws-rt.jar servers.CozinhaServer
java -cp out/classes:jaxws-api.jar:jaxws-rt.jar servers.MercadoServidorPublisher
java -cp out/classes:jaxws-api.jar:jaxws-rt.jar servers.RestauranteServer
java -cp out/classes MesasCliente
```

## Notas

- O projeto usa Java 8 (conforme configurado no IntelliJ)
- Certifique-se de que as portas estão disponíveis:
  - 1099: RMI (Cozinha e Restaurante)
  - 9000: SOAP (Mercado)
  - 9876, 9875, 9874: SOAP (Filiais)
- O arquivo `cardapio/menu_restaurante.csv` é lido pelo RestauranteImpl e FilialImpl
- **Branch atual:** `parteFinalEleicao` - inclui funcionalidade de eleição de líder e consenso entre filiais
- **Arquitetura:** Restaurante conecta apenas ao Mercado; Mercado coordena com as filiais

## Funcionalidade de Eleição e Consenso (Branch parteFinalEleicao)

Esta branch implementa:

### 1. Eleição de Líder
- 3 filiais são publicadas nas portas 9876, 9875 e 9874
- Cada filial conhece a próxima no anel
- O algoritmo encontra o líder (filial com maior ID)
- O resultado é anunciado a todas as filiais

### 2. Sistema de Estoque e Consenso
- **Estoque nas Filiais**: Cada filial mantém um estoque de produtos (inicializado aleatoriamente entre 0-5 unidades)
- **Mercado como Interface**: O Mercado funciona como interface web para as filiais, coordenando pedidos
- **Consenso para Pedidos**: Quando o restaurante não tem estoque de um produto:
  1. O restaurante faz pedido ao Mercado (via SOAP)
  2. O Mercado coordena com as filiais, pedindo à primeira filial
  3. A filial inicia consenso entre todas as filiais
  4. As filiais verificam se têm estoque do produto solicitado
  5. O algoritmo de consenso escolhe a filial com maior ID que tenha estoque
  6. A filial escolhida processa o pedido
  7. O Mercado retorna sucesso/falha ao restaurante
  8. O restaurante adiciona o produto ao seu estoque e processa o pedido do cliente

### Fluxo de Pedido com Filiais:
```
Cliente faz pedido → Restaurante verifica estoque
  ↓ (sem estoque)
Restaurante → Mercado (SOAP)
  ↓
Mercado → Primeira Filial
  ↓
Filiais fazem consenso (anel)
  ↓
Filial escolhida processa pedido
  ↓
Mercado retorna resultado → Restaurante
  ↓
Restaurante recebe produtos e processa pedido do cliente
```

Se nenhuma filial tiver estoque, o Mercado retorna falha ao restaurante.

