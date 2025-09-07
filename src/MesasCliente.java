import classes.Prato;
import interfaces.Restaurante;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.LinkedList;
import java.util.Scanner;

public class MesasCliente {
    public static Scanner s = new Scanner(System.in);
    public static Registry registry;
    public static Restaurante restaurante;
    public static int qntdComandas;
    public static String[] cardapio;

    public static void main(String[] args) throws Exception {
        registry = LocateRegistry.getRegistry("localhost");
        restaurante = (Restaurante) registry.lookup("ServerRestaurante");

        boolean ok = true;
        int opcao;

        while(ok){
            System.out.println("1 - Nova comanda\n2 - Consultar Cardápio\n3 - Fazer pedido\n4 - Fechar a comanda\n");
            opcao = Integer.parseInt(s.nextLine());
            switch(opcao) {
                case 1:
                    qntdComandas = novaComanda();
                    break;
                case 2:
                    consultaCardapio();
                    break;
                case 3:
                    fazerPedido();
                    break;
                case 4:
                    if (fecharComanda()) ok = false;
                    break;
            }
        }

    }
    public static int novaComanda() throws RemoteException {
        System.out.println("Informe o nome da pessoa: ");
        String nome = s.nextLine();

        System.out.println("Informe o número da mesa: ");
        int mesa = Integer.parseInt(s.nextLine());
        return restaurante.novaComanda(nome,mesa);
    }

    public static void consultaCardapio() throws RemoteException {
        cardapio = restaurante.consultarCardapio();
        int left_it = 0;
        while (left_it<cardapio.length){
            for (int i = left_it; i < Math.min(left_it + 20, cardapio.length); i++){
                System.out.println(cardapio[i]);
                Prato prato = new Prato(cardapio[i]);
                System.out.println(prato.idx + " - " + prato.nome + ": " +  prato.valor);
            }
            left_it += 20;
            if (left_it >= cardapio.length) return;
            else {
                System.out.println("\n Digite 1 se deseja olhar mais opções ou 0 caso não queira");
                int opcao = Integer.parseInt(s.nextLine());
                switch (opcao) {
                    case 1:
                        continue;
                    case 0:
                        return;
                }
            }
        }
    }

    public static void fazerPedido() throws RemoteException{
        int op, op1 = 0;
        LinkedList<Integer> v  = new LinkedList<>();
        System.out.println("Fazer pedido para qual comando? Existem " + qntdComandas + " comandas cadastradas.");
        op = Integer.parseInt(s.nextLine());

        if (op < 1 || op > qntdComandas){
            System.out.println("Número de comanda não existe");
            return;
        }

        System.out.println("Quais opções do cardápio (número do prato)? -1 para sair.");
        while(op1 >= 0){
            op1 = Integer.parseInt(s.nextLine());
            if(op1 == -1) break;
            v.add(op1);
        }

        String pedidos[] = new String[v.size()];
        int idx = 0;

        for(int i : v) {
            pedidos[idx] = cardapio[i - 1];
            idx++;
        }

        restaurante.fazerPedido(op - 1, pedidos);
        System.out.println("Pedidos enviados.");
    }

    public static boolean fecharComanda() throws RemoteException{
        System.out.println("Fechar qual comanda?");
        int idx = Integer.parseInt(s.nextLine());

        if(!restaurante.fecharComanda(idx - 1)) {
            System.out.println("Ainda existem pedidos em produção.");
            return false;
        }
        else {
            float total = restaurante.valorComanda(idx - 1);
            System.out.println("Total comanda: R$" + total);
            return true;
        }
    }

}
