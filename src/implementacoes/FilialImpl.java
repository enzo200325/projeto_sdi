package implementacoes;

import classes.Pedido;
import interfaces.Filial;

import javax.jws.WebMethod;
import javax.jws.WebService;
import java.time.LocalTime;
import java.util.*;

@WebService(
        endpointInterface = "interfaces.Filial" // ,
        // targetNamespace = "implementacoes"
)
public class FilialImpl implements Filial{
    Random r;
    int id;
    String my_url, adj_url, interface_url;
    public FilialImpl() {
        this.r = new Random();
        this.id = r.nextInt(10);
    }
    public FilialImpl(String my_url, String adj_url, String interface_url) {
        this.r = new Random();
        this.id = r.nextInt(10);
        this.my_url = my_url;
        this.adj_url = adj_url;
        this.interface_url = interface_url;
    }
    public FilialImpl(String my_url, String adj_url, String interface_url, int id) {
        this.r = new Random();
        this.id = id;
        this.my_url = my_url;
        this.adj_url = adj_url;
        this.interface_url = interface_url;
    }

    @WebMethod
    public int get_id() {
        return this.id;
    }

    @WebMethod
    public void set_id(int id) {
        this.id = id;
    }
}
