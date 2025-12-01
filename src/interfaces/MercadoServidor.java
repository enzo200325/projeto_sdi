package interfaces;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface MercadoServidor {
    @WebMethod
    int cadastrarPedido(String restaurante);
    @WebMethod
    boolean comprarProdutos(int restaurante, String[] produtos);
    @WebMethod
    int tempoEntrega(int restaurante);
    
    // Método para o líder notificar o mercado (heartbeat)
    @WebMethod
    void notificarLider(int termo, int liderId);
}