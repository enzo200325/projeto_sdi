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

        // URL da próxima filial no anel
        String nextUrl = URLS_FILIAIS[(myIndex + 1) % URLS_FILIAIS.length];

        System.out.println("Publicando filial na URL: " + myUrl);
        System.out.println("Próxima filial no anel: " + nextUrl);

        // Publica esta filial
        FilialImpl filialAtual = new FilialImpl(myUrl);
        Endpoint.publish(myUrl, filialAtual);

        // Aguarda as demais filiais subirem e tenta conectar em loop
        Filial filialNext = null;
        while (filialNext == null) {
            try {
                URL wsdlNext = new URL(nextUrl + "?wsdl");
                QName qname = new QName("http://implementacoes/", "FilialImplService");
                Service service = Service.create(wsdlNext, qname);
                filialNext = service.getPort(Filial.class);
                filialAtual.setNext(filialNext);
                filialAtual.setNextUrl(nextUrl); // Armazena URL para reconexão
                System.out.println("Filial publicada e conectada ao anel.");
            } catch (Exception e) {
                System.out.println("Ainda não consegui conectar na próxima filial (" + nextUrl + "). Tentando novamente em 1s...");
                Thread.sleep(1000);
            }
        }

        // As filiais agora iniciam eleição automaticamente quando detectam problemas
        // ou podem iniciar manualmente quando necessário
        // Removida eleição automática do início - filiais são mais resilientes agora
        
        System.out.println("Filial pronta para participar de eleições e consensos.");
        System.out.println("(Eleições serão iniciadas automaticamente quando necessário)");
        // Mantém o processo vivo
        Thread.sleep(Long.MAX_VALUE);
    }

}
