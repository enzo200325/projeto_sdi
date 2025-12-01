import classes.Prato;
import interfaces.Restaurante;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Cliente de teste simplificado para testar pedidos e integração com filiais
 */
public class ClienteTeste {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Cliente de Teste - Sistema de Restaurante ===\n");
        
        // Conecta ao restaurante
        Registry registry = LocateRegistry.getRegistry("localhost");
        Restaurante restaurante = (Restaurante) registry.lookup("ServerRestaurante");
        
        System.out.println("Conectado ao restaurante!\n");
        
        // Cria uma comanda de teste
        System.out.println("1. Criando comanda de teste...");
        int comanda = restaurante.novaComanda("Cliente Teste", 1);
        System.out.println("   Comanda criada: #" + comanda + "\n");
        
        // Consulta cardápio
        System.out.println("2. Consultando cardápio...");
        String[] cardapio = restaurante.consultarCardapio();
        System.out.println("   Cardápio carregado com " + cardapio.length + " itens\n");
        
        // Faz pedidos de teste
        System.out.println("3. Fazendo pedidos de teste...\n");
        
        // Teste 1: Pedido simples (1 prato)
        System.out.println("   Teste 1: Pedindo 1 prato...");
        String[] pedido1 = {cardapio[0]}; // Primeiro prato do cardápio
        Prato prato1 = new Prato(pedido1[0]);
        System.out.println("   Prato: " + prato1.nome);
        String resultado1 = restaurante.fazerPedido(comanda - 1, pedido1);
        System.out.println("   Resultado: " + resultado1 + "\n");
        
        Thread.sleep(1000);
        
        // Teste 2: Pedido múltiplo (3 pratos)
        System.out.println("   Teste 2: Pedindo 3 pratos...");
        String[] pedido2 = {cardapio[0], cardapio[1], cardapio[2]};
        for (String p : pedido2) {
            Prato prato = new Prato(p);
            System.out.println("   - " + prato.nome);
        }
        String resultado2 = restaurante.fazerPedido(comanda - 1, pedido2);
        System.out.println("   Resultado: " + resultado2 + "\n");
        
        Thread.sleep(1000);
        
        // Teste 3: Forçar uso das filiais (fazer muitos pedidos do mesmo prato)
        System.out.println("   Teste 3: Forçando uso das filiais (múltiplos pedidos do mesmo prato)...");
        System.out.println("   (Isso deve esgotar o estoque do restaurante e acionar as filiais)");
        String pratoTeste = cardapio[0];
        Prato pratoInfo = new Prato(pratoTeste);
        System.out.println("   Prato: " + pratoInfo.nome);
        
        // Faz vários pedidos do mesmo prato para esgotar estoque
        for (int i = 0; i < 10; i++) {
            String[] pedido = {pratoTeste};
            String resultado = restaurante.fazerPedido(comanda - 1, pedido);
            System.out.println("   Pedido " + (i + 1) + ": " + resultado);
            if (resultado.contains("filiais") || resultado.contains("mercado")) {
                System.out.println("   ✓ Filiais/Mercado foram acionados!\n");
                break;
            }
            Thread.sleep(500);
        }
        
        Thread.sleep(2000);
        
        // Consulta valor da comanda
        System.out.println("4. Consultando valor da comanda...");
        float valor = restaurante.valorComanda(comanda - 1);
        System.out.println("   Valor total: R$ " + String.format("%.2f", valor) + "\n");
        
        // Tenta fechar comanda
        System.out.println("5. Tentando fechar comanda...");
        boolean fechou = restaurante.fecharComanda(comanda - 1);
        if (fechou) {
            System.out.println("   ✓ Comanda fechada com sucesso!");
            System.out.println("   Total pago: R$ " + String.format("%.2f", valor));
        } else {
            System.out.println("   ⚠ Comanda não pode ser fechada (ainda há pedidos em preparo)");
        }
        
        System.out.println("\n=== Teste concluído ===");
    }
}

