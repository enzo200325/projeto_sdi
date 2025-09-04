import Cardapio.Prato;
import Interfaces.Cozinha;
import Interfaces.Restaurante;

import java.io.File;
import java.io.FileNotFoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class RestauranteImpl extends UnicastRemoteObject implements Restaurante {
    Registry registry;
    Cozinha cozinha;
    String[] cardapio;
    ArrayList<Comanda> comandas;
    Map<Integer, Integer> mapaPedidos;

    protected RestauranteImpl() throws RemoteException, NotBoundException {
        super();
        registry = LocateRegistry.getRegistry("localhost");
        cozinha = (Cozinha) registry.lookup("ServerCozinha");
        cardapio = buildCardapio();
        comandas = new ArrayList<>();
        mapaPedidos = new HashMap<>();
    }

    public String[] buildCardapio (){
        int idx = 0; cardapio = new String[100];
        try (Scanner scanner = new Scanner (new File("src/Cardapio/menu_restaurante.csv"))){
            scanner.nextLine();
            while(scanner.hasNextLine()){
                String linha = scanner.nextLine();
                cardapio[idx] = linha;
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
        for (String pedido : pedidos) {
            Prato prato = new Prato(pedido);
            comandas.get(comanda).addPedido(prato);
        }
        int preparo_id = cozinha.novoPreparo(comanda, pedidos);

        mapaPedidos.put(comanda, preparo_id);

        return null;
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
