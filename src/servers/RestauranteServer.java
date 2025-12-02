package servers;

import implementacoes.RestauranteImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RestauranteServer {
    public static void main(String[] args) {
        try {
            System.out.println("Iniciando servidor do Restaurante...");
            System.out.println("Conectando ao registry RMI...");
            // Lê porta RMI de propriedade do sistema ou usa padrão 1099
            String portaRMI = System.getProperty("rmi.port", "1099");
            int porta = Integer.parseInt(portaRMI);
            Registry registry = LocateRegistry.getRegistry("localhost", porta);
            
            System.out.println("Criando implementação do Restaurante...");
            System.out.println("(Isso pode falhar se CozinhaServer ou Mercado não estiverem rodando)");
            RestauranteImpl restaurante = new RestauranteImpl();
            
            System.out.println("Registrando serviço 'ServerRestaurante' no registry...");
            registry.rebind("ServerRestaurante", restaurante);
            System.out.println("✓ Servidor do Restaurante iniciado com sucesso!");
            System.out.println("  Serviço registrado como: ServerRestaurante");
            System.out.println("  Aguardando conexões...\n");
        } catch (java.rmi.NotBoundException e) {
            System.err.println("ERRO: Não foi possível encontrar o servidor da Cozinha.");
            System.err.println("Certifique-se de que o CozinhaServer está rodando!");
            System.err.println("Execute: ./run_cozinha.sh");
            System.exit(1);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("cozinha") || errorMsg.contains("servercozinha")) {
                System.err.println("ERRO: Não foi possível conectar ao servidor da Cozinha.");
                System.err.println("Certifique-se de que o CozinhaServer está rodando!");
                System.err.println("Execute: ./run_cozinha.sh");
            } else if (errorMsg.contains("mercado") || errorMsg.contains("wsdl") || errorMsg.contains("connection")) {
                System.err.println("ERRO: Não foi possível conectar ao Mercado.");
                System.err.println("Certifique-se de que o MercadoServidorPublisher está rodando!");
                System.err.println("Execute: ./run_mercado.sh");
            } else {
                System.err.println("ERRO ao iniciar servidor do Restaurante:");
                System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            e.printStackTrace();
            System.exit(1);
        }
    }
}
