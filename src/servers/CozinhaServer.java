package servers;

import implementacoes.CozinhaImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CozinhaServer {
    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.createRegistry(1099);
        CozinhaImpl cozinha = new CozinhaImpl();
        registry.rebind("ServerCozinha", cozinha);
        System.out.println("Cozinha server ready");
    }
}
