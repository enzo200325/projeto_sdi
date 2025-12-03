package servers;

import implementacoes.CozinhaImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CozinhaServer {
    public static void main(String[] args) throws Exception {
        // Porta padrão: 1099, ou pode ser passada como argumento
        int porta = 1099;
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Erro: Porta inválida. Usando porta padrão 1099.");
                porta = 1099;
            }
        }
        
        Registry registry = LocateRegistry.createRegistry(porta);
        CozinhaImpl cozinha = new CozinhaImpl();
        registry.rebind("ServerCozinha", cozinha);
        System.out.println("Cozinha server ready na porta " + porta);
    }
}
