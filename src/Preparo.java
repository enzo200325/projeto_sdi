import java.rmi.RemoteException;

public class Preparo {
    int comanda;
    int tempo_preparo;
    int segundo_inicial;
    String[] pedidos;

    Preparo(int comanda, int tempo_preparo, int segundo_incial, String[] pedidos) throws RemoteException {
        this.comanda = comanda;
        this.tempo_preparo = tempo_preparo;
        this.segundo_inicial = segundo_incial;
        this.pedidos = pedidos;
    }
}
