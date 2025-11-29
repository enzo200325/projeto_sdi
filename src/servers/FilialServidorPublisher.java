package servers;

import implementacoes.FilialImpl;
import interfaces.Filial;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.net.URL;
import java.util.Vector;

public class FilialServidorPublisher {
    public static void main(String[] args){
        System.out.println("Publicando servidores da Filial");

        String[] my = {"http://127.0.0.1:9876/filial", "http://127.0.0.1:9875/filial", "http://127.0.0.1:9874/filial"};
        String[] adj = {"http://127.0.0.1:9875/filial", "http://127.0.0.1:9874/filial","http://127.0.0.1:9876/filial"};
        int id = 0;

        for(int i = 0; i < my.length; i++, id++){
            Endpoint.publish(my[i], new FilialImpl(my[i],adj[i], id));
        }

        try {
            Thread.sleep(1500);

            System.out.println("Iniciando teste de eleição...");
            iniciarEleicao("http://127.0.0.1:9876/filial");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void iniciarEleicao(String url) throws Exception {
        URL wsdl = new URL(url + "?wsdl");
        QName qname = new QName("http://implementacoes/", "FilialImplService");

        Service service = Service.create(wsdl, qname);
        Filial filial = service.getPort(Filial.class);

        System.out.println("Chamando election(" + filial.get_id() + ") na filial inicial...");
        filial.election(filial.get_id());
        System.out.println("Eleição enviada.");
    }

}
