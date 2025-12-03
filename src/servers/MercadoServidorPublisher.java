package servers;

import implementacoes.MercadoServidorImpl;

import javax.xml.ws.Endpoint;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class MercadoServidorPublisher {
    public static void main(String[] args) throws RemoteException, NotBoundException, MalformedURLException {
        System.out.println("Publicando servidor do Mercado");
        // Mercado na porta 9000 para não conflitar com as filiais
        String url = "http://127.0.0.1:9000/mercado";
        Endpoint.publish(url, new MercadoServidorImpl(url));
        System.out.println("Mercado publicado em: " + url);
    }
}
