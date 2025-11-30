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
    public void election(int idCandidate, int idOrigem) throws MalformedURLException;
    @WebMethod
    public void announceLeader(int idLeader, int idOrigem);
}
