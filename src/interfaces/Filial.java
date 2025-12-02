package interfaces;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import java.net.MalformedURLException;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface Filial {
    // Identificação
    @WebMethod
    public int getId();
    
    // Raft: Estado e liderança
    @WebMethod
    public int getLider();
    @WebMethod
    public int getTermo();
    @WebMethod
    public String getEstado(); // "FOLLOWER", "CANDIDATE", "LEADER"
    
    // Bully: Election e Coordinator RPCs
    @WebMethod
    public boolean election(int candidatoId); // Recebe mensagem de eleição de filial com ID menor
    @WebMethod
    public void coordinator(int coordenadorId); // Recebe notificação de novo coordenador
    
    // Raft: RequestVote RPC (para eleição) - MANTIDO PARA COMPATIBILIDADE
    @WebMethod
    public boolean requestVote(int termo, int candidatoId, int lastLogIndex, int lastLogTerm);
    
    // Raft: AppendEntries RPC (heartbeat e coordenação) - MANTIDO PARA COMPATIBILIDADE
    @WebMethod
    public boolean appendEntries(int termo, int liderId, int prevLogIndex, int prevLogTerm, String[] entries, int leaderCommit);
    
    // Método público para o restaurante fazer pedidos (via líder)
    @WebMethod
    public boolean solicitarProdutos(String[] produtos) throws MalformedURLException;
    
    // Métodos para estoque
    @WebMethod
    public boolean temEstoque(String[] produtos);
    @WebMethod
    public boolean processarPedido(String[] produtos);
    @WebMethod
    public int consultarEstoque(String produto);
    
    // Método para consultar quais produtos estão disponíveis de uma lista
    @WebMethod
    public String[] consultarProdutosDisponiveis(String[] produtos);
    
    // Método para calcular média de estoque (para ordenação)
    @WebMethod
    public double calcularMediaEstoque();
    
    // Consenso: processa apenas os produtos específicos fornecidos
    @WebMethod
    public boolean processarProdutosEspecificos(String[] produtos, int termo, int liderId);
}
