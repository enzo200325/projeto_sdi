package implementacoes;

import classes.Pedido;
import interfaces.Filial;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalTime;
import java.util.*;

@WebService(
        endpointInterface = "interfaces.Filial" // ,
        // targetNamespace = "implementacoes"
)
public class FilialImpl implements Filial{
    Random r;
    int id, id_lider = -1;
    String my_url, adj_url, interface_url;

    private boolean emEleicao;

    public FilialImpl(String my_url, String adj_url, int id) {
        this.r = new Random();
        this.id = id;
        this.my_url = my_url;
        this.adj_url = adj_url;
        this.emEleicao = false;
    }

    @WebMethod
    public int get_id() {
        return this.id;
    }

    @WebMethod
    public void set_id(int id) {
        this.id = id;
    }

    @WebMethod
    public synchronized void election(int idC) throws MalformedURLException {

        System.out.println("Filial " + this.id + " recebeu election(" + idC + ")");

        int maior = Math.max(idC, this.id);

        // Fechamento do anel:
        if (getNext().get_id() == idC) {
            System.out.println("Filial " + this.id + " detectou fim do anel. Líder = " + maior);
            getNext().announceLeader(maior);
            return;
        }

        getNext().election(maior);
    }

    @WebMethod
    public synchronized void announceLeader (int id) {
        this.id_lider = id;

        System.out.println(this.id);
        if(this.id == id) {
            System.out.println("Divulgação do líder com sucesso.\n");
            return;
        }
        System.out.println("Filial " + this.id + " registrou a líder " + id + " .\n");

        try{
            getNext().announceLeader(id);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private Filial getNext() throws MalformedURLException {
        URL wsdl = new URL(adj_url + "?wsdl");
        QName qname = new QName("http://implementacoes/", "FilialImplService");
        Service service = Service.create(wsdl, qname);
        return service.getPort(Filial.class);
    }


}
