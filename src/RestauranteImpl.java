import Interfaces.Cozinha;
import Interfaces.Restaurante;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class RestauranteImpl extends UnicastRemoteObject implements Restaurante {
    Registry registry;
    Cozinha cozinha;

    protected RestauranteImpl() throws RemoteException, NotBoundException {
        super();
        registry = LocateRegistry.getRegistry("localhost");
        cozinha = (Cozinha) registry.lookup("ServerCozinha");
    }


    @Override
    public int novaComanda(String nome, int mesa) throws RemoteException {

       return 100;
    }
    @Override
    public String[] consultarCardapio() throws RemoteException {
        return null;
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
