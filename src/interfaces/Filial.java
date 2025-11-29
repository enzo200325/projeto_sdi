package interfaces;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import java.net.MalformedURLException;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface Filial {
    @WebMethod
    public int get_id();
    @WebMethod
    public void set_id(int id);
    @WebMethod
    public void election(int idCandidate) throws MalformedURLException;
    @WebMethod
    public void announceLeader(int idLeader);

}
