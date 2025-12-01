package implementacoes;

import interfaces.Filial;
import interfaces.MercadoServidor;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@WebService(
        endpointInterface = "interfaces.Filial"
)
public class FilialImpl implements Filial {
    // Identificação
    private final int id;
    private final String my_url;
    private final Random r;
    
    // Raft: Estados
    private enum Estado { FOLLOWER, CANDIDATE, LEADER }
    private volatile Estado estado = Estado.FOLLOWER;
    
    // Raft: Dados persistentes
    private volatile int termo = 0; // Termo atual
    private volatile Integer votedFor = null; // ID da filial que recebeu nosso voto neste termo
    private volatile int id_lider = -1; // ID do líder atual
    
    // Raft: Dados voláteis (resetados após eleição)
    private volatile long ultimoHeartbeat = System.currentTimeMillis();
    private volatile int votosRecebidos = 0;
    
    // Raft: Lista de todas as filiais (mesh topology)
    private List<Filial> todasFiliais = new ArrayList<>();
    private List<String> urlsFiliais = new ArrayList<>();
    private ReentrantLock lockFiliais = new ReentrantLock();
    
    // Conexão com o mercado (para enviar heartbeats)
    private MercadoServidor mercado = null;
    private static final String URL_MERCADO = "http://127.0.0.1:9000/mercado";
    
    // Estoque
    private Map<String, Integer> estoque;
    
    // Threads
    private Thread threadRaft; // Thread principal do Raft
    
    // Configuração Raft
    private static final int TIMEOUT_MIN = 150; // ms
    private static final int TIMEOUT_MAX = 300; // ms
    private static final int HEARTBEAT_INTERVAL = 50; // ms
    
    public FilialImpl(String my_url) {
        this.r = new Random();
        this.id = r.nextInt(2000);
        this.my_url = my_url;
        this.estoque = new HashMap<>();
        inicializarEstoque();
        
        System.out.println("Filial " + id + " inicializada (URL: " + my_url + ")");
        System.out.println("Estado inicial: FOLLOWER");
        
        // Inicia thread Raft
        iniciarRaft();
    }
    
    /**
     * Inicializa estoque lendo do cardápio
     */
    private void inicializarEstoque() {
        try (Scanner scanner = new Scanner(new File("src/cardapio/menu_restaurante.csv"))) {
            scanner.nextLine(); // Pula cabeçalho
            while (scanner.hasNextLine()) {
                String linha = scanner.nextLine();
                String[] partes = linha.split(",");
                if (partes.length >= 2) {
                    String nomeProduto = partes[1].trim();
                    estoque.put(nomeProduto, r.nextInt(6));
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Erro ao ler cardápio: " + e.getMessage());
            estoque.put("Pizza de Calabresa", r.nextInt(6));
            estoque.put("Macarronada", r.nextInt(6));
            estoque.put("Suco de Uva Integral", r.nextInt(6));
        }
        
        System.out.println("Filial " + id + " - Estoque inicializado:");
        estoque.forEach((produto, qtd) -> {
            if (qtd > 0) {
                System.out.println("  " + produto + ": " + qtd);
            }
        });
    }
    
    /**
     * Adiciona uma filial à lista de conhecidas (mesh topology)
     */
    public void adicionarFilial(Filial filial, String url) {
        lockFiliais.lock();
        try {
            if (!todasFiliais.contains(filial)) {
                todasFiliais.add(filial);
                urlsFiliais.add(url);
                System.out.println("Filial " + id + ": Adicionada filial " + filial.getId() + " à lista");
            }
        } finally {
            lockFiliais.unlock();
        }
    }
    
    /**
     * Inicia thread principal do Raft
     */
    private void iniciarRaft() {
        threadRaft = new Thread(() -> {
            while (true) {
                try {
                    if (estado == Estado.FOLLOWER) {
                        // Follower: aguarda heartbeat ou timeout
                        long timeout = TIMEOUT_MIN + r.nextInt(TIMEOUT_MAX - TIMEOUT_MIN);
                        long tempoEspera = timeout - (System.currentTimeMillis() - ultimoHeartbeat);
                        
                        if (tempoEspera > 0) {
                            Thread.sleep(tempoEspera);
                        }
                        
                        // Se não recebeu heartbeat, vira candidato
                        if (System.currentTimeMillis() - ultimoHeartbeat > timeout) {
                            System.out.println("Filial " + id + ": Timeout! Nenhum heartbeat recebido. Tornando-se CANDIDATE...");
                            tornarCandidato();
                        }
                    } else if (estado == Estado.CANDIDATE) {
                        // Candidate: já iniciou eleição, aguarda resultado
                        Thread.sleep(100);
                    } else if (estado == Estado.LEADER) {
                        // Leader: envia heartbeats periodicamente
                        Thread.sleep(HEARTBEAT_INTERVAL);
                        enviarHeartbeats();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Erro na thread Raft da filial " + id + ": " + e.getMessage());
                }
            }
        });
        threadRaft.setDaemon(true);
        threadRaft.start();
    }
    
    /**
     * Torna esta filial um candidato e inicia eleição
     */
    private void tornarCandidato() {
        lockFiliais.lock();
        try {
            estado = Estado.CANDIDATE;
            termo++;
            votedFor = id; // Vota em si mesmo
            votosRecebidos = 1; // Conta próprio voto
            ultimoHeartbeat = System.currentTimeMillis(); // Reset timer
            
            System.out.println("Filial " + id + ": Tornou-se CANDIDATE no termo " + termo);
            System.out.println("Filial " + id + ": Iniciando eleição...");
            
            // Solicita votos de todas as outras filiais
            int votosNecessarios = (todasFiliais.size() / 2) + 1; // Maioria
            
            for (Filial filial : todasFiliais) {
                if (filial.getId() != id) {
                    new Thread(() -> {
                        try {
                            boolean voto = filial.requestVote(termo, id, 0, 0);
                            if (voto) {
                                votosRecebidos++;
                                System.out.println("Filial " + id + ": Recebeu voto de " + filial.getId() + 
                                                 " (total: " + votosRecebidos + "/" + votosNecessarios + ")");
                                
                                if (votosRecebidos >= votosNecessarios && estado == Estado.CANDIDATE) {
                                    tornarLider();
                                }
                            }
                        } catch (Exception e) {
                            // Filial pode estar morta, ignora
                        }
                    }).start();
                }
            }
            
            // Se já tem maioria (caso de apenas 1 filial), vira líder imediatamente
            if (votosRecebidos >= votosNecessarios) {
                tornarLider();
            }
        } finally {
            lockFiliais.unlock();
        }
    }
    
    /**
     * Torna esta filial o líder
     */
    private void tornarLider() {
        if (estado != Estado.CANDIDATE) return;
        
        estado = Estado.LEADER;
        id_lider = id;
        votosRecebidos = 0;
        
        System.out.println("\n✓ Filial " + id + " é o novo LÍDER no termo " + termo + "!\n");
        
        // Conecta ao mercado para enviar heartbeats
        conectarMercado();
        
        // Notifica o mercado imediatamente
        notificarMercado();
        
        // Inicia envio de heartbeats
        enviarHeartbeats();
    }
    
    /**
     * Conecta ao mercado (apenas líder precisa).
     * Tenta reconectar se a conexão não existir ou se falhou anteriormente.
     */
    private void conectarMercado() {
        if (mercado == null) {
            try {
                URL wsdl = new URL(URL_MERCADO + "?wsdl");
                QName qname = new QName("http://implementacoes/", "MercadoServidorImplService");
                Service service = Service.create(wsdl, qname);
                mercado = service.getPort(MercadoServidor.class);
                // A conexão será testada quando tentarmos notificar
                System.out.println("Filial " + id + " (LÍDER): Tentando conectar ao mercado...");
            } catch (Exception e) {
                // Não loga erro aqui para evitar spam - será logado em notificarMercado se persistir
                mercado = null;
            }
        }
    }
    
    /**
     * Notifica o mercado sobre a liderança.
     * Tenta reconectar automaticamente se a conexão falhar.
     */
    private void notificarMercado() {
        // Se não tem conexão, tenta conectar
        if (mercado == null) {
            conectarMercado();
        }
        
        // Se ainda não tem conexão após tentar, loga e retorna
        if (mercado == null) {
            // Só loga ocasionalmente para evitar spam (a cada 10 tentativas aproximadamente)
            if (System.currentTimeMillis() % 10000 < 100) {
                System.out.println("Filial " + id + " (LÍDER): Aguardando mercado ficar disponível...");
            }
            return;
        }
        
        // Tenta notificar o mercado
        try {
            mercado.notificarLider(termo, id);
        } catch (Exception e) {
            // Mercado pode estar morto ou não disponível, tenta reconectar
            System.out.println("Filial " + id + " (LÍDER): Conexão com mercado perdida. Tentando reconectar...");
            mercado = null;
            conectarMercado();
            
            // Se conseguiu reconectar, tenta notificar novamente
            if (mercado != null) {
                try {
                    mercado.notificarLider(termo, id);
                    System.out.println("Filial " + id + " (LÍDER): ✓ Reconectado ao mercado com sucesso!");
                } catch (Exception e2) {
                    // Ainda não conseguiu, mercado pode estar realmente morto
                    mercado = null;
                }
            }
        }
    }
    
    /**
     * Envia heartbeats para todos os seguidores e para o mercado (apenas líder)
     */
    private void enviarHeartbeats() {
        if (estado != Estado.LEADER) return;
        
        // Envia heartbeats para filiais seguidoras
        lockFiliais.lock();
        try {
            for (Filial filial : todasFiliais) {
                if (filial.getId() != id) {
                    new Thread(() -> {
                        try {
                            filial.appendEntries(termo, id, 0, 0, new String[0], 0);
                        } catch (Exception e) {
                            // Seguidor pode estar morto, ignora
                        }
                    }).start();
                }
            }
        } finally {
            lockFiliais.unlock();
        }
        
        // Envia heartbeat para o mercado
        notificarMercado();
    }
    
    // ========== Métodos da Interface Filial ==========
    
    @WebMethod
    @Override
    public int getId() {
        return id;
    }
    
    @WebMethod
    @Override
    public int getLider() {
        return id_lider;
    }
    
    @WebMethod
    @Override
    public int getTermo() {
        return termo;
    }
    
    @WebMethod
    @Override
    public String getEstado() {
        return estado.toString();
    }
    
    /**
     * Raft RequestVote RPC
     * Candidato solicita voto de um seguidor
     */
    @WebMethod
    @Override
    public boolean requestVote(int termo, int candidatoId, int lastLogIndex, int lastLogTerm) {
        synchronized (this) {
            // Se termo é maior, atualiza e vira follower
            if (termo > this.termo) {
                this.termo = termo;
                this.estado = Estado.FOLLOWER;
                this.votedFor = null;
                this.id_lider = -1;
                System.out.println("Filial " + id + ": Atualizou termo para " + termo + " (recebeu RequestVote de " + candidatoId + ")");
            }
            
            // Vota se:
            // 1. Termo do candidato >= nosso termo
            // 2. Não votamos neste termo OU já votamos neste candidato
            boolean podeVotar = (termo >= this.termo) && 
                               (votedFor == null || votedFor == candidatoId);
            
            if (podeVotar) {
                votedFor = candidatoId;
                ultimoHeartbeat = System.currentTimeMillis(); // Reset timer
                System.out.println("Filial " + id + ": Votou em " + candidatoId + " no termo " + termo);
                return true;
            }
            
            return false;
        }
    }
    
    /**
     * Raft AppendEntries RPC (heartbeat)
     * Líder envia heartbeat para seguidores
     */
    @WebMethod
    @Override
    public boolean appendEntries(int termo, int liderId, int prevLogIndex, int prevLogTerm, 
                                 String[] entries, int leaderCommit) {
        synchronized (this) {
            // Se termo é maior, atualiza e vira follower
            if (termo > this.termo) {
                this.termo = termo;
                this.estado = Estado.FOLLOWER;
                this.votedFor = null;
                System.out.println("Filial " + id + ": Atualizou termo para " + termo + " (recebeu heartbeat de " + liderId + ")");
            }
            
            // Se recebeu heartbeat do líder atual ou de um líder com termo maior
            if (termo >= this.termo) {
                int liderAnterior = this.id_lider;
                this.id_lider = liderId;
                this.estado = Estado.FOLLOWER;
                this.ultimoHeartbeat = System.currentTimeMillis();
                
                // Log quando o líder muda
                if (liderAnterior != liderId && liderAnterior != -1) {
                    System.out.println("Filial " + id + " (FOLLOWER): Líder mudou! Novo líder: " + liderId + 
                                     " (anterior: " + liderAnterior + ", termo: " + termo + ")");
                } else if (liderAnterior == -1 && liderId != -1) {
                    System.out.println("Filial " + id + " (FOLLOWER): Líder eleito! Novo líder: " + liderId + " (termo: " + termo + ")");
                }
                
                // Se era candidato, volta a ser follower
                if (this.estado == Estado.CANDIDATE) {
                    System.out.println("Filial " + id + ": Recebeu heartbeat de líder " + liderId + ". Voltando a ser FOLLOWER");
                }
                
                return true;
            }
            
            return false;
        }
    }
    
    /**
     * Solicita produtos (chamado pelo Mercado)
     * Apenas o líder processa; seguidores redirecionam
     */
    @WebMethod
    @Override
    public boolean solicitarProdutos(String[] produtos) throws MalformedURLException {
        System.out.println("\n=== Filial " + id + " recebeu pedido: " + Arrays.toString(produtos));
        
        // Se não é líder, redireciona para o líder
        if (estado != Estado.LEADER) {
            if (id_lider == -1) {
                System.err.println("Filial " + id + ": Não há líder conhecido. Aguardando eleição...");
                return false;
            }
            
            // Tenta encontrar e redirecionar para o líder
            lockFiliais.lock();
            try {
                for (Filial filial : todasFiliais) {
                    if (filial.getId() == id_lider) {
                        System.out.println("Filial " + id + ": Redirecionando pedido para líder " + id_lider);
                        return filial.solicitarProdutos(produtos);
                    }
                }
            } finally {
                lockFiliais.unlock();
            }
            
            System.err.println("Filial " + id + ": Líder " + id_lider + " não encontrado");
            return false;
        }
        
        // Líder coordena consenso complexo: consulta cada filial e coleta produtos
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Filial " + id + " (LÍDER): Coordenando consenso para atender pedido...");
        
        // Conta quantos de cada produto são necessários
        Map<String, Integer> produtosNecessarios = new HashMap<>();
        for (String produto : produtos) {
            produtosNecessarios.put(produto, produtosNecessarios.getOrDefault(produto, 0) + 1);
        }
        
        System.out.println("Filial " + id + " (LÍDER): Produtos necessários:");
        for (Map.Entry<String, Integer> entry : produtosNecessarios.entrySet()) {
            System.out.println("  - " + entry.getKey() + ": " + entry.getValue() + " unidade(s)");
        }
        System.out.println("=".repeat(60));
        
        // Lista de produtos que ainda precisamos
        List<String> produtosRestantes = new ArrayList<>(Arrays.asList(produtos));
        Map<Integer, List<String>> produtosPorFilial = new HashMap<>(); // Filial -> produtos que ela vai fornecer
        
        // Ordena filiais por média de estoque (maior média primeiro)
        List<Filial> filiaisOrdenadas = new ArrayList<>();
        lockFiliais.lock();
        try {
            filiaisOrdenadas.addAll(todasFiliais);
        } finally {
            lockFiliais.unlock();
        }
        
        // Ordena por média de estoque (decrescente)
        filiaisOrdenadas.sort((f1, f2) -> {
            try {
                double media1 = f1.calcularMediaEstoque();
                double media2 = f2.calcularMediaEstoque();
                return Double.compare(media2, media1); // Maior primeiro
            } catch (Exception e) {
                return 0;
            }
        });
        
        System.out.println("Filial " + id + " (LÍDER): Filiais ordenadas por média de estoque:");
        for (Filial f : filiaisOrdenadas) {
            try {
                System.out.println("  - Filial " + f.getId() + ": média " + String.format("%.2f", f.calcularMediaEstoque()));
            } catch (Exception e) {
                // Ignora filiais mortas
            }
        }
        
        // Consulta cada filial (em ordem) e coleta produtos disponíveis
        for (Filial filial : filiaisOrdenadas) {
            if (produtosRestantes.isEmpty()) {
                break; // Já temos todos os produtos
            }
            
            try {
                // Pergunta quais produtos esta filial tem disponível
                String[] produtosDisponiveis = filial.consultarProdutosDisponiveis(
                    produtosRestantes.toArray(new String[0])
                );
                
                if (produtosDisponiveis != null && produtosDisponiveis.length > 0) {
                    List<String> produtosDestaFilial = Arrays.asList(produtosDisponiveis);
                    produtosPorFilial.put(filial.getId(), produtosDestaFilial);
                    
                    // Conta quantos de cada produto esta filial tem
                    Map<String, Integer> produtosFilial = new HashMap<>();
                    for (String p : produtosDisponiveis) {
                        produtosFilial.put(p, produtosFilial.getOrDefault(p, 0) + 1);
                    }
                    
                    System.out.println("Filial " + id + " (LÍDER): ✓ Filial " + filial.getId() + " tem disponível:");
                    for (Map.Entry<String, Integer> entry : produtosFilial.entrySet()) {
                        System.out.println("    - " + entry.getKey() + ": " + entry.getValue() + " unidade(s)");
                    }
                    
                    // Remove os produtos que esta filial vai fornecer
                    produtosRestantes.removeAll(produtosDestaFilial);
                    
                    // Mostra o que ainda falta
                    if (!produtosRestantes.isEmpty()) {
                        Map<String, Integer> aindaFalta = new HashMap<>();
                        for (String p : produtosRestantes) {
                            aindaFalta.put(p, aindaFalta.getOrDefault(p, 0) + 1);
                        }
                        System.out.println("Filial " + id + " (LÍDER): Ainda faltam:");
                        for (Map.Entry<String, Integer> entry : aindaFalta.entrySet()) {
                            System.out.println("    - " + entry.getKey() + ": " + entry.getValue() + " unidade(s)");
                        }
                    }
                } else {
                    System.out.println("Filial " + id + " (LÍDER): Filial " + filial.getId() + " não tem produtos disponíveis");
                }
            } catch (Exception e) {
                // Filial pode estar morta, continua
                System.err.println("Filial " + id + " (LÍDER): Erro ao consultar filial " + filial.getId() + ": " + e.getMessage());
            }
        }
        
        // Verifica se conseguimos todos os produtos
        if (!produtosRestantes.isEmpty()) {
            System.out.println("Filial " + id + " (LÍDER): ❌ Não foi possível obter todos os produtos.");
            
            // Conta quantos de cada produto faltam
            Map<String, Integer> produtosFaltando = new HashMap<>();
            for (String produto : produtosRestantes) {
                produtosFaltando.put(produto, produtosFaltando.getOrDefault(produto, 0) + 1);
            }
            
            System.out.println("Filial " + id + " (LÍDER): Produtos faltando:");
            for (Map.Entry<String, Integer> entry : produtosFaltando.entrySet()) {
                System.out.println("  - " + entry.getKey() + ": " + entry.getValue() + " unidade(s)");
            }
            return false;
        }
        
        // Processa pedido em cada filial que vai contribuir
        System.out.println("Filial " + id + " (LÍDER): Distribuindo pedido entre " + produtosPorFilial.size() + " filiais...");
        boolean todosSucesso = true;
        
        for (Map.Entry<Integer, List<String>> entry : produtosPorFilial.entrySet()) {
            int filialId = entry.getKey();
            List<String> produtosFilial = entry.getValue();
            
            lockFiliais.lock();
            try {
                for (Filial filial : todasFiliais) {
                    if (filial.getId() == filialId) {
                        System.out.println("Filial " + id + " (LÍDER): Processando " + produtosFilial.size() + 
                                         " produtos na filial " + filialId);
                        boolean sucesso = filial.processarProdutosEspecificos(
                            produtosFilial.toArray(new String[0]), 
                            termo, 
                            id
                        );
                        if (sucesso) {
                            System.out.println("Filial " + id + " (LÍDER): ✓ Filial " + filialId + " processou com sucesso");
                        } else {
                            System.out.println("Filial " + id + " (LÍDER): ✗ Filial " + filialId + " falhou ao processar");
                            todosSucesso = false;
                        }
                        break;
                    }
                }
            } finally {
                lockFiliais.unlock();
            }
        }
        
        if (todosSucesso) {
            System.out.println("Filial " + id + " (LÍDER): ✓ Pedido processado com sucesso por todas as filiais!\n");
        } else {
            System.out.println("Filial " + id + " (LÍDER): ✗ Algumas filiais falharam ao processar\n");
        }
        
        return todosSucesso;
    }
    
    @WebMethod
    @Override
    public boolean temEstoque(String[] produtos) {
        for (String produto : produtos) {
            int quantidade = estoque.getOrDefault(produto, 0);
            if (quantidade <= 0) {
                return false;
            }
        }
        return true;
    }
    
    @WebMethod
    @Override
    public boolean processarPedido(String[] produtos) {
        if (!temEstoque(produtos)) {
            return false;
        }
        
        for (String produto : produtos) {
            estoque.compute(produto, (k, v) -> (v == null) ? 0 : v - 1);
        }
        
        System.out.println("Filial " + id + " processou pedido. Estoque atualizado.");
        return true;
    }
    
    @WebMethod
    @Override
    public int consultarEstoque(String produto) {
        return estoque.getOrDefault(produto, 0);
    }
    
    /**
     * Consulta quais produtos de uma lista estão disponíveis nesta filial
     */
    @WebMethod
    @Override
    public String[] consultarProdutosDisponiveis(String[] produtos) {
        List<String> disponiveis = new ArrayList<>();
        for (String produto : produtos) {
            int quantidade = estoque.getOrDefault(produto, 0);
            if (quantidade > 0) {
                disponiveis.add(produto);
            }
        }
        return disponiveis.toArray(new String[0]);
    }
    
    /**
     * Calcula a média de estoque (soma de todas as quantidades / número de produtos)
     */
    @WebMethod
    @Override
    public double calcularMediaEstoque() {
        if (estoque.isEmpty()) {
            return 0.0;
        }
        int soma = 0;
        for (int quantidade : estoque.values()) {
            soma += quantidade;
        }
        return (double) soma / estoque.size();
    }
    
    /**
     * Processa apenas os produtos específicos fornecidos (chamado pelo líder)
     */
    @WebMethod
    @Override
    public boolean processarProdutosEspecificos(String[] produtos, int termo, int liderId) {
        // Verifica se o termo e líder são válidos
        if (termo < this.termo || (this.id_lider != -1 && this.id_lider != liderId)) {
            System.err.println("Filial " + id + ": Rejeitou pedido - termo/líder inválido");
            return false;
        }
        
        // Mostra estoque ANTES
        System.out.println("\n📦 Filial " + id + " - Estoque ANTES do processamento:");
        Map<String, Integer> estoqueAntes = new HashMap<>();
        for (String produto : produtos) {
            int qtd = estoque.getOrDefault(produto, 0);
            estoqueAntes.put(produto, qtd);
            System.out.println("  - " + produto + ": " + qtd + " unidade(s)");
        }
        
        // Verifica se tem estoque dos produtos solicitados
        if (!temEstoque(produtos)) {
            System.err.println("Filial " + id + ": ❌ Não tem estoque suficiente dos produtos solicitados");
            return false;
        }
        
        // Processa apenas os produtos fornecidos
        for (String produto : produtos) {
            estoque.compute(produto, (k, v) -> (v == null) ? 0 : v - 1);
        }
        
        // Mostra estoque DEPOIS
        System.out.println("📦 Filial " + id + " - Estoque DEPOIS do processamento:");
        for (String produto : produtos) {
            int qtdAntes = estoqueAntes.get(produto);
            int qtdDepois = estoque.getOrDefault(produto, 0);
            System.out.println("  - " + produto + ": " + qtdAntes + " → " + qtdDepois + " unidade(s)");
        }
        
        System.out.println("✓ Filial " + id + " processou " + produtos.length + " produto(s): " + Arrays.toString(produtos) + "\n");
        return true;
    }
}
