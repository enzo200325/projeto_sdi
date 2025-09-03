import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CozinhaServer {
    public static void main(String[] args) throws Exception {
        CozinhaImpl cozinha = new CozinhaImpl();
        System.out.println("Server Interfaces.Restaurante iniciado com sucesso");
    }
}
