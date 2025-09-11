package servers;

import implementacoes.MercadoServidorImpl;

import javax.xml.ws.Endpoint;

public class MercadoServidorPublisher {
    public static void main(String[] args){
        System.out.println("Publicando servidor do Mercado");
        Endpoint.publish("http://127.0.0.1:9876/mercado", new MercadoServidorImpl());
    }
}
