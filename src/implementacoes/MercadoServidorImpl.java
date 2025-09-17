package implementacoes;

import classes.Pedido;
import interfaces.MercadoServidor;
import javax.jws.WebService;
import java.time.LocalTime;
import java.util.*;

@WebService(endpointInterface = "interfaces.MercadoServidor")
public class MercadoServidorImpl implements MercadoServidor {
    private Map<Integer, List<Pedido>> restaurantesClientes = new HashMap<>();
    private Map<Integer, String> idToRestaurantes = new HashMap<>();
    private Random r = new Random();

    private int codigosPedidos = 0;

    public int cadastrarPedido(String restaurante) {
        restaurantesClientes.put(this.codigosPedidos, new ArrayList<>());
        idToRestaurantes.put(codigosPedidos, restaurante);
        return codigosPedidos++;
    }

    public boolean comprarProdutos(int restaurante, String[] produtos) {
        if(!restaurantesClientes.containsKey(restaurante)) return false;
        // Só deixa comprar se ultimo pedido ja foi entregue
        for(Pedido p : restaurantesClientes.get(restaurante)) if(!p.entregue) return false;

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
