package Classes;

import java.rmi.RemoteException;

public class Preparo {
    public int comanda;
    public int tempo_preparo;
    public int segundo_inicial;
    public String[] pedidos;

    public Preparo(int comanda, int tempo_preparo, int segundo_incial, String[] pedidos) throws RemoteException {
        this.comanda = comanda;
        this.tempo_preparo = tempo_preparo;
        this.segundo_inicial = segundo_incial;
        this.pedidos = pedidos;
    }
}
