package classes;

public class Pedido {
    public String[] produtos;
    public int tempo_entrega;
    public int segundo_inicial;
    public boolean entregue;

    public Pedido(String[] produtos, int tempo_preparo, int segundo_inicial){
            this.produtos = produtos;
            this.tempo_entrega = tempo_preparo;
            this.segundo_inicial = segundo_inicial;
            entregue = false;
    }
}
