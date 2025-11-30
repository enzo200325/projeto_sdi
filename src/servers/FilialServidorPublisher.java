package servers;

import implementacoes.FilialImpl;
import interfaces.Filial;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;

public class FilialServidorPublisher {
    public static void main(String[] args) throws InterruptedException, MalformedURLException {
        System.out.println("Publicando servidores da Filial");

        String[] urls = {"http://127.0.0.1:9876/filial", "http://127.0.0.1:9875/filial", "http://127.0.0.1:9874/filial"};
        String[] adj = {"http://127.0.0.1:9875/filial", "http://127.0.0.1:9874/filial","http://127.0.0.1:9876/filial"};

        FilialImpl[] filiais = new FilialImpl[3];

        for(int i = 0; i < urls.length; i++){
            filiais[i] = new FilialImpl(urls[i]);
            Endpoint.publish(urls[i], filiais[i]);
        }

        Thread.sleep(1000);

        for(int i = 0; i < urls.length; i++){
            URL wsdl = new URL( adj[i] + "?wsdl");
            QName qname = new QName("http://implementacoes/", "FilialImplService");
            Service service = Service.create(wsdl, qname);
            Filial filial = service.getPort(Filial.class);
            filiais[i].setNext(filial);
        }

        System.out.println("Antes da eleição: ");
        for(int i = 0; i < urls.length; i++) System.out.println(filiais[i].getLider());

        System.out.println("Chamando election na filial inicial...");
        URL wsdl = new URL(urls[0] + "?wsdl");
        QName qname = new QName("http://implementacoes/", "FilialImplService");
        Service service = Service.create(wsdl, qname);
        Filial filialInicial = service.getPort(Filial.class);
        filialInicial.election(filialInicial.getId(), filialInicial.getId());

        System.out.println("Eleição enviada.");

        System.out.println("Resultados: ");
        for(int i = 0; i < urls.length; i++) System.out.println(filiais[i].getLider());
    }


}
