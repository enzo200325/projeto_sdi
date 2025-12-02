import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class testar_rmi {
    public static void main(String[] args) {
        try {
            String portaRMI = System.getProperty("rmi.port", "1099");
            int porta = Integer.parseInt(portaRMI);
            System.out.println("Conectando ao registry RMI na porta " + porta + "...");
            
            Registry registry = LocateRegistry.getRegistry("localhost", porta);
            
            System.out.println("✓ Registry encontrado");
            System.out.println("Listando serviços registrados...");
            
            String[] services = registry.list();
            System.out.println("Serviços encontrados: " + services.length);
            for (String service : services) {
                System.out.println("  - " + service);
            }
            
            if (services.length == 0) {
                System.out.println("\n❌ Nenhum serviço registrado!");
                System.out.println("   O RestauranteServer pode não ter conseguido registrar o serviço.");
            } else {
                boolean encontrou = false;
                for (String service : services) {
                    if (service.equals("ServerRestaurante")) {
                        encontrou = true;
                        System.out.println("\n✓ ServerRestaurante encontrado!");
                        try {
                            Object obj = registry.lookup("ServerRestaurante");
                            System.out.println("✓ Serviço acessível: " + obj.getClass().getName());
                        } catch (Exception e) {
                            System.err.println("❌ Erro ao acessar serviço: " + e.getMessage());
                        }
                        break;
                    }
                }
                if (!encontrou) {
                    System.out.println("\n❌ ServerRestaurante NÃO encontrado na lista!");
                    System.out.println("   O servidor pode não ter conseguido registrar o serviço.");
                }
            }
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


