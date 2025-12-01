package interfaces;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import java.net.MalformedURLException;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface Filial {
    @WebMethod
    public int getId();
    @WebMethod
    public int getLider();
    @WebMethod
    public void election(int idCandidate, int idOrigem) throws MalformedURLException;
    @WebMethod
    public void announceLeader(int idLeader, int idOrigem);
    
    // Método público para o restaurante fazer pedidos
    @WebMethod
    public boolean solicitarProdutos(String[] produtos) throws MalformedURLException;
    
    // Métodos internos para estoque (podem ser usados internamente)
    @WebMethod
    public boolean temEstoque(String[] produtos);
    @WebMethod
    public boolean processarPedido(String[] produtos);
    @WebMethod
    public int consultarEstoque(String produto);
    
    // Métodos internos do algoritmo de consenso (não expostos ao restaurante)
    @WebMethod
    public void iniciarConsenso(String[] produtos, int idOrigem, int idFilialEscolhida) throws MalformedURLException;
    @WebMethod
    public void receberConsenso(String[] produtos, int idOrigem, int idFilialEscolhida) throws MalformedURLException;
    @WebMethod
    public void anunciarDecisao(int idFilialEscolhida, int idOrigem, boolean processado) throws MalformedURLException;
}
