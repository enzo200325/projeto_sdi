package implementacoes;

import interfaces.MercadoServidor;
import javax.jws.WebService;
import java.util.*;

@WebService(endpointInterface = "interfaces.MercadoServidor")
public class MercadoServidorImpl implements MercadoServidor {
    private Map<Integer, List<String>> pedidos = new HashMap<>();
    private Map<Integer, List<Integer>> restaurantesClientes = new HashMap<>();
    private Random r = new Random();

    private int codigosPedidos = 0;

    public int cadastrarPedido(String restaurante) {
        //if(!restaurantesClientes.containsKey(restaurante)) restaurantesClientes.put(restaurante, new ArrayList<>(codigosPedidos));
       // else restaurantesClientes.get(restaurante).add(codigosPedidos);
        // Adicionar os pedidos a serem feitos
        pedidos.put(codigosPedidos, new ArrayList<>());
        return codigosPedidos++;
    }

    public boolean comprarProdutos(int restaurante, String[] produtos) {
        // Verificar diferença entre string acima e int aqui para o restaurante
        if(!restaurantesClientes.containsKey(restaurante)) return false;
        // Isso é bomba, mas é só pra deixa um placeholder para uma suposta lógica
       // else pedidos.get(restaurante).add(produtos);
        return true;
    }

    public int tempoEntrega(int restaurante) {
        return r.nextInt(9) + 1;
    }

}
