package Cardapio;

public class Prato {
    public String nome;
    public int idx;
    public double valor;
    public Prato(String nome, int idx, double valor) {
        this.nome = nome;
        this.idx = idx;
        this.valor = valor;
    }
    public Prato(String prato_str) {
        String[] campos = prato_str.split(",");
        this.nome = campos[1];
        this.idx = Integer.parseInt(campos[0]);
        this.valor = Double.parseDouble(campos[2]);
    }
}
