package implementacoes;

import classes.Pedido;
import classes.Prato;
import classes.Comanda;
import interfaces.Cozinha;
import interfaces.Restaurante;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.URL;
import interfaces.MercadoServidor;

public class RestauranteImpl extends UnicastRemoteObject implements Restaurante {
    Registry registry;
    Cozinha cozinha;
    String[] cardapio;
    ArrayList<Comanda> comandas;
    Map<Integer, Integer> mapaPedidos;
    Map<String, Integer> mapaEstoque;

    ArrayList<Integer> id_pedidos;

    URL url;
    QName qname;
    Service service;
    MercadoServidor mercado;

    public RestauranteImpl() throws RemoteException, NotBoundException, MalformedURLException {
        super();
        registry = LocateRegistry.getRegistry("localhost");
        cozinha = (Cozinha) registry.lookup("ServerCozinha");

        url = new URL("http://127.0.0.1:9000/mercado?wsdl");
        qname = new QName("http://implementacoes/", "MercadoServidorImplService");
        service = Service.create(url, qname);

        mapaEstoque = new HashMap<>();
        cardapio = buildCardapio();
        comandas = new ArrayList<>();
        mapaPedidos = new HashMap<>();
        mercado = service.getPort(MercadoServidor.class);

        id_pedidos = new ArrayList<>();
    }

    public String[] buildCardapio (){
        int idx = 0; cardapio = new String[100];
        try (Scanner scanner = new Scanner (new File("src/cardapio/menu_restaurante.csv"))){
            scanner.nextLine();
            while(scanner.hasNextLine()){
                String linha = scanner.nextLine();
                cardapio[idx] = linha;
                String[] partes = linha.split(",");
                String nomePrato = partes[1].trim();

                // tudo começa com 3 no estoque
                mapaEstoque.put(nomePrato, 3);
                idx++;
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return cardapio;
    }

    @Override
    public int novaComanda(String nome, int mesa) throws RemoteException {
        Comanda  comanda = new Comanda(nome, mesa);
        comandas.add(comanda);
        return comandas.size();
    }
    @Override
    public String[] consultarCardapio() throws RemoteException {
        return cardapio;
    }
    @Override
    public String fazerPedido(int comanda, String[] pedidos) throws RemoteException {
        System.out.println("\n=== Restaurante: Recebendo pedido para comanda " + comanda + " ===");
        System.out.println("Restaurante: Produtos solicitados: " + java.util.Arrays.toString(pedidos));
        
        ArrayList<String> nao_temos = new ArrayList<>();
        ArrayList<Prato> para_pedir =  new ArrayList<>();
        
        for (String pedido : pedidos) {
            Prato prato = new Prato(pedido);
            Integer estoqueAtual = mapaEstoque.get(prato.nome);
            
            if (estoqueAtual == null || estoqueAtual <= 0){
                nao_temos.add(prato.nome);
                System.out.println("Restaurante: Sem estoque de " + prato.nome + 
                                 (estoqueAtual == null ? " (não existe no estoque)" : " (estoque: " + estoqueAtual + ")"));
            } else {
                System.out.println("Restaurante: Tem estoque de " + prato.nome + " (quantidade: " + estoqueAtual + ")");
            }
            para_pedir.add(prato);
        }
        
        if (nao_temos.isEmpty()) {
            // Temos tudo em estoque, processa normalmente
            System.out.println("Restaurante: Todos os produtos estão em estoque. Processando localmente...");
            for (Prato prato : para_pedir) {
                comandas.get(comanda).addPedido(prato);
                int estoqueAntes = mapaEstoque.get(prato.nome);
                mapaEstoque.compute(prato.nome, (k, qtdAtual) -> qtdAtual - 1);
                int estoqueDepois = mapaEstoque.get(prato.nome);
                System.out.println("Restaurante: " + prato.nome + " - estoque: " + estoqueAntes + " → " + estoqueDepois);
            }
            int preparo_id = cozinha.novoPreparo(comanda, pedidos);
            mapaPedidos.put(comanda, preparo_id);

            System.out.println("Restaurante: Pedido processado localmente (sem acionar mercado)\n");
            return "Pedido feito, por favor aguarde";
        }
        else {
            // Não temos estoque, pede ao mercado (que coordena com as filiais)
            System.out.println("Restaurante: Faltam " + nao_temos.size() + " produto(s). Acionando mercado...");
            System.out.println("Restaurante: Produtos faltando: " + nao_temos);
            
            String mostrar = "Estamos sem ";
            int pedidoId = mercado.cadastrarPedido("Restaurante");
            System.out.println("Restaurante: Pedido cadastrado no mercado com ID: " + pedidoId);
            System.out.println("Restaurante: Enviando pedido ao mercado...");
            
            boolean sucesso = mercado.comprarProdutos(pedidoId, nao_temos.toArray(new String[0]));
            
            if (sucesso) {
                // Mercado conseguiu atender (via filiais), adiciona ao estoque e processa
                System.out.println("Restaurante: ✓ Mercado conseguiu atender! Adicionando produtos ao estoque...");
                for (String produto : nao_temos) {
                    int estoqueAntes = mapaEstoque.getOrDefault(produto, 0);
                    mapaEstoque.put(produto, estoqueAntes + 1);
                    System.out.println("Restaurante: " + produto + " - estoque: " + estoqueAntes + " → " + (estoqueAntes + 1));
                }
                
                // Processa todos os pedidos
                System.out.println("Restaurante: Processando todos os pedidos...");
                for (Prato prato : para_pedir) {
                    comandas.get(comanda).addPedido(prato);
                    int estoqueAntes = mapaEstoque.get(prato.nome);
                    mapaEstoque.compute(prato.nome, (k, qtdAtual) -> qtdAtual - 1);
                    int estoqueDepois = mapaEstoque.get(prato.nome);
                    System.out.println("Restaurante: " + prato.nome + " - estoque: " + estoqueAntes + " → " + estoqueDepois);
                }
                int preparo_id = cozinha.novoPreparo(comanda, pedidos);
                mapaPedidos.put(comanda, preparo_id);
                
                System.out.println("Restaurante: ✓ Pedido completo processado com produtos do mercado!\n");
                return "Pedido feito com produtos do mercado (filiais), por favor aguarde";
            } else {
                // Mercado não conseguiu atender
                System.out.println("Restaurante: ✗ Mercado não conseguiu atender o pedido");
                for (String falta : nao_temos) {
                    mostrar += falta;
                    mostrar += ", ";
                }
                mostrar += " (mercado não conseguiu atender)";
                System.out.println("Restaurante: " + mostrar + "\n");
                return mostrar;
            }
        }
    }
    
    @Override
    public float valorComanda(int comanda) throws RemoteException {
        float valor = 0;
        for (Prato prato : comandas.get(comanda).pedidos) {
            valor += prato.valor;
        }
        return valor;
    }
    @Override
    public int fecharComanda(int comanda) throws RemoteException {
        Integer preparoId = mapaPedidos.get(comanda);
        
        // Se não há pedido associado à comanda, pode fechar (comanda vazia ou pedido falhou)
        if (preparoId == null) {
            System.out.println("Comanda " + comanda + " não tem pedidos em preparo. Pode fechar.");
            return 0; // 0 = pode fechar
        }
        
        int tempoRestante = cozinha.tempoPreparo(preparoId);
        
        if (tempoRestante == 0) {
            // Pedido pronto, pode fechar
            mapaPedidos.remove(comanda);
            return 0; // 0 = pode fechar
        } else {
            // Ainda está em preparo, retorna tempo restante
            return tempoRestante;
        }
    }
}
