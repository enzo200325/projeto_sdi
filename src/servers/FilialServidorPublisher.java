package servers;

import implementacoes.FilialImpl;
import javax.xml.ws.Endpoint;

public class FilialServidorPublisher {
    public static void main(String[] args){
        System.out.println("Publicando servidores da Filial");
        //Endpoint.publish("http://127.0.0.1:9876/mercado", new FilialImpl());
        //Endpoint.publish("http://127.0.0.1:9875/mercado", new FilialImpl());
        //Endpoint.publish("http://127.0.0.1:9874/mercado", new FilialImpl());
    }
}
