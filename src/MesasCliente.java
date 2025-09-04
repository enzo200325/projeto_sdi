import Interfaces.Restaurante;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class MesasCliente {
    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost");
        Restaurante restaurante = (Restaurante) registry.lookup("ServerRestaurante");

        // Create comanda
        int comandaId = restaurante.novaComanda("João", 5);
        System.out.println("Nova comanda ID: " + comandaId);

        // Show menu
        String[] cardapio = restaurante.consultarCardapio();
        System.out.println("Cardápio:");
        for (String prato : cardapio) {
            if (prato != null) System.out.println(prato);
        }

        // Simulate order
        String[] pedidos = { cardapio[0], cardapio[1] }; // pick first two dishes
        restaurante.fazerPedido(comandaId - 1, pedidos); // comanda index is size()-1
        System.out.println("Pedidos enviados.");

        // Calculate total
        float total = restaurante.valorComanda(comandaId - 1);
        System.out.println("Total comanda: R$" + total);
    }
}
