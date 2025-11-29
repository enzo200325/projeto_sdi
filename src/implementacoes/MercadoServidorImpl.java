package implementacoes;

import classes.Pedido;
import interfaces.Filial;
import interfaces.MercadoServidor;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.time.LocalTime;
import java.util.*;

import static java.util.List.*;

@WebService(
        endpointInterface = "interfaces.MercadoServidor" // ,
        //targetNamespace = "implementacoes"
)
public class MercadoServidorImpl implements MercadoServidor {
    private Map<Integer, List<Pedido>> restaurantesClientes;
    private Map<Integer, String> idToRestaurantes;
    private Random r;
    //private int lider_idx;

    private String urlLider;
    private ArrayList<String> urls_filiais;
    String my_url;

    public MercadoServidorImpl(String url) throws RemoteException {
        restaurantesClientes =  new HashMap<>();
        idToRestaurantes = new HashMap<>();
        r =  new Random();
        my_url = url;
    }

    private Filial getFilial(String url){
        try {
            URL wsdl = new URL(url + "?wsdl");
            QName qname = new QName("http://implementacoes/", "FilialImplService");
            Service service = Service.create(wsdl, qname);
            return service.getPort(Filial.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private int codigosPedidos = 0;

    public int cadastrarPedido(String restaurante) {
        restaurantesClientes.put(this.codigosPedidos, new ArrayList<>());
        idToRestaurantes.put(codigosPedidos, restaurante);

        System.out.println("Cadastrando restaurante " + restaurante + " com o id " + codigosPedidos);
        return codigosPedidos++;
    }

    public boolean comprarProdutos(int restaurante, String[] produtos) {
        if(!restaurantesClientes.containsKey(restaurante)) return false;
        // Só deixa comprar se ultimo pedido ja foi entregue
        for(Pedido p : restaurantesClientes.get(restaurante)) if(!p.entregue) return false;

        System.out.println("Restaurante " + restaurante + " com o id " + codigosPedidos + " pediu o seguinte: ");
        for (int i = 0; i < produtos.length; i++) {
            System.out.println("Produto " + (i + 1) + ": " + produtos[i]);
        }

        int segundo_atual = LocalTime.now().getSecond(), tempo_entrega = r.nextInt(9) + 1;
        Pedido pedido = new Pedido(produtos, tempo_entrega, segundo_atual);

        restaurantesClientes.get(restaurante).add(pedido);
        return true;
    }

    public int tempoEntrega(int restaurante) {
        int segundo_atual = LocalTime.now().getSecond(), tamanho = restaurantesClientes.get(restaurante).size();

        Pedido pedido = restaurantesClientes.get(restaurante).get(tamanho);

        int rest = Math.max(0, pedido.tempo_entrega - (segundo_atual - pedido.segundo_inicial));

        if(rest == 0) pedido.entregue = true;
        return rest;
    }

}
