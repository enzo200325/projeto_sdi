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
        qname = new QName("http://demo.example.com/", "MercadoServidorImplService");
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
        try (Scanner scanner = new Scanner (new File("cardapio/menu_restaurante.csv"))){
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
        ArrayList<String> nao_temos = new ArrayList<>();
        ArrayList<Prato> para_pedir =  new ArrayList<>();
        for (String pedido : pedidos) {
            Prato prato = new Prato(pedido);
            if (mapaEstoque.get(prato.nome) == null){
                nao_temos.add(prato.nome);
            }
            else {
                if (mapaEstoque.get(prato.nome) <= 0){
                    nao_temos.add(prato.nome);
                }
            }
            para_pedir.add(prato);
        }
        if (nao_temos.isEmpty()) {
            for (Prato prato : para_pedir) {
                comandas.get(comanda).addPedido(prato);
                mapaEstoque.compute(prato.nome, (k, qtdAtual) -> qtdAtual - 1);
            }
            int preparo_id = cozinha.novoPreparo(comanda, pedidos);
            mapaPedidos.put(comanda, preparo_id);

            return "Pedido feito, por favor aguarde";
        }
        else {
            String mostrar = "Estamos sem ";
            id_pedidos.add(mercado.cadastrarPedido("Pedido" + id_pedidos.size()));
            String[] to_array = nao_temos.toArray(new String[0]);
            mercado.comprarProdutos(id_pedidos.get(id_pedidos.size() - 1), to_array);

            for (String falta : nao_temos) {
                mostrar += falta;
                mostrar += ", ";
            }
            return mostrar;

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
    public boolean fecharComanda(int comanda) throws RemoteException {
        int preparoId = mapaPedidos.get(comanda);
        if(cozinha.tempoPreparo(preparoId) == 0) {
            mapaPedidos.remove(preparoId);
            return true;
        } else return false;
    }
}
