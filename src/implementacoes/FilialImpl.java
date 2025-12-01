package implementacoes;

import classes.Pedido;
import interfaces.Filial;

import javax.jws.WebMethod;
import javax.jws.WebService;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.*;

@WebService(
        endpointInterface = "interfaces.Filial"
)
public class FilialImpl implements Filial{
    Random r;
    int id, id_lider, id_origem;
    String my_url;

    private Filial next;
    private boolean emEleicao;
    private String nextUrl; // URL da próxima filial para reconexão
    private Thread threadMonitoramento; // Thread para monitorar falhas
    private long ultimaEleicaoTimestamp = 0; // Timestamp da última eleição iniciada
    private int idEleicaoAtual = -1; // ID do candidato da eleição atual em processamento
    
    // Estoque da filial: nome do produto -> quantidade
    private Map<String, Integer> estoque;
    private boolean emConsenso;
    private int idFilialEscolhidaConsenso;
    private String[] produtosConsenso;
    private boolean pedidoProcessado; // Flag para indicar se o pedido foi processado com sucesso

    public FilialImpl(String my_url) {
        this.r = new Random();
        this.id = r.nextInt(2000);
        this.id_lider = -1;
        this.my_url = my_url;
        this.emEleicao = false;
        this.estoque = new HashMap<>();
        this.emConsenso = false;
        this.idFilialEscolhidaConsenso = -1;
        this.pedidoProcessado = false;
        inicializarEstoque();
        
        // Inicia thread de monitoramento de falhas
        iniciarMonitoramento();
    }
    
    /**
     * Inicia thread que monitora periodicamente se a próxima filial está viva
     */
    private void iniciarMonitoramento() {
        threadMonitoramento = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000); // Verifica a cada 3 segundos
                    
                    // Só monitora se já tem referência para próxima filial
                    if (next != null && nextUrl != null) {
                        if (!proximaFilialViva()) {
                            System.out.println("\n⚠️  Filial " + id + " detectou que a próxima filial está morta!");
                            System.out.println("   Tentando reconectar...");
                            
                            if (tentarReconectar() && proximaFilialViva()) {
                                System.out.println("   ✓ Reconectado! Iniciando nova eleição...");
                                // Aguarda um pouco antes de iniciar eleição
                                Thread.sleep(1000);
                                iniciarNovaEleicao();
                            } else {
                                System.out.println("   ✗ Não foi possível reconectar. Continuando monitoramento...");
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Erro no monitoramento da filial " + id + ": " + e.getMessage());
                }
            }
        });
        threadMonitoramento.setDaemon(true); // Thread daemon não impede JVM de terminar
        threadMonitoramento.start();
        System.out.println("Filial " + id + ": Monitoramento de falhas iniciado");
    }
    
    private void inicializarEstoque() {
        // Inicializa estoque lendo do cardápio
        // Cada filial começa com quantidades aleatórias entre 0 e 5 de cada produto
        try (Scanner scanner = new Scanner(new File("src/cardapio/menu_restaurante.csv"))) {
            scanner.nextLine(); // Pula cabeçalho
            while (scanner.hasNextLine()) {
                String linha = scanner.nextLine();
                String[] partes = linha.split(",");
                if (partes.length >= 2) {
                    String nomeProduto = partes[1].trim();
                    // Cada filial tem entre 0 e 5 unidades de cada produto
                    estoque.put(nomeProduto, r.nextInt(6));
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Erro ao ler cardápio: " + e.getMessage());
            // Se não encontrar, inicializa com alguns produtos padrão
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

    @WebMethod
    public int getId() {
        return this.id;
    }

    @WebMethod
    @Override
    public int getLider() {
        return this.id_lider;
    }

    @WebMethod
    public  void election(int idC, int idOrigem) throws MalformedURLException {
        int maior = Math.max(idC, this.id);
        
        // Se já está em eleição, verifica se deve processar esta mensagem
        if (emEleicao) {
            // Se esta mensagem tem um ID maior que o que estamos processando, processa
            // Caso contrário, ignora (evita processar eleições com IDs menores)
            if (maior <= idEleicaoAtual && idOrigem != this.id) {
                System.out.println("Filial " + this.id + " já está em eleição (candidato atual: " + idEleicaoAtual + 
                                 "). Ignorando mensagem com ID menor ou igual (" + maior + ") de origem " + idOrigem);
                return;
            }
            // Se a mensagem tem ID maior, continua processando (o maior ID sempre vence)
            System.out.println("Filial " + this.id + " recebeu election(" + idC + ") de origem " + idOrigem + 
                             " [JÁ EM ELEIÇÃO, mas ID maior - processando]");
        } else {
            System.out.println("Filial " + this.id + " recebeu election(" + idC + ") de origem " + idOrigem + " [NOVA ELEIÇÃO]");
        }
        
        this.id_origem = idOrigem;
        emEleicao = true;
        idEleicaoAtual = maior; // Atualiza o ID da eleição atual

        // Garante que temos referência para a próxima filial antes de começar
        while (next == null) {
            System.out.println("Filial " + this.id + " recebeu pedido de eleição, mas 'next' ainda é null. Aguardando conexão com a próxima filial...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emEleicao = false;
                idEleicaoAtual = -1;
                return;
            }
        }

        System.out.println("FILIAL ATUAL: " + this.id);
        
        // Verifica se próxima filial está disponível
        if (next == null || !proximaFilialViva()) {
            System.err.println("Filial " + this.id + ": Próxima filial não está disponível durante eleição. Tentando reconectar...");
            if (!tentarReconectar() || next == null || !proximaFilialViva()) {
                System.err.println("Filial " + this.id + ": Não foi possível reconectar. Eleição abortada.");
                emEleicao = false;
                idEleicaoAtual = -1;
                return;
            }
        }
        
        // Verificação final de segurança
        if (next == null) {
            System.err.println("Filial " + this.id + ": next é null após verificação. Eleição abortada.");
            emEleicao = false;
            idEleicaoAtual = -1;
            return;
        }
        
        System.out.println("NEXT " + next.getId());

        if (next.getId() == idOrigem) {
            // Verifica se não há outra eleição em andamento com ID maior
            if (maior >= this.id) {
                System.out.println("Filial " + this.id + " detectou fim do anel. Líder = " + maior);
                try {
                    next.announceLeader(maior, this.id);
                    emEleicao = false;
                    idEleicaoAtual = -1;
                    return;
                } catch (Exception e) {
                    System.err.println("Filial " + this.id + " não conseguiu anunciar líder: " + e.getMessage());
                    // Tenta reconectar e iniciar nova eleição
                    if (tentarReconectar()) {
                        iniciarNovaEleicao();
                    }
                    emEleicao = false;
                    idEleicaoAtual = -1;
                }
            } else {
                System.out.println("Filial " + this.id + " ignorando eleição com ID menor (" + maior + 
                                 "). Aguardando eleição com ID maior.");
                emEleicao = false;
                idEleicaoAtual = -1;
            }
            return;
        }
        
        // Tenta passar a eleição para a próxima filial
        try {
            next.election(maior, idOrigem);
        } catch (Exception e) {
            System.err.println("Filial " + this.id + " não conseguiu passar eleição para próxima filial: " + e.getMessage());
            // Próxima filial pode estar morta - tenta reconectar
            if (tentarReconectar() && proximaFilialViva()) {
                // Se reconectou, tenta novamente
                try {
                    next.election(maior, idOrigem);
                } catch (Exception e2) {
                    System.err.println("Filial " + this.id + " falhou novamente após reconexão. Anel pode estar quebrado.");
                    emEleicao = false;
                    idEleicaoAtual = -1;
                    // Inicia nova eleição se for a origem
                    if (id == idOrigem) {
                        iniciarNovaEleicao();
                    }
                }
            } else {
                // Não conseguiu reconectar - anel está quebrado
                System.err.println("Filial " + this.id + ": Anel quebrado. Eleição abortada.");
                emEleicao = false;
                idEleicaoAtual = -1;
                if (id == idOrigem) {
                    // Se é a origem, tenta iniciar nova eleição depois
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            iniciarNovaEleicao();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }
            }
        }
    }

    @WebMethod
    public  void announceLeader (int idLider, int idOrigem) {
        this.id_lider = idLider;
        emEleicao = false;
        idEleicaoAtual = -1; // Limpa eleição atual
        if(this.id == idOrigem) {
            System.out.println("Divulgação do líder com sucesso.\n");
            return;
        }

        System.out.println("Filial " + this.id + " recebeu que líder é " + idLider);
        
        // Verifica se próxima filial está disponível
        if (next == null || !proximaFilialViva()) {
            System.err.println("Filial " + this.id + ": Próxima filial não está disponível durante anúncio. Tentando reconectar...");
            if (!tentarReconectar() || !proximaFilialViva()) {
                System.err.println("Filial " + this.id + ": Não foi possível reconectar. Anúncio abortado.");
                return;
            }
        }
        
        try{
            next.announceLeader(idLider, idOrigem);
        } catch (Exception e){
            System.err.println("Filial " + this.id + " não conseguiu repassar anúncio de líder: " + e.getMessage());
            // Tenta reconectar
            if (tentarReconectar() && proximaFilialViva()) {
                try {
                    next.announceLeader(idLider, idOrigem);
                } catch (Exception e2) {
                    System.err.println("Filial " + this.id + " falhou após reconexão. Anel pode estar quebrado.");
                }
            }
        }
    }

    public void setNext(Filial next) {
        this.next = next;
    }
    
    public void setNextUrl(String nextUrl) {
        this.nextUrl = nextUrl;
    }
    
    /**
     * Tenta reconectar à próxima filial
     */
    private boolean tentarReconectar() {
        if (nextUrl == null) {
            return false;
        }
        
        try {
            System.out.println("Filial " + id + " tentando reconectar à próxima filial: " + nextUrl);
            javax.xml.namespace.QName qname = new javax.xml.namespace.QName("http://implementacoes/", "FilialImplService");
            javax.xml.ws.Service service = javax.xml.ws.Service.create(new java.net.URL(nextUrl + "?wsdl"), qname);
            this.next = service.getPort(Filial.class);
            System.out.println("Filial " + id + " reconectada com sucesso!");
            return true;
        } catch (Exception e) {
            System.out.println("Filial " + id + " não conseguiu reconectar: " + e.getMessage());
            this.next = null;
            return false;
        }
    }
    
    /**
     * Verifica se a próxima filial está viva
     */
    private boolean proximaFilialViva() {
        if (next == null) {
            return false;
        }
        try {
            next.getId(); // Tenta chamar um método simples
            return true;
        } catch (Exception e) {
            // Não loga aqui para evitar spam - o monitoramento já loga
            return false;
        }
    }
    
    /**
     * Verifica se o líder está vivo (útil para detectar se o líder morreu)
     */
    private boolean liderVivo() {
        if (id_lider == -1 || id_lider == id) {
            return true; // Não há líder ou somos o líder
        }
        
        // Se o líder não somos nós, precisamos verificar se ele está no anel
        // Como não temos referência direta ao líder, assumimos que se o anel está funcionando,
        // o líder provavelmente está vivo (será detectado pelo monitoramento do anel)
        return proximaFilialViva();
    }
    
    /**
     * Inicia uma nova eleição quando detecta que o anel está quebrado
     */
    private void iniciarNovaEleicao() {
        if (emEleicao) {
            // Verifica se a eleição atual está "travada" (muito antiga)
            long agora = System.currentTimeMillis();
            if (agora - ultimaEleicaoTimestamp < 5000) { // 5 segundos
                System.out.println("Filial " + id + ": Eleição recente em andamento. Aguardando...");
                return; // Eleição recente, não reinicia
            }
            // Se passou muito tempo, pode ser que a eleição travou
            System.out.println("Filial " + id + ": Eleição anterior pode ter travado. Reiniciando...");
            emEleicao = false;
            idEleicaoAtual = -1;
        }
        
        // Verifica se a próxima filial está viva antes de iniciar eleição
        if (next == null || !proximaFilialViva()) {
            System.out.println("Filial " + id + ": Não é possível iniciar eleição - próxima filial não está disponível.");
            if (nextUrl != null) {
                System.out.println("Filial " + id + ": Tentando reconectar antes de iniciar eleição...");
                if (!tentarReconectar() || !proximaFilialViva()) {
                    System.out.println("Filial " + id + ": Não foi possível reconectar. Eleição abortada.");
                    return;
                }
            } else {
                return;
            }
        }
        
        ultimaEleicaoTimestamp = System.currentTimeMillis();
        System.out.println("Filial " + id + " detectou problema no anel. Iniciando nova eleição...");
        try {
            election(this.id, this.id);
        } catch (Exception e) {
            System.err.println("Erro ao iniciar nova eleição: " + e.getMessage());
            emEleicao = false;
            idEleicaoAtual = -1;
        }
    }
    
    @WebMethod
    @Override
    public boolean solicitarProdutos(String[] produtos) throws MalformedURLException {
        // Método público chamado pelo restaurante
        // Internamente inicia o consenso entre as filiais
        System.out.println("\n=== Filial " + id + " recebeu pedido do restaurante: " + java.util.Arrays.toString(produtos));
        System.out.println("Iniciando consenso entre filiais para decidir qual atende...");
        
        // Reseta flag de processamento
        pedidoProcessado = false;
        
        // Inicia o consenso (esta filial é a origem)
        int idOrigem = this.id;
        iniciarConsenso(produtos, idOrigem, -1);
        
        // Aguarda o consenso terminar (a filial escolhida processa automaticamente)
        try {
            Thread.sleep(2000); // Tempo para o consenso circular e processar
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Retorna true se alguma filial processou o pedido
        return pedidoProcessado;
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
        // Verifica se tem estoque suficiente
        if (!temEstoque(produtos)) {
            return false;
        }
        
        // Remove do estoque
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
    
    @WebMethod
    @Override
    public void iniciarConsenso(String[] produtos, int idOrigem, int idFilialEscolhida) throws MalformedURLException {
        this.produtosConsenso = produtos;
        this.id_origem = idOrigem;
        this.emConsenso = true;
        
        // Verifica se próxima filial está viva antes de iniciar consenso
        if (next == null || !proximaFilialViva()) {
            System.err.println("Filial " + id + ": Próxima filial não está disponível. Tentando reconectar...");
            if (!tentarReconectar() || !proximaFilialViva()) {
                System.err.println("Filial " + id + ": Não foi possível conectar à próxima filial. Consenso abortado.");
                emConsenso = false;
                return;
            }
        }
        
        // Verifica se esta filial tem estoque
        int melhorId = idFilialEscolhida;
        if (temEstoque(produtos)) {
            // Esta filial tem estoque, então ela é candidata
            melhorId = Math.max(melhorId, this.id);
        }
        
        System.out.println("Filial " + id + " iniciando consenso. Melhor candidato até agora: " + melhorId);
        
        // Passa para a próxima filial
        try {
            next.receberConsenso(produtos, idOrigem, melhorId);
        } catch (Exception e) {
            System.err.println("Filial " + id + " não conseguiu passar consenso: " + e.getMessage());
            // Tenta reconectar
            if (tentarReconectar() && proximaFilialViva()) {
                try {
                    next.receberConsenso(produtos, idOrigem, melhorId);
                } catch (Exception e2) {
                    System.err.println("Filial " + id + ": Consenso falhou. Próxima filial pode estar morta.");
                    emConsenso = false;
                }
            } else {
                emConsenso = false;
            }
        }
    }
    
    @WebMethod
    @Override
    public void receberConsenso(String[] produtos, int idOrigem, int idFilialEscolhida) throws MalformedURLException {
        this.produtosConsenso = produtos;
        this.id_origem = idOrigem;
        this.emConsenso = true;
        
        int melhorId = idFilialEscolhida;
        
        // Verifica se esta filial tem estoque e é melhor candidata
        if (temEstoque(produtos)) {
            melhorId = Math.max(melhorId, this.id);
        }
        
        System.out.println("Filial " + id + " recebeu consenso. Melhor candidato: " + melhorId);
        
        // Verifica se próxima filial está disponível
        if (next == null || !proximaFilialViva()) {
            System.err.println("Filial " + id + ": Próxima filial não está disponível durante consenso. Tentando reconectar...");
            if (!tentarReconectar() || next == null || !proximaFilialViva()) {
                System.err.println("Filial " + id + ": Não foi possível reconectar. Consenso abortado.");
                emConsenso = false;
                return;
            }
        }
        
        // Verificação final de segurança
        if (next == null) {
            System.err.println("Filial " + id + ": next é null após verificação. Consenso abortado.");
            emConsenso = false;
            return;
        }
        
        // Se voltou ao início, anuncia a decisão
        if (next.getId() == idOrigem) {
            System.out.println("Filial " + id + " detectou fim do anel no consenso. Filial escolhida: " + melhorId);
            this.idFilialEscolhidaConsenso = melhorId;
            // Verifica se a filial escolhida tem estoque antes de processar
            boolean podeProcessar = (melhorId != -1);
            try {
                next.anunciarDecisao(melhorId, idOrigem, podeProcessar);
            } catch (Exception e) {
                System.err.println("Filial " + id + " não conseguiu anunciar decisão: " + e.getMessage());
                if (tentarReconectar() && proximaFilialViva()) {
                    try {
                        next.anunciarDecisao(melhorId, idOrigem, podeProcessar);
                    } catch (Exception e2) {
                        System.err.println("Filial " + id + ": Falha após reconexão.");
                    }
                }
            }
            emConsenso = false;
            return;
        }
        
        // Passa para a próxima filial
        try {
            next.receberConsenso(produtos, idOrigem, melhorId);
        } catch (Exception e) {
            System.err.println("Filial " + id + " não conseguiu passar consenso: " + e.getMessage());
            // Tenta reconectar
            if (tentarReconectar() && proximaFilialViva()) {
                try {
                    next.receberConsenso(produtos, idOrigem, melhorId);
                } catch (Exception e2) {
                    System.err.println("Filial " + id + ": Consenso falhou. Próxima filial pode estar morta.");
                    emConsenso = false;
                }
            } else {
                emConsenso = false;
            }
        }
    }
    
    @WebMethod
    @Override
    public void anunciarDecisao(int idFilialEscolhida, int idOrigem, boolean processado) throws MalformedURLException {
        this.idFilialEscolhidaConsenso = idFilialEscolhida;
        emConsenso = false;
        
        System.out.println("Filial " + id + " recebeu decisão: filial " + idFilialEscolhida + " foi escolhida");
        
        // Se esta filial foi escolhida e pode processar, processa o pedido
        if (id == idFilialEscolhida && processado && temEstoque(produtosConsenso)) {
            boolean sucesso = processarPedido(produtosConsenso);
            if (sucesso) {
                System.out.println("Filial " + id + " processou pedido com SUCESSO");
                processado = true; // Atualiza flag
            } else {
                System.out.println("Filial " + id + " falhou ao processar pedido");
                processado = false;
            }
        }
        
        // Se voltou ao início (filial que recebeu o pedido do restaurante), marca resultado
        if (id == idOrigem) {
            pedidoProcessado = processado && (idFilialEscolhida != -1);
            System.out.println("Consenso finalizado. Filial " + idFilialEscolhida + 
                             (pedidoProcessado ? " atenderá o pedido" : " não pôde atender") + ".\n");
            return;
        }
        
        // Verifica se próxima filial está disponível
        if (next == null || !proximaFilialViva()) {
            System.err.println("Filial " + id + ": Próxima filial não está disponível durante anúncio de decisão. Tentando reconectar...");
            if (!tentarReconectar() || !proximaFilialViva()) {
                System.err.println("Filial " + id + ": Não foi possível reconectar. Anúncio de decisão abortado.");
                return;
            }
        }
        
        // Passa para a próxima filial
        try {
            next.anunciarDecisao(idFilialEscolhida, idOrigem, processado);
        } catch (Exception e) {
            System.err.println("Filial " + id + " não conseguiu repassar anúncio de decisão: " + e.getMessage());
            if (tentarReconectar() && proximaFilialViva()) {
                try {
                    next.anunciarDecisao(idFilialEscolhida, idOrigem, processado);
                } catch (Exception e2) {
                    System.err.println("Filial " + id + ": Falha após reconexão.");
                }
            }
        }
    }

}
