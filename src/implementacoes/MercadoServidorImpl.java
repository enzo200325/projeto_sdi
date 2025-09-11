package implementacoes;

import classes.Pedido;
import interfaces.MercadoServidor;
import javax.jws.WebService;
import java.util.*;

@WebService(endpointInterface = "interfaces.MercadoServidor")
public class MercadoServidorImpl implements MercadoServidor {
    private Map<Integer, List<Pedido>> restaurantesClientes = new HashMap<>();
    private Map<Integer, String> idToRestaurantes = new HashMap<>();
    private Random r = new Random();

    private int codigosPedidos = 0;

    public int cadastrarPedido(String restaurante) {
        // Adicionar os pedidos a serem feitos
        restaurantesClientes.put(this.codigosPedidos, new ArrayList<>());
        idToRestaurantes.put(codigosPedidos, restaurante);
        return codigosPedidos++;
    }

    public boolean comprarProdutos(int restaurante, String[] produtos) {
        // Verificar diferença entre string acima e int aqui para o restaurante
        if(!restaurantesClientes.containsKey(restaurante)) return false;
        // Isso é bomba, mas é só pra deixa um placeholder para uma suposta lógica
       // else pedidos.get(restaurante).add(produtos);

        Pedido pedido = new Pedido(produtos);
        restaurantesClientes.get(restaurante).add(pedido);
        return true;
    }

    public int tempoEntrega(int restaurante) {
        return r.nextInt(9) + 1;
    }

}
