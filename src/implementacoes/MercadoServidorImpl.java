package implementacoes;

import classes.Pedido;
import interfaces.Filial;
import interfaces.MercadoServidor;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.time.LocalTime;
import java.util.*;

import static java.util.List.*;

@WebService(
        endpointInterface = "interfaces.MercadoServidor" // ,
        //targetNamespace = "implementacoes"
)
public class MercadoServidorImpl implements MercadoServidor {
    private Map<Integer, List<Pedido>> restaurantesClientes;
    private Map<Integer, String> idToRestaurantes;
    private Random r;

    private String my_url;
    
    // Conexões com as filiais
    private Filial[] filiais;
    private String[] urlsFiliais = {"http://127.0.0.1:9876/filial", "http://127.0.0.1:9875/filial", "http://127.0.0.1:9874/filial"};
    
    // Método público para obter URLs das filiais (útil para debug)
    public String[] getUrlsFiliais() {
        return urlsFiliais;
    }

    public MercadoServidorImpl(String url) throws RemoteException {
        restaurantesClientes =  new HashMap<>();
        idToRestaurantes = new HashMap<>();
        r =  new Random();
        my_url = url;
        
        // Conecta às filiais
        conectarFiliais();
    }
    
    private void conectarFiliais() {
        filiais = new Filial[urlsFiliais.length];
        try {
            for (int i = 0; i < urlsFiliais.length; i++) {
                filiais[i] = getFilial(urlsFiliais[i]);
                System.out.println("Mercado conectado à filial: " + urlsFiliais[i]);
            }
            System.out.println("Mercado conectado a " + filiais.length + " filiais.\n");
        } catch (Exception e) {
            System.err.println("Aviso: Não foi possível conectar a todas as filiais: " + e.getMessage());
            System.err.println("Certifique-se de que o FilialServidorPublisher está rodando.");
        }
    }

    private Filial getFilial(String url){
        try {
            URL wsdl = new URL(url + "?wsdl");
            QName qname = new QName("http://implementacoes/", "FilialImplService");
            Service service = Service.create(wsdl, qname);
            return service.getPort(Filial.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private int codigosPedidos = 0;

    public int cadastrarPedido(String restaurante) {
        restaurantesClientes.put(this.codigosPedidos, new ArrayList<>());
        idToRestaurantes.put(codigosPedidos, restaurante);

        System.out.println("Cadastrando restaurante " + restaurante + " com o id " + codigosPedidos);
        return codigosPedidos++;
    }

    public boolean comprarProdutos(int restaurante, String[] produtos) {
        if(!restaurantesClientes.containsKey(restaurante)) return false;
        // Só deixa comprar se ultimo pedido ja foi entregue
        for(Pedido p : restaurantesClientes.get(restaurante)) if(!p.entregue) return false;

        System.out.println("\n=== Mercado recebeu pedido do Restaurante " + restaurante + ":");
        for (int i = 0; i < produtos.length; i++) {
            System.out.println("  Produto " + (i + 1) + ": " + produtos[i]);
        }
        
        // Coordena com as filiais para atender o pedido
        boolean sucesso = coordenarComFiliais(produtos);
        
        if (sucesso) {
            // Se as filiais atenderam, registra o pedido
            int segundo_atual = LocalTime.now().getSecond(), tempo_entrega = r.nextInt(9) + 1;
            Pedido pedido = new Pedido(produtos, tempo_entrega, segundo_atual);
            restaurantesClientes.get(restaurante).add(pedido);
            System.out.println("Mercado: Pedido atendido pelas filiais com sucesso.\n");
        } else {
            System.out.println("Mercado: Filiais não conseguiram atender o pedido.\n");
        }
        
        return sucesso;
    }
    
    /**
     * Obtém a filial líder atual consultando as filiais disponíveis.
     * Se o líder não for encontrado ou estiver morto, tenta obter novamente.
     * 
     * @return A filial líder, ou null se nenhuma filial estiver disponível
     */
    private Filial obterFilialLider() {
        if (filiais == null) {
            System.out.println("Mercado: Filiais não disponíveis");
            return null;
        }
        
        // Primeiro, garante que todas as filiais estejam conectadas
        garantirConexoesFiliais();
        
        // Consulta uma filial viva para descobrir quem é o líder
        int idLider = -1;
        for (int i = 0; i < filiais.length; i++) {
            if (filiais[i] != null) {
                try {
                    idLider = filiais[i].getLider();
                    if (idLider != -1) {
                        System.out.println("Mercado: Líder identificado: filial " + idLider);
                        break;
                    }
                } catch (Exception e) {
                    // Filial está morta, tenta reconectar
                    try {
                        filiais[i] = getFilial(urlsFiliais[i]);
                        idLider = filiais[i].getLider();
                        if (idLider != -1) {
                            System.out.println("Mercado: Líder identificado após reconexão: filial " + idLider);
                            break;
                        }
                    } catch (Exception e2) {
                        // Continua tentando próxima filial
                    }
                }
            }
        }
        
        if (idLider == -1) {
            System.err.println("Mercado: Não foi possível identificar o líder (eleição pode estar em andamento)");
            return null;
        }
        
        // Agora encontra a filial com o ID do líder
        for (int i = 0; i < filiais.length; i++) {
            if (filiais[i] != null) {
                try {
                    int idFilial = filiais[i].getId();
                    if (idFilial == idLider) {
                        System.out.println("Mercado: Filial líder encontrada (ID: " + idLider + ", porta: " + (9876 - i) + ")");
                        return filiais[i];
                    }
                } catch (Exception e) {
                    // Filial está morta, tenta reconectar
                    try {
                        filiais[i] = getFilial(urlsFiliais[i]);
                        int idFilial = filiais[i].getId();
                        if (idFilial == idLider) {
                            System.out.println("Mercado: Filial líder encontrada após reconexão (ID: " + idLider + ")");
                            return filiais[i];
                        }
                    } catch (Exception e2) {
                        // Continua procurando
                    }
                }
            }
        }
        
        // Se chegou aqui, o líder não foi encontrado (pode ter morrido)
        System.err.println("Mercado: Filial líder (ID: " + idLider + ") não foi encontrada. Pode ter morrido.");
        return null;
    }
    
    /**
     * Garante que todas as filiais estejam conectadas, reconectando as que estiverem mortas.
     */
    private void garantirConexoesFiliais() {
        for (int i = 0; i < filiais.length; i++) {
            if (filiais[i] == null) {
                // Filial nunca foi conectada
                try {
                    filiais[i] = getFilial(urlsFiliais[i]);
                    filiais[i].getId(); // Testa conexão
                } catch (Exception e) {
                    // Não foi possível conectar agora, mas continua
                }
            } else {
                // Testa se a filial ainda está viva
                try {
                    filiais[i].getId();
                } catch (Exception e) {
                    // Filial morreu, tenta reconectar
                    try {
                        filiais[i] = getFilial(urlsFiliais[i]);
                        filiais[i].getId(); // Testa reconexão
                    } catch (Exception e2) {
                        // Não foi possível reconectar
                    }
                }
            }
        }
    }
    
    private boolean coordenarComFiliais(String[] produtos) {
        // Obtém a filial líder
        Filial filialLider = obterFilialLider();
        
        if (filialLider == null) {
            System.err.println("Mercado: Não foi possível obter a filial líder para atender o pedido");
            return false;
        }
        
        try {
            System.out.println("Mercado: Enviando pedido para a filial líder...");
            
            // O mercado envia o pedido diretamente para a filial líder
            // A filial líder coordena com as outras filiais via consenso
            boolean sucesso = filialLider.solicitarProdutos(produtos);
            
            if (sucesso) {
                System.out.println("Mercado: Pedido processado com sucesso pela filial líder");
            } else {
                System.out.println("Mercado: Filial líder não conseguiu processar o pedido");
            }
            
            return sucesso;
        } catch (Exception e) {
            System.err.println("Mercado: Erro ao enviar pedido para filial líder: " + e.getMessage());
            e.printStackTrace();
            
            // Se o líder morreu durante o processamento, tenta obter novo líder
            System.out.println("Mercado: Tentando obter novo líder...");
            filialLider = obterFilialLider();
            if (filialLider != null) {
                try {
                    System.out.println("Mercado: Tentando novamente com novo líder...");
                    return filialLider.solicitarProdutos(produtos);
                } catch (Exception e2) {
                    System.err.println("Mercado: Erro ao tentar novamente: " + e2.getMessage());
                }
            }
            
            return false;
        }
    }

    public int tempoEntrega(int restaurante) {
        int segundo_atual = LocalTime.now().getSecond(), tamanho = restaurantesClientes.get(restaurante).size();

        Pedido pedido = restaurantesClientes.get(restaurante).get(tamanho);

        int rest = Math.max(0, pedido.tempo_entrega - (segundo_atual - pedido.segundo_inicial));

        if(rest == 0) pedido.entregue = true;
        return rest;
    }

}
