package implementacoes;

import classes.Pedido;
import interfaces.Filial;

import javax.jws.WebMethod;
import javax.jws.WebService;
import java.net.MalformedURLException;
import java.util.*;

@WebService(
        endpointInterface = "interfaces.Filial"
)
public class FilialImpl implements Filial{
    Random r;
    int id, id_lider, id_origem;
    String my_url;

    private Filial next;
    private boolean emEleicao;

    public FilialImpl(String my_url) {
        this.r = new Random();
        this.id = r.nextInt(2000);
        this.id_lider = -1;
        this.my_url = my_url;
        this.emEleicao = false;
    }

    @WebMethod
    public int getId() {
        return this.id;
    }

    public int getLider() {
        return this.id_lider;
    }

    @WebMethod
    public  void election(int idC, int idOrigem) throws MalformedURLException {
        this.id_origem = idOrigem;
        emEleicao = true;

        System.out.println("FILIAL ATUAL: " + this.id);
        System.out.println("NEXT " + next.getId());
        System.out.println("Filial " + this.id + " recebeu election(" + idC + ")");

        int maior = Math.max(idC, this.id);

        if (next.getId() == idOrigem) {
            System.out.println("Filial " + this.id + " detectou fim do anel. Líder = " + maior);
           try {
                next.announceLeader(maior, this.id);
                emEleicao = false;
                return;
           }catch (Exception e) {
               e.printStackTrace();
           }
           return;
        }
        next.election(maior, idOrigem);
    }

    @WebMethod
    public  void announceLeader (int idLider, int idOrigem) {
        this.id_lider = idLider;
        emEleicao = false;
        if(this.id == idOrigem) {
            System.out.println("Divulgação do líder com sucesso.\n");
            return;
        }

        System.out.println("Filial " + this.id + " recebeu que líder é " + idLider);
        try{
            next.announceLeader(idLider, idOrigem);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void setNext(Filial next) {
        this.next = next;
    }

}
