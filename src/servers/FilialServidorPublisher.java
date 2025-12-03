package servers;

import implementacoes.FilialImpl;
import interfaces.Filial;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;

public class FilialServidorPublisher {

    private static final String[] URLS_FILIAIS = {
            "http://127.0.0.1:9876/filial",
            "http://127.0.0.1:9875/filial",
            "http://127.0.0.1:9874/filial"
    };

    public static void main(String[] args) throws InterruptedException, MalformedURLException {
        if (args.length != 1) {
            System.out.println("Uso: java servers.FilialServidorPublisher <porta>");
            System.out.println("Exemplo: java servers.FilialServidorPublisher 9876");
            return;
        }

        String porta = args[0];
        String myUrl = "http://127.0.0.1:" + porta + "/filial";

        // Descobre índice desta filial na lista
        int myIndex = -1;
        for (int i = 0; i < URLS_FILIAIS.length; i++) {
            if (URLS_FILIAIS[i].equals(myUrl)) {
                myIndex = i;
                break;
            }
        }

        if (myIndex == -1) {
            System.out.println("Porta " + porta + " não está configurada como filial válida.");
            System.out.println("Filiais válidas: 9876, 9875, 9874");
            return;
        }

        System.out.println("Publicando filial na URL: " + myUrl);
        System.out.println("Conectando a todas as outras filiais (topologia mesh)...");

        // Publica esta filial
        FilialImpl filialAtual = new FilialImpl(myUrl);
        Endpoint.publish(myUrl, filialAtual);

        // Conecta a TODAS as outras filiais (mesh topology para Raft)
        int filiaisConectadas = 0;
        while (filiaisConectadas < URLS_FILIAIS.length - 1) {
            for (String url : URLS_FILIAIS) {
                if (url.equals(myUrl)) continue; // Pula a própria URL
                
                try {
                    URL wsdl = new URL(url + "?wsdl");
                    QName qname = new QName("http://implementacoes/", "FilialImplService");
                    Service service = Service.create(wsdl, qname);
                    Filial filial = service.getPort(Filial.class);
                    
                    // Adiciona à lista de filiais conhecidas
                    filialAtual.adicionarFilial(filial, url);
                    filiaisConectadas++;
                    System.out.println("Filial conectada: " + url);
                } catch (Exception e) {
                    System.out.println("Erro ao conectar à filial: " + url + " - " + e.getMessage());
                    // Ainda não está disponível, tenta depois
                }
            }
            
            if (filiaisConectadas < URLS_FILIAIS.length - 1) {
                System.out.println("Aguardando outras filiais... (" + filiaisConectadas + "/" + (URLS_FILIAIS.length - 1) + " conectadas)");
                Thread.sleep(1000);
            }
        }
        
        System.out.println("\n✓ Filial " + filialAtual.getId() + " pronta!");
        System.out.println("  Conectada a " + (filiaisConectadas - 1) + " outras filiais");
        System.out.println("  Usando algoritmo Raft para eleição e consenso");
        System.out.println("  (Eleições serão iniciadas automaticamente quando necessário)\n");
        
        // Mantém o processo vivo
        Thread.sleep(Long.MAX_VALUE);
    }

}
