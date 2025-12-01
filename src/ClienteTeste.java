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
        
        // Teste 3: Pedido grande com múltiplos produtos diferentes para forçar uso do mercado
        System.out.println("   Teste 3: Pedido grande para acionar mercado e consenso distribuído...");
        System.out.println("   (Fazendo pedido com 10 produtos diferentes para esgotar estoque e acionar filiais)");
        
        // Primeiro, esgota o estoque fazendo pedidos repetidos dos primeiros produtos
        System.out.println("   Esgotando estoque dos primeiros produtos...");
        for (int i = 0; i < 5; i++) {
            String[] pedidoEsgotar = {cardapio[0]}; // Primeiro produto
            String resultado = restaurante.fazerPedido(comanda - 1, pedidoEsgotar);
            System.out.println("   Pedido " + (i + 1) + " para esgotar estoque: " + resultado);
            Thread.sleep(200);
        }
        
        // Agora faz pedido grande com múltiplos produtos diferentes
        // Seleciona 10 produtos diferentes do cardápio
        int numProdutos = Math.min(10, cardapio.length);
        String[] pedidoGrande = new String[numProdutos];
        System.out.println("\n   Produtos no pedido grande:");
        for (int i = 0; i < numProdutos; i++) {
            pedidoGrande[i] = cardapio[i];
            Prato prato = new Prato(cardapio[i]);
            System.out.println("   - " + prato.nome);
        }
        
        System.out.println("\n   Enviando pedido grande ao restaurante...");
        String resultado3 = restaurante.fazerPedido(comanda - 1, pedidoGrande);
        System.out.println("   Resultado: " + resultado3);
        
        if (resultado3.contains("filiais") || resultado3.contains("mercado")) {
            System.out.println("   ✓ Mercado/Filiais foram acionados!");
            System.out.println("   → Observe os logs das filiais para ver o consenso distribuído em ação!");
        } else {
            System.out.println("   ⚠ Mercado/Filiais NÃO foram acionados (restaurante ainda tinha estoque)");
        }
        
        Thread.sleep(3000); // Aguarda um pouco para ver os logs do consenso
        
        // Consulta valor da comanda
        System.out.println("4. Consultando valor da comanda...");
        float valor = restaurante.valorComanda(comanda - 1);
        System.out.println("   Valor total: R$ " + String.format("%.2f", valor) + "\n");
        
        // Tenta fechar comanda (fica tentando até conseguir)
        System.out.println("5. Tentando fechar comanda...");
        int tentativas = 0;
        while (true) {
            int tempoRestante = restaurante.fecharComanda(comanda - 1);
            
            if (tempoRestante == 0) {
                // Pode fechar!
                System.out.println("   ✓ Comanda fechada com sucesso!");
                System.out.println("   Total pago: R$ " + String.format("%.2f", valor));
                break;
            } else {
                // Ainda está em preparo
                tentativas++;
                System.out.println("   ⏳ Pedidos ainda em preparo... Tempo restante: " + tempoRestante + " segundos");
                System.out.println("   (Tentativa " + tentativas + " - aguardando...)");
                
                // Aguarda um pouco antes de tentar novamente
                // Aguarda o tempo mínimo entre tentativas (1 segundo) ou o tempo restante, o que for menor
                int tempoAguardar = Math.min(1000, tempoRestante * 1000);
                Thread.sleep(tempoAguardar);
            }
        }
        
        System.out.println("\n=== Teste concluído ===");
    }
}

