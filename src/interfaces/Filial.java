package interfaces;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface Filial {
    @WebMethod
    public int get_id();
    @WebMethod
    public void set_id(int id);
}
