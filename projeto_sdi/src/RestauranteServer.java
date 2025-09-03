import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RestauranteServer {
    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.createRegistry(1099);

        CozinhaImpl cozinha = new CozinhaImpl();
        registry.rebind("ServerCozinha", cozinha);

        RestauranteImpl restaurante = new RestauranteImpl();
        registry.rebind("ServerRestaurante", restaurante);

        String[] a = restaurante.consultarCardapio();
        for(String x : a) System.out.println(x);

        System.out.println("Servers iniciado com sucesso");
    }
}
