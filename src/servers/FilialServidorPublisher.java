package servers;

import implementacoes.FilialImpl;
import javax.xml.ws.Endpoint;

public class FilialServidorPublisher {

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1) {
            System.out.println("Uso: java servers.FilialServidorPublisher <porta> [seed_url]");
            System.out.println();
            System.out.println("Exemplos:");
            System.out.println("  Primeira filial (seed do cluster):");
            System.out.println("    java servers.FilialServidorPublisher 9876");
            System.out.println();
            System.out.println("  Filiais seguintes (conectam ao seed):");
            System.out.println("    java servers.FilialServidorPublisher 9875 http://127.0.0.1:9876/filial");
            return;
        }

        String porta = args[0];
        String seedUrl = args.length > 1 ? args[1] : null;
        String myUrl = "http://127.0.0.1:" + porta + "/filial";

        System.out.println("Publicando filial na URL: " + myUrl);
        if (seedUrl != null) {
            System.out.println("Seed URL: " + seedUrl);
        } else {
            System.out.println("Sem seed - esta filial será o seed do cluster");
        }

        // Cria e publica a filial
        FilialImpl filial = new FilialImpl(myUrl, seedUrl);
        Endpoint.publish(myUrl, filial);

        System.out.println("✓ Filial " + filial.getId() + " online!");
        
        if (seedUrl == null) {
            System.out.println("  Aguardando timeout para se tornar líder...\n");
        } else {
            System.out.println("  Conectando ao cluster via seed...\n");
        }
        
        // Mantém o processo vivo
        Thread.sleep(Long.MAX_VALUE);
    }
}
