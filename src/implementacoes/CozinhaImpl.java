package implementacoes;

import classes.Preparo;
import interfaces.Cozinha;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Random;
import java.lang.Math;

public class CozinhaImpl extends UnicastRemoteObject implements Cozinha {
    ArrayList<Preparo> preparos;
    Random r = new Random();
    public CozinhaImpl() throws RemoteException {
        super();
        preparos = new ArrayList<>();
    }

    @Override
    public int novoPreparo(int comanda, String[] pedidos) throws RemoteException {
        int tempo_preparo = r.nextInt(9) + 1;
        int segundo_atual = LocalTime.now().getSecond();
        Preparo preparo = new Preparo(comanda, tempo_preparo, segundo_atual, pedidos);
        preparos.add(preparo);
        return preparos.size() - 1;
    }
    @Override
    public int tempoPreparo(int preparo_idx) throws RemoteException {
        int segundo_atual = LocalTime.now().getSecond();
        Preparo preparo = preparos.get(preparo_idx);
        return Math.max(0, preparo.tempo_preparo - (segundo_atual - preparo.segundo_inicial));
    }

    @Override
    public String[] pegarPreparo(int preparo_idx) throws RemoteException {
        if (tempoPreparo(preparo_idx) > 0) return null;
        Preparo preparo = preparos.get(preparo_idx);
        return preparo.pedidos;
    }
}
