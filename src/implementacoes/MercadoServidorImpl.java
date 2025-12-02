package implementacoes;

import classes.Pedido;
import interfaces.Filial;
import interfaces.MercadoServidor;
import javax.jws.WebMethod;
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
        //endpointInterface = "interfaces.MercadoServidor" // ,
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
    
    // Referência ao líder atual (atualizada via heartbeats)
    private volatile Filial filialLider = null;
    private volatile int termoLider = -1;
    private volatile long ultimoHeartbeatLider = 0;
    
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
        int filiaisConectadas = 0;
        for (int i = 0; i < urlsFiliais.length; i++) {
            try {
                filiais[i] = getFilial(urlsFiliais[i]);
                filiais[i].getId(); // Testa conexão
                System.out.println("Mercado conectado à filial: " + urlsFiliais[i]);
                filiaisConectadas++;
            } catch (Exception e) {
                System.out.println("Mercado: Não foi possível conectar à filial " + urlsFiliais[i] + 
                                 " (pode estar iniciando ainda)");
                filiais[i] = null; // Marca como não conectada
            }
        }
        System.out.println("Mercado: " + filiaisConectadas + "/" + urlsFiliais.length + " filiais conectadas.\n");
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
     * Obtém a filial líder atual.
     * O líder é conhecido automaticamente através de heartbeats.
     * Se não há líder conhecido ou o líder morreu, tenta descobrir consultando filiais.
     * 
     * @return A filial líder, ou null se nenhuma filial estiver disponível
     */
    private Filial obterFilialLider() {
        // Verifica se temos um líder conhecido e se ainda está válido
        if (filialLider != null) {
            try {
                // Verifica se o líder ainda está vivo
                filialLider.getId(); // Testa se está vivo
                
                // Verifica se recebeu heartbeat recente (menos de 1 segundo)
                long tempoDesdeUltimoHeartbeat = System.currentTimeMillis() - ultimoHeartbeatLider;
                if (tempoDesdeUltimoHeartbeat < 1000) { // Heartbeat recente
                    return filialLider;
                } else {
                    System.out.println("Mercado: Líder conhecido não enviou heartbeat recente (" + tempoDesdeUltimoHeartbeat + "ms). Descobrindo novo líder...");
                    filialLider = null;
                    termoLider = -1;
                }
            } catch (Exception e) {
                // Líder morreu
                System.out.println("Mercado: Líder conhecido morreu. Descobrindo novo líder...");
                filialLider = null;
                termoLider = -1;
            }
        }
        
        // Se não temos líder conhecido, tenta descobrir consultando filiais
        if (filiais == null) {
            System.out.println("Mercado: Filiais não disponíveis");
            return null;
        }
        
        // Tenta encontrar qualquer filial viva para descobrir quem é o líder
        int idLider = -1;
        
        for (int i = 0; i < filiais.length; i++) {
            if (filiais[i] != null) {
                try {
                    filiais[i].getId(); // Testa se está viva
                    idLider = filiais[i].getLider();
                    if (idLider != -1) {
                        System.out.println("Mercado: Líder identificado através da filial " + i + ": filial " + idLider);
                        break;
                    }
                } catch (Exception e) {
                    // Filial morreu, tenta reconectar
                    try {
                        filiais[i] = getFilial(urlsFiliais[i]);
                        filiais[i].getId();
                        idLider = filiais[i].getLider();
                        if (idLider != -1) {
                            System.out.println("Mercado: Líder identificado após reconexão: filial " + idLider);
                            break;
                        }
                    } catch (Exception e2) {
                        filiais[i] = null;
                    }
                }
            } else {
                try {
                    filiais[i] = getFilial(urlsFiliais[i]);
                    filiais[i].getId();
                    idLider = filiais[i].getLider();
                    if (idLider != -1) {
                        System.out.println("Mercado: Conectado à filial " + i + " e identificado líder: filial " + idLider);
                        break;
                    }
                } catch (Exception e) {
                    // Continua
                }
            }
        }
        
        if (idLider == -1) {
            System.err.println("Mercado: Não foi possível identificar o líder (nenhuma filial disponível ou eleição em andamento)");
            return null;
        }
        
        // Encontra a filial com o ID do líder
        for (int i = 0; i < filiais.length; i++) {
            if (filiais[i] != null) {
                try {
                    if (filiais[i].getId() == idLider) {
                        filialLider = filiais[i];
                        termoLider = filiais[i].getTermo();
                        System.out.println("Mercado: Filial líder encontrada e armazenada (ID: " + idLider + ", termo: " + termoLider + ")");
                        return filialLider;
                    }
                } catch (Exception e) {
                    try {
                        filiais[i] = getFilial(urlsFiliais[i]);
                        if (filiais[i].getId() == idLider) {
                            filialLider = filiais[i];
                            termoLider = filiais[i].getTermo();
                            return filialLider;
                        }
                    } catch (Exception e2) {
                        filiais[i] = null;
                    }
                }
            } else {
                try {
                    filiais[i] = getFilial(urlsFiliais[i]);
                    if (filiais[i].getId() == idLider) {
                        filialLider = filiais[i];
                        termoLider = filiais[i].getTermo();
                        return filialLider;
                    }
                } catch (Exception e) {
                    // Continua
                }
            }
        }
        
        System.err.println("Mercado: Filial líder (ID: " + idLider + ") não foi encontrada.");
        return null;
    }
    
    /**
     * Método chamado pelo líder para notificar o mercado (heartbeat).
     * Atualiza a referência ao líder automaticamente.
     */
    //@WebMethod
    //@Override
    public void notificarLider(int termo, int liderId) {
        // Se o termo é maior ou igual ao termo conhecido, atualiza referência ao líder
        if (termo >= termoLider) {
            int termoAnterior = termoLider;
            
            // Encontra a filial com o ID do líder
            for (int i = 0; i < filiais.length; i++) {
                if (filiais[i] != null) {
                    try {
                        if (filiais[i].getId() == liderId) {
                            filialLider = filiais[i];
                            termoLider = termo;
                            ultimoHeartbeatLider = System.currentTimeMillis();
                            
                            if (termo > termoAnterior) {
                                System.out.println("Mercado: ✓ Novo líder notificado! Filial " + liderId + " (termo: " + termo + ")");
                            }
                            return;
                        }
                    } catch (Exception e) {
                        // Filial morreu, tenta reconectar
                        try {
                            filiais[i] = getFilial(urlsFiliais[i]);
                            if (filiais[i].getId() == liderId) {
                                filialLider = filiais[i];
                                termoLider = termo;
                                ultimoHeartbeatLider = System.currentTimeMillis();
                                System.out.println("Mercado: ✓ Líder reconectado e notificado! Filial " + liderId + " (termo: " + termo + ")");
                                return;
                            }
                        } catch (Exception e2) {
                            filiais[i] = null;
                        }
                    }
                } else {
                    // Tenta conectar filial que ainda não foi conectada
                    try {
                        filiais[i] = getFilial(urlsFiliais[i]);
                        if (filiais[i].getId() == liderId) {
                            filialLider = filiais[i];
                            termoLider = termo;
                            ultimoHeartbeatLider = System.currentTimeMillis();
                            System.out.println("Mercado: ✓ Líder conectado e notificado! Filial " + liderId + " (termo: " + termo + ")");
                            return;
                        }
                    } catch (Exception e) {
                        // Continua
                    }
                }
            }
        } else {
            // Termo menor - mensagem antiga, ignora silenciosamente
            // A filial deve descobrir o termo atual consultando outras filiais
            // Não logamos para evitar spam
        }
    }
    
    /**
     * Tenta reconectar uma filial específica se necessário.
     * Usado quando uma filial específica é necessária (ex: o líder).
     */
    private Filial tentarReconectarFilial(int index) {
        try {
            filiais[index] = getFilial(urlsFiliais[index]);
            filiais[index].getId(); // Testa conexão
            return filiais[index];
        } catch (Exception e) {
            filiais[index] = null;
            return null;
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
        int segundo_atual = LocalTime.now().getSecond();
        int tamanho = restaurantesClientes.get(restaurante).size();
        
        if (tamanho == 0) {
            return 0; // Não há pedidos
        }

        // Pega o último pedido (índice é 0-based, então tamanho - 1)
        Pedido pedido = restaurantesClientes.get(restaurante).get(tamanho - 1);

        // Calcula diferença de segundos, tratando virada de minuto
        int diferencaSegundos = segundo_atual - pedido.segundo_inicial;
        if (diferencaSegundos < 0) {
            // Virada de minuto: segundo_atual < segundo_inicial
            diferencaSegundos = (60 - pedido.segundo_inicial) + segundo_atual;
        }

        int rest = Math.max(0, pedido.tempo_entrega - diferencaSegundos);

        if(rest == 0) pedido.entregue = true;
        return rest;
    }

}
