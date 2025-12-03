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
    
    // Bully: Estados
    private enum Estado { NORMAL, ELECTION, COORDINATOR } // COORDINATOR = líder
    private volatile Estado estado = Estado.NORMAL;
    
    // Bully: Dados
    private volatile int id_lider = -1; // ID do líder atual (coordenador)
    private volatile long ultimoHeartbeat = System.currentTimeMillis();
    private volatile boolean aguardandoRespostaEleicao = false; // Se está aguardando resposta de eleição
    private volatile long ultimaEleicaoIniciada = 0; // Timestamp da última eleição iniciada
    private static final long COOLDOWN_ELEICAO = 1000; // 1 segundo entre eleições
    
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
    private Thread threadBully; // Thread principal do Bully
    
    // Configuração Bully
    private static final int TIMEOUT_HEARTBEAT = 300; // ms - timeout para detectar líder morto
    private static final int HEARTBEAT_INTERVAL = 100; // ms - intervalo entre heartbeats do líder

    public FilialImpl(String my_url) {
        this.r = new Random();
        this.id = r.nextInt(2000);
        this.my_url = my_url;
        this.estoque = new HashMap<>();
        inicializarEstoque();
        
        System.out.println("Filial " + id + " inicializada (URL: " + my_url + ")");
        System.out.println("Estado inicial: NORMAL");
        
        // Inicia thread Bully
        iniciarBully();
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
     * Inicia thread principal do Bully
     */
    private void iniciarBully() {
        threadBully = new Thread(() -> {
            while (true) {
                try {
                    if (estado == Estado.NORMAL) {
                        // Normal: aguarda heartbeat do líder ou timeout
                        long tempoDesdeUltimoHeartbeat = System.currentTimeMillis() - ultimoHeartbeat;
                        
                        if (tempoDesdeUltimoHeartbeat > TIMEOUT_HEARTBEAT) {
                            // Líder morreu ou não está respondendo - inicia eleição
                            System.out.println("Filial " + id + ": Timeout! Nenhum heartbeat recebido do líder. Iniciando eleição...");
                            iniciarEleicao();
                        } else {
                            // Aguarda até próximo timeout
                            Thread.sleep(TIMEOUT_HEARTBEAT - tempoDesdeUltimoHeartbeat);
                        }
                    } else if (estado == Estado.ELECTION) {
                        // Em eleição: aguarda resultado
                        Thread.sleep(100);
                    } else if (estado == Estado.COORDINATOR) {
                        // Coordenador (líder): envia heartbeats periodicamente
                        Thread.sleep(HEARTBEAT_INTERVAL);
                        enviarHeartbeats();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Erro na thread Bully da filial " + id + ": " + e.getMessage());
                }
            }
        });
        threadBully.setDaemon(true);
        threadBully.start();
    }
    
    /**
     * Inicia eleição Bully: envia election para todas as filiais com ID maior
     */
    private void iniciarEleicao() {
        synchronized (this) {
            // Verifica novamente se já é coordenador ou já está em eleição
            if (estado == Estado.COORDINATOR) {
                System.out.println("Filial " + id + ": Tentou iniciar eleição mas já é coordenador. Ignorando.");
                return; // Já é líder, não precisa eleger
            }
            
            // Verifica cooldown
            long tempoDesdeUltimaEleicao = System.currentTimeMillis() - ultimaEleicaoIniciada;
            if (estado == Estado.ELECTION && tempoDesdeUltimaEleicao < COOLDOWN_ELEICAO) {
                System.out.println("Filial " + id + ": Tentou iniciar eleição mas já está em eleição (cooldown ativo). Ignorando.");
                return; // Já está em eleição recentemente
            }
            
            estado = Estado.ELECTION;
            aguardandoRespostaEleicao = false;
            ultimaEleicaoIniciada = System.currentTimeMillis();
        }
        
        System.out.println("Filial " + id + ": Iniciando eleição Bully...");
        
        // Remove filiais mortas antes de iniciar eleição
        removerFiliaisMortas();
        
        // Envia election para todas as filiais com ID maior
        lockFiliais.lock();
        try {
            List<Filial> filiaisParaEleicao = new ArrayList<>(todasFiliais);
            
            for (Filial filial : filiaisParaEleicao) {
                try {
                    int filialId = filial.getId();
                    if (filialId <= id) {
                        continue; // Só envia para filiais com ID maior
                    }
                    
                    // Envia election em thread separada
                    final int filialIdFinal = filialId;
                    new Thread(() -> {
                        try {
                            boolean resposta = filial.election(id);
                            if (resposta) {
                                synchronized (FilialImpl.this) {
                                    aguardandoRespostaEleicao = true;
                                    // Se recebeu resposta, significa que essa filial tem ID maior e pode ser coordenador
                                    // Atualiza o líder e o heartbeat para evitar timeout imediato
                                    if (filialIdFinal > id) {
                                        id_lider = filialIdFinal;
                                        ultimoHeartbeat = System.currentTimeMillis();
                                        System.out.println("Filial " + id + ": Recebeu resposta de eleição da filial " + filialIdFinal + 
                                                         " (ID maior). Atualizando líder e heartbeat.");
                                    } else {
                                        System.out.println("Filial " + id + ": Recebeu resposta de eleição da filial " + filialIdFinal);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Filial pode estar morta, ignora
                        }
                    }).start();
                } catch (Exception e) {
                    // Filial morta, continua
                }
            }
        } finally {
            lockFiliais.unlock();
        }
        
        // Aguarda um pouco para receber respostas
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Se não recebeu nenhuma resposta, esta filial é a de maior ID - vira líder
        synchronized (this) {
            if (estado == Estado.ELECTION && !aguardandoRespostaEleicao) {
                System.out.println("Filial " + id + ": Não recebeu resposta de nenhuma filial com ID maior. Tornando-se COORDINATOR (líder)!");
                tornarCoordenador();
            } else if (estado == Estado.ELECTION) {
                // Recebeu resposta, aguarda mensagem de coordinator
                // Mas se já atualizou o líder acima, pode já ter o líder correto
                if (id_lider != -1) {
                    System.out.println("Filial " + id + ": Recebeu resposta de eleição. Líder atualizado para " + id_lider + 
                                     ". Solicitando coordinator e aguardando heartbeat...");
                    // Solicita explicitamente que o coordenador envie coordinator
                    lockFiliais.lock();
                    try {
                        for (Filial filial : todasFiliais) {
                            try {
                                if (filial.getId() == id_lider) {
                                    // Solicita coordinator de forma síncrona para garantir recebimento
                                    try {
                                        filial.coordinator(id_lider);
                                        System.out.println("Filial " + id + ": Solicitou e recebeu coordinator de " + id_lider);
                                    } catch (Exception e) {
                                        // Tenta assíncrono como fallback
                                        new Thread(() -> {
                                            try {
                                                filial.coordinator(id_lider);
                                            } catch (Exception e2) {
                                                // Ignora
                                            }
                                        }).start();
                                    }
                                    break;
                                }
                            } catch (Exception e) {
                                // Continua
                            }
                        }
                    } finally {
                        lockFiliais.unlock();
                    }
                } else {
                    System.out.println("Filial " + id + ": Recebeu resposta de eleição. Aguardando mensagem de coordinator...");
                }
                estado = Estado.NORMAL;
            }
        }
    }
    
    /**
     * Remove filiais mortas da lista
     */
    private void removerFiliaisMortas() {
        lockFiliais.lock();
        try {
            List<Filial> filiaisParaRemover = new ArrayList<>();
            List<String> urlsParaRemover = new ArrayList<>();
            
            for (int i = 0; i < todasFiliais.size(); i++) {
                Filial filial = todasFiliais.get(i);
                String url = urlsFiliais.get(i);
                
                try {
                    int filialId = filial.getId();
                    if (filialId == id) {
                        continue; // Pula própria filial
                    }
                    // Se chegou aqui, filial está viva
                } catch (Exception e) {
                    // Filial está morta - marca para remover
                    filiaisParaRemover.add(filial);
                    urlsParaRemover.add(url);
                }
            }
            
            // Remove filiais mortas
            for (Filial filial : filiaisParaRemover) {
                todasFiliais.remove(filial);
            }
            for (String url : urlsParaRemover) {
                urlsFiliais.remove(url);
                System.out.println("Filial " + id + ": Removida filial morta da lista (URL: " + url + ")");
            }
        } catch (Exception e) {
            // Erro ao remover filiais mortas, ignora para não quebrar o sistema
        } finally {
            lockFiliais.unlock();
        }
    }
    
    /**
     * Torna esta filial o coordenador (líder) - algoritmo Bully
     */
    private synchronized void tornarCoordenador() {
        if (estado == Estado.COORDINATOR) {
            return; // Já é coordenador
        }
        
        estado = Estado.COORDINATOR;
        id_lider = id;
        
        System.out.println("\n✓ Filial " + id + " é o novo COORDENADOR (líder)!\n");
        
        // Remove filiais mortas antes de enviar heartbeats
        removerFiliaisMortas();
        
        // Conecta ao mercado
        conectarMercado();
        
        // Notifica todas as outras filiais que somos o novo coordenador (IMPORTANTE!)
        notificarNovoCoordenador();
        
        // Envia heartbeats imediatamente para garantir que outras filiais saibam
        enviarHeartbeats();
        
        // Notifica o mercado
        notificarMercado();
    }
    
    /**
     * Notifica todas as outras filiais que somos o novo coordenador
     */
    private void notificarNovoCoordenador() {
        lockFiliais.lock();
        try {
            List<Filial> filiaisParaNotificar = new ArrayList<>(todasFiliais);
            
            System.out.println("Filial " + id + " (COORDENADOR): Notificando " + (filiaisParaNotificar.size() - 1) + " filiais sobre novo coordenador...");
            
            for (Filial filial : filiaisParaNotificar) {
                try {
                    int filialId = filial.getId();
                    if (filialId != id) {
                        // Notifica de forma SÍNCRONA (bloqueante) para garantir que todas recebam
                        try {
                            filial.coordinator(id);
                            System.out.println("Filial " + id + " (COORDENADOR): Notificou filial " + filialId);
                        } catch (Exception e) {
                            // Filial pode estar morta, tenta assíncrono como fallback
                            new Thread(() -> {
                                try {
                                    filial.coordinator(id);
                                } catch (Exception e2) {
                                    // Ignora
                                }
                            }).start();
                        }
                    }
                } catch (Exception e) {
                    // Filial morta, continua
                }
            }
        } finally {
            lockFiliais.unlock();
        }
    }
    
    /**
     * Conecta ao mercado (apenas coordenador precisa)
     */
    private void conectarMercado() {
        if (mercado == null) {
            try {
                URL wsdl = new URL(URL_MERCADO + "?wsdl");
                QName qname = new QName("http://implementacoes/", "MercadoServidorImplService");
                Service service = Service.create(wsdl, qname);
                mercado = service.getPort(MercadoServidor.class);
            } catch (Exception e) {
                mercado = null;
            }
        }
    }
    
    /**
     * Notifica o mercado sobre a liderança (apenas coordenador)
     */
    private void notificarMercado() {
        if (estado != Estado.COORDINATOR) {
            return; // Não é coordenador, não precisa notificar
        }
        
        if (mercado == null) {
            conectarMercado();
        }
        
        if (mercado != null) {
            try {
                // Usa um termo fixo (0) já que Bully não usa termos
                mercado.notificarLider(0, id);
            } catch (Exception e) {
                mercado = null;
            }
        }
    }
    
    /**
     * Envia heartbeats para todas as outras filiais e para o mercado (apenas coordenador)
     */
    private void enviarHeartbeats() {
        if (estado != Estado.COORDINATOR) return;
        
        // Envia heartbeats para outras filiais (usando appendEntries para compatibilidade)
        lockFiliais.lock();
        try {
            List<Filial> filiaisParaHeartbeat = new ArrayList<>(todasFiliais);
            for (Filial filial : filiaisParaHeartbeat) {
                try {
                    int filialId = filial.getId();
                    if (filialId != id) {
                        // Usa appendEntries com termo 0 (Bully não usa termos)
                        filial.appendEntries(0, id, 0, 0, new String[0], 0);
                    }
                } catch (Exception e) {
                    // Filial pode estar morta, ignora
                }
            }
        } finally {
            lockFiliais.unlock();
        }
        
        // Notifica o mercado
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
        // Bully não usa termos, retorna 0 para compatibilidade
        return 0;
    }

    @WebMethod
    @Override
    public String getEstado() {
        // Converte estados Bully para compatibilidade com interface
        if (estado == Estado.COORDINATOR) {
            return "LEADER";
        } else if (estado == Estado.ELECTION) {
            return "CANDIDATE";
        } else {
            return "FOLLOWER";
        }
    }
    
    /**
     * Raft RequestVote RPC - MANTIDO PARA COMPATIBILIDADE
     * No Bully, sempre retorna false (não usa votos)
     */
    @WebMethod
    @Override
    public boolean requestVote(int termo, int candidatoId, int lastLogIndex, int lastLogTerm) {
        // Bully não usa votos, sempre retorna false
        return false;
    }
    
    /**
     * Bully: Election RPC - recebe mensagem de eleição de filial com ID menor
     */
    @WebMethod
    @Override
    public boolean election(int candidatoId) {
        synchronized (this) {
            // Se o candidato tem ID menor, responde OK
            if (candidatoId < id) {
                // Se é coordenador, tenta descobrir e adicionar a filial candidata à lista
                if (estado == Estado.COORDINATOR) {
                    descobrirEAdicionarFilial(candidatoId);
                }
                
                // Verifica se já está em eleição ou se iniciou eleição recentemente (cooldown)
                long tempoDesdeUltimaEleicao = System.currentTimeMillis() - ultimaEleicaoIniciada;
                boolean podeIniciarEleicao = (estado != Estado.ELECTION && estado != Estado.COORDINATOR) && 
                                             (tempoDesdeUltimaEleicao > COOLDOWN_ELEICAO);
                
                if (podeIniciarEleicao) {
                    System.out.println("Filial " + id + ": Recebeu election de filial " + candidatoId + " (ID menor). Respondendo OK e iniciando própria eleição.");
                    ultimaEleicaoIniciada = System.currentTimeMillis();
                    
                    // Inicia própria eleição em thread separada
                    new Thread(() -> {
                        iniciarEleicao();
                    }).start();
                } else {
                    // Já está em eleição ou acabou de iniciar uma - apenas responde OK
                    if (estado == Estado.ELECTION) {
                        System.out.println("Filial " + id + ": Recebeu election de filial " + candidatoId + " (ID menor). Já está em eleição, apenas respondendo OK.");
                    } else if (estado == Estado.COORDINATOR) {
                        System.out.println("Filial " + id + ": Recebeu election de filial " + candidatoId + " (ID menor). Já é coordenador, apenas respondendo OK.");
                    } else {
                        System.out.println("Filial " + id + ": Recebeu election de filial " + candidatoId + " (ID menor). Cooldown ativo, apenas respondendo OK.");
                    }
                }
                
                return true; // Responde OK
            }
            
            // Se o candidato tem ID maior ou igual, não responde (ou responde false)
            return false;
        }
    }
    
    /**
     * Tenta descobrir e adicionar uma filial à lista conhecida
     */
    private void descobrirEAdicionarFilial(int filialId) {
        lockFiliais.lock();
        try {
            // Verifica se já conhece esta filial
            boolean jaConhece = false;
            for (Filial filial : todasFiliais) {
                try {
                    if (filial != null && filial.getId() == filialId) {
                        jaConhece = true;
                        break;
                    }
                } catch (Exception e) {
                    // Filial morta, continua
                }
            }
            
            if (jaConhece) {
                return; // Já conhece, não precisa descobrir
            }
            
            // Tenta descobrir a filial consultando as URLs conhecidas
            String[] urlsConhecidas = {
                "http://127.0.0.1:9876/filial",
                "http://127.0.0.1:9875/filial",
                "http://127.0.0.1:9874/filial"
            };
            
            for (String url : urlsConhecidas) {
                if (url.equals(my_url)) continue; // Pula própria URL
                if (urlsFiliais.contains(url)) continue; // Já está na lista
                
                try {
                    java.net.URL wsdl = new java.net.URL(url + "?wsdl");
                    javax.xml.namespace.QName qname = new javax.xml.namespace.QName("http://implementacoes/", "FilialImplService");
                    javax.xml.ws.Service service = javax.xml.ws.Service.create(wsdl, qname);
                    Filial filial = service.getPort(Filial.class);
                    
                    // Verifica se é a filial que estamos procurando
                    if (filial.getId() == filialId) {
                        adicionarFilial(filial, url);
                        System.out.println("Filial " + id + " (COORDENADOR): Descobriu e adicionou filial " + filialId + " à lista (URL: " + url + ")");
                        break;
                    }
                } catch (Exception e) {
                    // Não é esta URL ou filial não está disponível, continua
                }
            }
        } finally {
            lockFiliais.unlock();
        }
    }
    
    /**
     * Bully: Coordinator RPC - recebe notificação de novo coordenador
     */
    @WebMethod
    @Override
    public void coordinator(int coordenadorId) {
        synchronized (this) {
            if (coordenadorId != id) {
                int liderAnterior = this.id_lider;
                this.id_lider = coordenadorId;
                this.estado = Estado.NORMAL;
                this.ultimoHeartbeat = System.currentTimeMillis();
                
                // Para qualquer eleição em andamento
                if (this.estado == Estado.ELECTION) {
                    this.estado = Estado.NORMAL;
                    System.out.println("Filial " + id + ": Recebeu coordinator durante eleição. Parando eleição.");
                }
                
                if (liderAnterior != coordenadorId) {
                    System.out.println("Filial " + id + ": Novo coordenador eleito! Filial " + coordenadorId + 
                                     (liderAnterior != -1 ? " (anterior: " + liderAnterior + ")" : ""));
                } else {
                    // Mesmo coordenador, apenas atualiza heartbeat
                    System.out.println("Filial " + id + ": Recebeu coordinator de " + coordenadorId + ". Atualizando heartbeat.");
                }
            }
        }
    }
    
    /**
     * Raft AppendEntries RPC (heartbeat) - MANTIDO PARA COMPATIBILIDADE
     * Agora usado apenas como heartbeat simples do Bully
     */
    @WebMethod
    @Override
    public boolean appendEntries(int termo, int liderId, int prevLogIndex, int prevLogTerm, 
                                 String[] entries, int leaderCommit) {
        synchronized (this) {
            // Bully: atualiza líder e heartbeat
            if (liderId != id) {
                int liderAnterior = this.id_lider;
                boolean estavaEmEleicao = (this.estado == Estado.ELECTION);
                
                this.id_lider = liderId;
                this.estado = Estado.NORMAL;
                this.ultimoHeartbeat = System.currentTimeMillis();
                
                // Tenta descobrir e adicionar o coordenador à lista se ainda não estiver
                descobrirEAdicionarFilial(liderId);
                
                if (liderAnterior != liderId && liderAnterior != -1) {
                    System.out.println("Filial " + id + ": Recebeu heartbeat de coordenador " + liderId + 
                                     " (anterior: " + liderAnterior + ")");
                } else if (liderAnterior == -1 && liderId != -1) {
                    System.out.println("Filial " + id + ": Recebeu heartbeat de coordenador " + liderId);
                } else if (liderAnterior == liderId) {
                    // Mesmo coordenador - heartbeat periódico (não loga para evitar spam, mas atualiza heartbeat)
                    // Log apenas ocasionalmente para debug
                    if (System.currentTimeMillis() % 5000 < 100) {
                        System.out.println("Filial " + id + ": Recebeu heartbeat periódico de coordenador " + liderId);
                    }
                }
                
                // Se estava em eleição, para a eleição
                if (estavaEmEleicao) {
                    System.out.println("Filial " + id + ": Recebeu heartbeat durante eleição. Parando eleição.");
                }
            }
            
            return true;
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
        
        // Se não é coordenador, redireciona para o coordenador
        if (estado != Estado.COORDINATOR) {
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
        StringBuilder separador = new StringBuilder();
        for (int i = 0; i < 60; i++) separador.append("=");
        System.out.println("\n" + separador.toString());
        System.out.println("Filial " + id + " (COORDENADOR): Coordenando consenso para atender pedido...");
        
        // Conta quantos de cada produto são necessários
        Map<String, Integer> produtosNecessarios = new HashMap<>();
        for (String produto : produtos) {
            produtosNecessarios.put(produto, produtosNecessarios.getOrDefault(produto, 0) + 1);
        }
        
        System.out.println("Filial " + id + " (LÍDER): Produtos necessários:");
        for (Map.Entry<String, Integer> entry : produtosNecessarios.entrySet()) {
            System.out.println("  - " + entry.getKey() + ": " + entry.getValue() + " unidade(s)");
        }
        System.out.println(separador.toString());
        
        // Lista de produtos que ainda precisamos
        List<String> produtosRestantes = new ArrayList<>(Arrays.asList(produtos));
        Map<Integer, List<String>> produtosPorFilial = new HashMap<>(); // Filial -> produtos que ela vai fornecer
        
        // Ordena filiais por média de estoque (maior média primeiro)
        // INCLUI a própria filial líder na lista para consultar seu próprio estoque
        List<Filial> filiaisOrdenadas = new ArrayList<>();
        List<Integer> idsFiliais = new ArrayList<>();
        
        // Primeiro adiciona a própria filial (líder) como null (será consultada diretamente)
        filiaisOrdenadas.add(null); // Placeholder - será consultada diretamente
        idsFiliais.add(id);
        
        lockFiliais.lock();
        try {
            filiaisOrdenadas.addAll(todasFiliais);
            for (Filial f : todasFiliais) {
                try {
                    idsFiliais.add(f.getId());
                } catch (Exception e) {
                    idsFiliais.add(-1);
                }
            }
        } finally {
            lockFiliais.unlock();
        }
        
        // Ordena por média de estoque (decrescente)
        // Cria lista de pares (filial, média) para ordenar
        List<Map.Entry<Filial, Double>> filiaisComMedia = new ArrayList<>();
        for (int i = 0; i < filiaisOrdenadas.size(); i++) {
            Filial f = filiaisOrdenadas.get(i);
            try {
                double media;
                if (f == null) {
                    // É a própria filial líder - calcula média diretamente
                    if (estoque.isEmpty()) {
                        media = 0.0;
                    } else {
                        int soma = 0;
                        for (int qtd : estoque.values()) {
                            soma += qtd;
                        }
                        media = (double) soma / estoque.size();
                    }
                } else {
                    media = f.calcularMediaEstoque();
                }
                filiaisComMedia.add(new AbstractMap.SimpleEntry<>(f, media));
            } catch (Exception e) {
                filiaisComMedia.add(new AbstractMap.SimpleEntry<>(f, 0.0));
            }
        }
        
        // Ordena por média (decrescente)
        filiaisComMedia.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));
        
        System.out.println("Filial " + id + " (LÍDER): Filiais ordenadas por média de estoque:");
        for (Map.Entry<Filial, Double> entry : filiaisComMedia) {
            Filial f = entry.getKey();
            double media = entry.getValue();
            if (f == null) {
                System.out.println("  - Filial " + id + " (EU - LÍDER): média " + String.format("%.2f", media));
            } else {
                try {
                    System.out.println("  - Filial " + f.getId() + ": média " + String.format("%.2f", media));
                } catch (Exception e) {
                    // Ignora filiais mortas
                }
            }
        }
        
        // Consulta cada filial (em ordem) e coleta produtos disponíveis
        for (Map.Entry<Filial, Double> entry : filiaisComMedia) {
            Filial filial = entry.getKey();
            if (produtosRestantes.isEmpty()) {
                break; // Já temos todos os produtos
            }
            
            try {
                String[] produtosDisponiveis;
                int filialId;
                
                if (filial == null) {
                    // É a própria filial líder - consulta diretamente
                    filialId = id;
                    produtosDisponiveis = consultarProdutosDisponiveis(
                        produtosRestantes.toArray(new String[0])
                    );
                } else {
                    // É outra filial - consulta via RPC
                    filialId = filial.getId();
                    produtosDisponiveis = filial.consultarProdutosDisponiveis(
                        produtosRestantes.toArray(new String[0])
                    );
                }
                
                if (produtosDisponiveis != null && produtosDisponiveis.length > 0) {
                    // Conta quantos de cada produto esta filial tem disponível
                    Map<String, Integer> produtosFilial = new HashMap<>();
                    for (String p : produtosDisponiveis) {
                        produtosFilial.put(p, produtosFilial.getOrDefault(p, 0) + 1);
                    }
                    
                    // Conta quantos de cada produto ainda precisamos
                    Map<String, Integer> produtosNecessariosRestantes = new HashMap<>();
                    for (String p : produtosRestantes) {
                        produtosNecessariosRestantes.put(p, produtosNecessariosRestantes.getOrDefault(p, 0) + 1);
                    }
                    
                    // Seleciona apenas os produtos que esta filial pode fornecer (respeitando quantidade necessária)
                    List<String> produtosDestaFilial = new ArrayList<>();
                    for (Map.Entry<String, Integer> necessidadeEntry : produtosNecessariosRestantes.entrySet()) {
                        String produto = necessidadeEntry.getKey();
                        int quantidadeNecessaria = necessidadeEntry.getValue();
                        int quantidadeDisponivel = produtosFilial.getOrDefault(produto, 0);
                        
                        // Adiciona o produto quantas vezes for necessário (até o limite disponível)
                        int quantidadeAUsar = Math.min(quantidadeNecessaria, quantidadeDisponivel);
                        for (int i = 0; i < quantidadeAUsar; i++) {
                            produtosDestaFilial.add(produto);
                        }
                    }
                    
                    if (!produtosDestaFilial.isEmpty()) {
                        produtosPorFilial.put(filialId, produtosDestaFilial);
                        
                        // Conta quantos de cada produto esta filial vai fornecer
                        Map<String, Integer> produtosFornecidos = new HashMap<>();
                        for (String p : produtosDestaFilial) {
                            produtosFornecidos.put(p, produtosFornecidos.getOrDefault(p, 0) + 1);
                        }
                        
                        System.out.println("Filial " + id + " (LÍDER): ✓ Filial " + filialId + " tem disponível:");
                        for (Map.Entry<String, Integer> prodEntry : produtosFornecidos.entrySet()) {
                            System.out.println("    - " + prodEntry.getKey() + ": " + prodEntry.getValue() + " unidade(s)");
                        }
                        
                        // Remove os produtos que esta filial vai fornecer
                        produtosRestantes.removeAll(produtosDestaFilial);
                    }
                    
                    // Mostra o que ainda falta
                    if (!produtosRestantes.isEmpty()) {
                        Map<String, Integer> aindaFalta = new HashMap<>();
                        for (String p : produtosRestantes) {
                            aindaFalta.put(p, aindaFalta.getOrDefault(p, 0) + 1);
                        }
                        System.out.println("Filial " + id + " (LÍDER): Ainda faltam:");
                        for (Map.Entry<String, Integer> faltaEntry : aindaFalta.entrySet()) {
                            System.out.println("    - " + faltaEntry.getKey() + ": " + faltaEntry.getValue() + " unidade(s)");
                        }
                    }
                } else {
                    System.out.println("Filial " + id + " (LÍDER): Filial " + filialId + " não tem produtos disponíveis");
                }
            } catch (Exception e) {
                // Filial pode estar morta, continua
                int filialIdErro = (filial == null) ? id : -1;
                try {
                    if (filial != null) filialIdErro = filial.getId();
                } catch (Exception e2) {}
                System.err.println("Filial " + id + " (LÍDER): Erro ao consultar filial " + filialIdErro + ": " + e.getMessage());
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
            
            if (filialId == id) {
                // É a própria filial líder - processa diretamente
                System.out.println("Filial " + id + " (LÍDER): Processando " + produtosFilial.size() + 
                                 " produtos na própria filial (LÍDER)");
                boolean sucesso = processarProdutosEspecificos(
                    produtosFilial.toArray(new String[0]), 
                    0, // Bully não usa termos
                    id
                );
                if (sucesso) {
                    System.out.println("Filial " + id + " (LÍDER): ✓ Própria filial processou com sucesso");
                } else {
                    System.out.println("Filial " + id + " (LÍDER): ✗ Própria filial falhou ao processar");
                    todosSucesso = false;
                }
            } else {
                // É outra filial - processa via RPC
                lockFiliais.lock();
                try {
                    for (Filial filial : todasFiliais) {
                        if (filial.getId() == filialId) {
                            System.out.println("Filial " + id + " (LÍDER): Processando " + produtosFilial.size() + 
                                             " produtos na filial " + filialId);
                            boolean sucesso = filial.processarProdutosEspecificos(
                                produtosFilial.toArray(new String[0]), 
                                0, // Bully não usa termos
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
        // Bully: verifica apenas se o líder é válido (não usa termos)
        if (this.id_lider != -1 && this.id_lider != liderId) {
            System.err.println("Filial " + id + ": Rejeitou pedido - líder inválido (esperado: " + this.id_lider + ", recebido: " + liderId + ")");
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
