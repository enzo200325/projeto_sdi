import Interfaces.Cozinha;
import Interfaces.Restaurante;

import java.io.File;
import java.io.FileNotFoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

public class RestauranteImpl extends UnicastRemoteObject implements Restaurante {
    Registry registry;
    Cozinha cozinha;
    int comanda;
    String[] cardapio;

    protected RestauranteImpl() throws RemoteException, NotBoundException {
        super();
        registry = LocateRegistry.getRegistry("localhost");
        cozinha = (Cozinha) registry.lookup("ServerCozinha");
        comanda = 0;
        cardapio = buildCardapio();
    }

    public String[] buildCardapio (){
        int idx = 0; cardapio = new String[100];
        try (Scanner scanner = new Scanner (new File("src/cardapio/menu_restaurante.csv"))){
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
       return comanda++;
    }
    @Override
    public String[] consultarCardapio() throws RemoteException {
        return cardapio;
    }
    @Override
    public String fazerPedido(int comanda, String[] pedido) throws RemoteException {
        //cozinha.novoPreparo(comanda, pedido);
        return null;
    }
    @Override
    public float valorComanda(int comanda) throws RemoteException {
        return 0;
    }
    @Override
    public boolean fecharComanda(int comanda) throws RemoteException {
        return false;
    }
}
