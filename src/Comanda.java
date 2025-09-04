import Cardapio.Prato;

import java.util.ArrayList;

public class Comanda {
    String nome;
    int mesa;
    ArrayList<Prato> pedidos;
    Comanda(String nome, int mesa) {
        this.nome = nome;
        this.mesa = mesa;
        this.pedidos = new ArrayList<>();
    }

    public void addPedido(Prato prato) {
        this.pedidos.add(prato);
    }
}
