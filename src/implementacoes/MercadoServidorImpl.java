package implementacoes;

import classes.Pedido;
import interfaces.Filial;
import interfaces.MercadoServidor;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.time.LocalTime;
import java.util.*;

@WebService(endpointInterface = "interfaces.MercadoServidor")
public class MercadoServidorImpl implements MercadoServidor {
    private Map<Integer, List<Pedido>> restaurantesClientes;
    private Map<Integer, String> idToRestaurantes;
    private Random r;
    private int codigosPedidos = 0;
    
    // URLs das filiais conhecidas (para fallback/descoberta)
    private String[] urlsFiliais = {
        "http://127.0.0.1:9876/filial", 
        "http://127.0.0.1:9875/filial", 
        "http://127.0.0.1:9874/filial"
    };
    
    // Referência ao líder atual (atualizada quando o líder se anuncia)
    private volatile Filial filialLider = null;
    private volatile int idLiderAtual = -1;
    private volatile long ultimoHeartbeatLider = 0;

    public MercadoServidorImpl(String url) throws RemoteException {
        restaurantesClientes = new HashMap<>();
        idToRestaurantes = new HashMap<>();
        r = new Random();
        System.out.println("Mercado iniciado. Aguardando líder se anunciar...\n");
    }
    
    /**
     * Conecta a uma filial específica por URL
     */
    private Filial conectarFilial(String url) {
        try {
            URL wsdl = new URL(url + "?wsdl");
            QName qname = new QName("http://implementacoes/", "FilialImplService");
            Service service = Service.create(wsdl, qname);
            return service.getPort(Filial.class);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public int cadastrarPedido(String restaurante) {
        restaurantesClientes.put(codigosPedidos, new ArrayList<>());
        idToRestaurantes.put(codigosPedidos, restaurante);
        System.out.println("Cadastrando restaurante " + restaurante + " com o id " + codigosPedidos);
        return codigosPedidos++;
    }

    public boolean comprarProdutos(int restaurante, String[] produtos) {
        if (!restaurantesClientes.containsKey(restaurante)) return false;
        
        // Só deixa comprar se ultimo pedido ja foi entregue
        for (Pedido p : restaurantesClientes.get(restaurante)) {
            if (!p.entregue) return false;
        }

        System.out.println("\n=== Mercado recebeu pedido do Restaurante " + restaurante + ":");
        for (int i = 0; i < produtos.length; i++) {
            System.out.println("  Produto " + (i + 1) + ": " + produtos[i]);
        }
        
        boolean sucesso = coordenarComFiliais(produtos);
        
        if (sucesso) {
            int segundo_atual = LocalTime.now().getSecond();
            int tempo_entrega = r.nextInt(9) + 1;
            Pedido pedido = new Pedido(produtos, tempo_entrega, segundo_atual);
            restaurantesClientes.get(restaurante).add(pedido);
            System.out.println("Mercado: Pedido atendido com sucesso.\n");
        } else {
            System.out.println("Mercado: Não foi possível atender o pedido.\n");
        }
        
        return sucesso;
    }
    
    /**
     * Obtém a filial líder atual.
     * Primeiro tenta usar a referência conhecida, se não tiver tenta descobrir.
     */
    private Filial obterFilialLider() {
        // Se temos um líder conhecido, verifica se ainda está vivo
        if (filialLider != null) {
            try {
                filialLider.getId(); // Testa se está vivo
                return filialLider;
            } catch (Exception e) {
                System.out.println("Mercado: Líder anterior morreu. Tentando descobrir novo líder...");
                filialLider = null;
                idLiderAtual = -1;
            }
        }
        
        // Não temos líder conhecido, tenta descobrir perguntando às filiais
        System.out.println("Mercado: Procurando líder...");
        
        for (String url : urlsFiliais) {
            try {
                Filial filial = conectarFilial(url);
                if (filial == null) continue;
                
                int idLider = filial.getLider();
                if (idLider == -1) continue; // Esta filial não sabe quem é o líder
                
                // Encontrou uma filial que sabe quem é o líder
                // Verifica se esta filial É o líder
                if (filial.getId() == idLider) {
                    filialLider = filial;
                    idLiderAtual = idLider;
                    ultimoHeartbeatLider = System.currentTimeMillis();
                    System.out.println("Mercado: Líder encontrado! Filial " + idLider);
                    return filialLider;
                }
                
                // Esta filial não é o líder, mas sabe quem é - tenta conectar ao líder
                for (String urlLider : urlsFiliais) {
                    try {
                        Filial possibleLider = conectarFilial(urlLider);
                        if (possibleLider != null && possibleLider.getId() == idLider) {
                            filialLider = possibleLider;
                            idLiderAtual = idLider;
                            ultimoHeartbeatLider = System.currentTimeMillis();
                            System.out.println("Mercado: Líder encontrado! Filial " + idLider);
                            return filialLider;
                        }
                    } catch (Exception e2) {
                        // Continua tentando
                    }
                }
            } catch (Exception e) {
                // Esta filial não está disponível, tenta próxima
            }
        }
        
        System.err.println("Mercado: Não foi possível encontrar o líder");
        return null;
    }
    
    /**
     * Método chamado pelo líder para se anunciar ao mercado.
     */
    public void notificarLider(int liderId) {
        boolean novoLider = (idLiderAtual != liderId);
        
        // Tenta conectar ao líder que está se anunciando
        for (String url : urlsFiliais) {
            try {
                Filial filial = conectarFilial(url);
                if (filial != null && filial.getId() == liderId) {
                    filialLider = filial;
                    idLiderAtual = liderId;
                    ultimoHeartbeatLider = System.currentTimeMillis();
                    
                    if (novoLider) {
                        System.out.println("Mercado: ✓ Líder conectado! Filial " + liderId);
                    }
                    return;
                }
            } catch (Exception e) {
                // Continua tentando
            }
        }
    }
    
    private boolean coordenarComFiliais(String[] produtos) {
        Filial lider = obterFilialLider();
        
        if (lider == null) {
            System.err.println("Mercado: Sem líder disponível para atender o pedido");
            return false;
        }
        
        try {
            System.out.println("Mercado: Enviando pedido para o líder (Filial " + idLiderAtual + ")...");
            boolean sucesso = lider.solicitarProdutos(produtos);
            
            if (sucesso) {
                System.out.println("Mercado: Pedido processado com sucesso pelo líder");
            } else {
                System.out.println("Mercado: Líder não conseguiu processar o pedido");
            }
            
            return sucesso;
        } catch (Exception e) {
            System.err.println("Mercado: Erro ao enviar pedido: " + e.getMessage());
            
            // Líder pode ter morrido, tenta novamente
            filialLider = null;
            idLiderAtual = -1;
            
            lider = obterFilialLider();
            if (lider != null) {
                try {
                    System.out.println("Mercado: Tentando novamente com novo líder...");
                    return lider.solicitarProdutos(produtos);
                } catch (Exception e2) {
                    System.err.println("Mercado: Falha na segunda tentativa: " + e2.getMessage());
                }
            }
            
            return false;
        }
    }

    public int tempoEntrega(int restaurante) {
        int segundo_atual = LocalTime.now().getSecond();
        int tamanho = restaurantesClientes.get(restaurante).size();
        
        if (tamanho == 0) return 0;

        Pedido pedido = restaurantesClientes.get(restaurante).get(tamanho - 1);

        int diferencaSegundos = segundo_atual - pedido.segundo_inicial;
        if (diferencaSegundos < 0) {
            diferencaSegundos = (60 - pedido.segundo_inicial) + segundo_atual;
        }

        int rest = Math.max(0, pedido.tempo_entrega - diferencaSegundos);
        if (rest == 0) pedido.entregue = true;
        
        return rest;
    }
}
