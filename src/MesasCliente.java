import Interfaces.Restaurante;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class MesasCliente {
    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost");
        Restaurante restaurante = (Restaurante) registry.lookup("ServerRestaurante");

    }
}
