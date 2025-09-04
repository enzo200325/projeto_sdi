import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RestauranteServer {
    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost");
        RestauranteImpl restaurante = new RestauranteImpl();
        registry.rebind("ServerRestaurante", restaurante);
        System.out.println("Servers iniciado com sucesso");
    }
}
