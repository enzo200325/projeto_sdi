package servers;

import implementacoes.MercadoServidorImpl;

import javax.xml.ws.Endpoint;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class MercadoServidorPublisher {
    public static void main(String[] args) throws RemoteException, NotBoundException, MalformedURLException {
        System.out.println("Publicando servidor do Mercado");
        String url = "http://127.0.0.1:9876/mercado";
        Endpoint.publish(url, new MercadoServidorImpl(url));
    }
}
